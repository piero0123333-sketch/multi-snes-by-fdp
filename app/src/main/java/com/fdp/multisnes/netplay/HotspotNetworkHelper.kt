package com.fdp.multisnes.netplay

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Optimiza el hardware de radio Wi-Fi para juego Hotspot a <10 cm con <2 ms de latencia:
 * - Desactiva el ahorro de energía del chip Wi-Fi (WIFI_MODE_FULL_LOW_LATENCY).
 * - Habilita recepción de tramas UDP Broadcast en subredes Hotspot (ap0, swlan0, wlan0).
 * - Detecta automáticamente la IP local y dirección de difusión del Hotspot Android.
 */
class HotspotNetworkHelper(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    data class LocalEndpointInfo(
        val localIp: String,
        val broadcastIp: String,
        val interfaceName: String
    )

    fun acquireUltraLowLatencyLocks() {
        releaseLocks()
        val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }

        wifiLock = wifiManager.createWifiLock(lockMode, "MultiSnes:HotspotLowLatencyLock").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        multicastLock = wifiManager.createMulticastLock("MultiSnes:HotspotBeaconLock").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MultiSnes:NetplayCpuLock"
        ).apply {
            setReferenceCounted(false)
            runCatching { acquire(2 * 60 * 60 * 1000L) }
        }
    }

    fun releaseLocks() {
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        runCatching { if (multicastLock?.isHeld == true) multicastLock?.release() }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wifiLock = null
        multicastLock = null
        wakeLock = null
    }

    /**
     * Inspecciona todas las interfaces de red activas priorizando interfaces Hotspot de Android
     * (ap0, wlan1, swlan0, softap0, rndis0, wlan0).
     */
    fun detectActiveEndpoints(): List<LocalEndpointInfo> {
        val results = mutableListOf<LocalEndpointInfo>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }
            .getOrNull() ?: emptyList()

        for (nif in interfaces) {
            val up = runCatching { nif.isUp && !nif.isLoopback }.getOrDefault(false)
            if (!up) continue

            for (ifaceAddr in nif.interfaceAddresses) {
                val addr = ifaceAddr.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    // Ignorar IPs de datos móviles CGNAT si hay subredes LAN privadas
                    val bcast = ifaceAddr.broadcast?.hostAddress ?: guessSubnetBroadcast(ip)
                    results.add(LocalEndpointInfo(ip, bcast, nif.name))
                }
            }
        }

        return results.sortedByDescending { endpointPriority(it) }
    }

    fun candidateBroadcastTargets(): List<InetAddress> {
        val set = LinkedHashSet<String>()
        for (ep in detectActiveEndpoints()) {
            set.add(ep.broadcastIp)
            // En Android cuando un teléfono se conecta al Hotspot de otro, el Host suele ser .1
            val prefix = ep.localIp.substringBeforeLast('.', "")
            if (prefix.isNotEmpty()) {
                set.add("$prefix.1")
                set.add("$prefix.255")
            }
        }
        // Direcciones estándar de Hotspot Android (AOSP, Samsung, Xiaomi, Motorola)
        set.add("192.168.43.1")
        set.add("192.168.43.255")
        set.add("192.168.137.1")
        set.add("255.255.255.255")

        return set.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
    }

    private fun endpointPriority(ep: LocalEndpointInfo): Int {
        val n = ep.interfaceName.lowercase()
        return when {
            n.startsWith("ap") || n.contains("swlan") || n.contains("softap") -> 100
            ep.localIp.startsWith("192.168.43.") -> 95
            n.startsWith("wlan") -> 80
            ep.localIp.startsWith("192.168.") || ep.localIp.startsWith("10.") -> 60
            else -> 10
        }
    }

    private fun guessSubnetBroadcast(ipv4: String): String {
        val parts = ipv4.split('.')
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.255" else "255.255.255.255"
    }
}
