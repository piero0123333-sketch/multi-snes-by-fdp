package com.fdp.multisnes

import android.app.Application
import com.fdp.multisnes.core.SnesNativeBridge
import com.fdp.multisnes.data.repository.GameLibraryRepository
import com.fdp.multisnes.data.repository.SettingsRepository
import com.fdp.multisnes.data.rom.AutoRomScanner
import com.fdp.multisnes.input.MacroEngine
import com.fdp.multisnes.netplay.HotspotNetworkHelper
import com.fdp.multisnes.netplay.UdpHotspotTransport

class MultiSnesApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var libraryRepository: GameLibraryRepository
        private set

    lateinit var romScanner: AutoRomScanner
        private set

    lateinit var nativeBridge: SnesNativeBridge
        private set

    lateinit var macroEngine: MacroEngine
        private set

    lateinit var networkHelper: HotspotNetworkHelper
        private set

    lateinit var hotspotTransport: UdpHotspotTransport
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        libraryRepository = GameLibraryRepository(this)
        romScanner = AutoRomScanner(this)
        nativeBridge = SnesNativeBridge(this)
        macroEngine = MacroEngine(this, nativeBridge)
        settingsRepository.syncMacroSlots(
            slots = macroEngine.slotConfigs,
            keyMap = macroEngine.loadHardwareKeyMap(settingsRepository.settings.value.hardwareKeyMap)
        )
        networkHelper = HotspotNetworkHelper(this)
        hotspotTransport = UdpHotspotTransport(networkHelper)
    }
}
