package com.fdp.multisnes.core

import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Puente JNI de alto rendimiento hacia C++ NDK (Libretro Snes9x + Resampler 48kHz + Rewind 90 Slots + Rollback).
 * Ejecuta el bucle de emulación a 60.0988 Hz exactos (o 120 Hz en modo Fast-Forward '>>').
 */
class SnesNativeBridge(private val context: Context) {

    interface FrameStepHook {
        /**
         * Permite al coordinador Hotspot 2J inyectar los inputs sincronizados de P1 y P2
         * y aplicar Rollback instantáneo antes de avanzar el frame actual.
         */
        fun onBeforeStepFrame(currentFrame: Int)
    }

    /**
     * Métricas de rendimiento en vivo mostradas junto al botón de pausa.
     * @param fps cuadros por segundo reales emulados
     * @param speedPercent velocidad respecto al hardware original (100% = velocidad exacta)
     * @param frameMs tiempo medio de CPU que cuesta emular un frame
     */
    data class PerfStats(
        val fps: Int = 0,
        val speedPercent: Int = 0,
        val frameMs: Float = 0f
    )

    companion object {
        init {
            System.loadLibrary("multisnes_jni")
        }
    }

    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val fastForward = AtomicBoolean(false)
    private var emulationThread: Thread? = null

    @Volatile
    var perfStats: PerfStats = PerfStats()
        private set

    var netplayHook: FrameStepHook? = null
    var onFrameRendered: (() -> Unit)? = null

    val audioPlayer = SnesAudioPlayer()

    fun initializeCore(): Boolean {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val bundledCore = File(nativeDir, "libsnes9x_libretro_android.so")
        val customCore = File(context.filesDir, "cores/libsnes9x_libretro_android.so")
        val corePath = when {
            bundledCore.exists() -> bundledCore.absolutePath
            customCore.exists() -> customCore.absolutePath
            else -> "libsnes9x_libretro_android.so"
        }

        val sysDir = File(context.filesDir, "system").apply { mkdirs() }
        val saveDir = File(context.filesDir, "sram").apply { mkdirs() }

        return nativeInit(corePath, sysDir.absolutePath, saveDir.absolutePath)
    }

    fun startSession(
        romNameHint: String,
        romBytes: ByteArray,
        initialSram: ByteArray?,
        initialQuickState: ByteArray?,
        isOnlineSession: Boolean
    ): Boolean {
        stopSession()
        initializeCore()

        if (!nativeLoadRomBytes(romNameHint, romBytes)) {
            return false
        }

        if (initialSram != null && initialSram.isNotEmpty()) {
            nativeWriteSram(initialSram)
        }
        if (initialQuickState != null && initialQuickState.isNotEmpty()) {
            nativeUnserializeState(initialQuickState)
        }

        // TAREA 1: Salida siempre a 48000 Hz remuestreada en C++
        audioPlayer.start(SnesAudioPlayer.OUTPUT_SAMPLE_RATE_HZ)

        paused.set(false)
        fastForward.set(false)
        nativeSetRewinding(false)
        running.set(true)

        emulationThread = thread(start = true, name = "SnesEmulationLoop") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            // TAREA 1: Buffer de drenaje aumentado a 4096 muestras estéreo PCM16
            val audioScratch = ShortArray(4096)
            val baseFps = nativeGetTargetFps().coerceIn(50.0, 60.2)
            var nextFrameNs = System.nanoTime()
            var rewindTickParity = 0

            // Medición de rendimiento (ventana de 1 segundo)
            var windowStartNs = System.nanoTime()
            var windowFrames = 0
            var emuNsAccum = 0L
            perfStats = PerfStats()

            while (running.get()) {
                if (paused.get() && !isOnlineSession) {
                    Thread.sleep(8)
                    nextFrameNs = System.nanoTime()
                    windowStartNs = nextFrameNs
                    windowFrames = 0
                    emuNsAccum = 0L
                    perfStats = PerfStats()
                    continue
                }

                val isRewindingNow = !isOnlineSession && nativeIsRewinding()
                val effectiveFps = when {
                    isOnlineSession -> baseFps
                    isRewindingNow -> baseFps * 0.5 // ~30 snapshots/seg de extracción suave en Rewind
                    fastForward.get() -> baseFps * 2.0
                    else -> baseFps
                }
                val frameDurationNs = (1_000_000_000.0 / effectiveFps).toLong()

                val frameIdx = nativeGetFrameNumber()
                if (!isRewindingNow) {
                    netplayHook?.onBeforeStepFrame(frameIdx)
                }

                val emuStartNs = System.nanoTime()
                nativeStepFrame(isOnlineSession)
                emuNsAccum += System.nanoTime() - emuStartNs
                windowFrames++
                onFrameRendered?.invoke()

                val nowNs = System.nanoTime()
                if (nowNs - windowStartNs >= 1_000_000_000L) {
                    val elapsedSec = (nowNs - windowStartNs) / 1_000_000_000.0
                    val measuredFps = windowFrames / elapsedSec
                    perfStats = PerfStats(
                        fps = measuredFps.roundToInt(),
                        speedPercent = (measuredFps / baseFps * 100.0).roundToInt(),
                        frameMs = (emuNsAccum / windowFrames.coerceAtLeast(1)) / 1_000_000f
                    )
                    windowStartNs = nowNs
                    windowFrames = 0
                    emuNsAccum = 0L
                }

                // TAREA 1 y 3: Si está rebobinando silenciamos la cola; en juego normal drenamos 4096 muestras sin pérdida
                val readSamples = nativeDrainAudioPcm16(audioScratch)
                if (isRewindingNow) {
                    audioPlayer.flushPending()
                } else {
                    audioPlayer.writeSamples(audioScratch, readSamples)
                }

                nextFrameNs += frameDurationNs
                val sleepNs = nextFrameNs - System.nanoTime()
                if (sleepNs > 1_500_000L) {
                    Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                } else if (sleepNs > 0L) {
                    while (System.nanoTime() < nextFrameNs && running.get()) {
                        Thread.onSpinWait()
                    }
                } else if (sleepNs < -frameDurationNs * 3) {
                    nextFrameNs = System.nanoTime()
                }
            }
        }
        return true
    }

    fun stopSession(): ByteArray? {
        nativeSetRewinding(false)
        running.set(false)
        emulationThread?.join(500)
        emulationThread = null
        audioPlayer.stop()
        val finalSram = nativeReadSram()
        nativeUnloadRom()
        return finalSram
    }

    fun setPaused(value: Boolean) {
        if (value) {
            nativeSetRewinding(false)
        }
        paused.set(value)
    }

    fun isPaused(): Boolean = paused.get()

    fun toggleFastForward(): Boolean {
        val next = !fastForward.get()
        fastForward.set(next)
        return next
    }

    fun isFastForward(): Boolean = fastForward.get()

    /**
     * TAREA 3: Activa o desactiva el modo Rewind (Retroceder el Tiempo en RAM).
     * Se desactiva automáticamente si la sesión actual es Online.
     */
    fun setRewinding(active: Boolean) {
        nativeSetRewinding(active)
    }

    fun isRewinding(): Boolean = nativeIsRewinding()

    fun getRewindAvailableCount(): Int = nativeGetRewindCount()

    fun setLocalPlayerInput(port: Int, mask: Int) {
        nativeSetInputMask(port, mask)
    }

    /**
     * Hook JNI individual por botón requerido por el motor de Macros (Turbo / Auto-Hold).
     * @param buttonId ID Libretro (0=B, 1=Y, 2=SELECT, 3=START, 4=UP..11=R)
     */
    fun setButtonState(buttonId: Int, pressed: Boolean, port: Int = 0) {
        nativeSetButtonState(port, buttonId, pressed)
    }

    /**
     * Hook JNI de máscara de Macro (soporta combos de múltiples botones y fase baja de Turbo).
     */
    fun setMacroMaskState(bitMask: Int, pressed: Boolean, isTurboLowPhase: Boolean = false, port: Int = 0) {
        nativeSetMacroMaskState(port, bitMask, pressed, isTurboLowPhase)
    }

    fun clearAllMacros(port: Int = 0) {
        nativeClearAllMacros(port)
    }

    fun getEffectiveInputMask(port: Int = 0): Int = nativeGetEffectiveInputMask(port)

    fun getCurrentFrame(): Int = nativeGetFrameNumber()

    fun performRollback(
        targetFrame: Int,
        currentFrame: Int,
        p1History: ShortArray,
        p2History: ShortArray
    ): Boolean = nativeRollbackAndReplay(targetFrame, currentFrame, p1History, p2History)

    fun copyVideoRgba(outPixels: IntArray, outDimensions: IntArray): Int =
        nativeCopyVideoRgba(outPixels, outDimensions)

    /**
     * Captura una miniatura Bitmap ARGB_8888 del framebuffer actual para las ranuras de SaveStateManager.
     */
    fun captureCurrentScreenshotBitmap(): Bitmap? {
        return runCatching {
            val rawPixels = IntArray(512 * 480)
            val dims = IntArray(2)
            val count = nativeCopyVideoRgba(rawPixels, dims)
            val w = dims[0]
            val h = dims[1]
            if (count <= 0 || w <= 0 || h <= 0) return null
            val argb = IntArray(w * h)
            for (i in 0 until (w * h)) {
                val abgr = rawPixels[i]
                val r = abgr and 0xFF
                val g = (abgr ushr 8) and 0xFF
                val b = (abgr ushr 16) and 0xFF
                argb[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    fun saveStateBytes(): ByteArray? = nativeSerializeState()

    fun loadStateBytes(bytes: ByteArray): Boolean = nativeUnserializeState(bytes)

    fun readSramBytes(): ByteArray? = nativeReadSram()

    fun getStateChecksum(): Int = nativeGetStateChecksum()

    // Métodos JNI nativos implementados en native_jni.cpp
    private external fun nativeInit(coreLibPath: String, systemDir: String, saveDir: String): Boolean
    private external fun nativeLoadRomBytes(romPathHint: String, romBytes: ByteArray): Boolean
    private external fun nativeUnloadRom()
    private external fun nativeReset()
    private external fun nativeStepFrame(recordRollbackSnapshot: Boolean)
    private external fun nativeSetInputMask(port: Int, mask: Int)
    private external fun nativeSetButtonState(port: Int, buttonId: Int, pressed: Boolean)
    private external fun nativeSetMacroMaskState(port: Int, bitMask: Int, pressed: Boolean, isTurboLowPhase: Boolean)
    private external fun nativeClearAllMacros(port: Int)
    private external fun nativeGetEffectiveInputMask(port: Int): Int
    private external fun nativeGetFrameNumber(): Int
    private external fun nativeRollbackAndReplay(
        targetFrame: Int,
        currentFrame: Int,
        p1History: ShortArray,
        p2History: ShortArray
    ): Boolean
    private external fun nativeCopyVideoRgba(outPixels: IntArray, outDimensions: IntArray): Int
    private external fun nativeDrainAudioPcm16(outAudio: ShortArray): Int
    private external fun nativeGetTargetFps(): Double
    private external fun nativeGetSampleRate(): Double
    private external fun nativeSerializeState(): ByteArray?
    private external fun nativeUnserializeState(stateBytes: ByteArray): Boolean
    private external fun nativeReadSram(): ByteArray?
    private external fun nativeWriteSram(sramBytes: ByteArray): Boolean
    private external fun nativeGetStateChecksum(): Int
    private external fun nativeSetRewinding(active: Boolean)
    private external fun nativeIsRewinding(): Boolean
    private external fun nativeGetRewindCount(): Int
}
