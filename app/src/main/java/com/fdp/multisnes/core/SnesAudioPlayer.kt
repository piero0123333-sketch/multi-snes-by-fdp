package com.fdp.multisnes.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TAREA 1: Reproductor PCM 16-bit Estéreo a 48000 Hz sin estática ni pérdida de muestras.
 * - Buffer nativo de AudioTrack dimensionado a getMinBufferSize(...) * 3.
 * - Cola de respaldo (pendingBuffer) para WRITE_NON_BLOCKING: si written < count, conserva las muestras
 *   restantes (count - written) para escribirlas al inicio del siguiente ciclo sin descartarlas jamás.
 */
class SnesAudioPlayer {

    companion object {
        const val OUTPUT_SAMPLE_RATE_HZ = 48000
        private const val MAX_PENDING_SAMPLES = 32768
    }

    private var audioTrack: AudioTrack? = null
    private val enabled = AtomicBoolean(true)
    @Volatile
    private var volumeGain: Float = 0.9f

    // Buffer de muestras pendientes cuando WRITE_NON_BLOCKING escribe parcialmente (written < count)
    private var pendingBuffer = ShortArray(16384)
    private var pendingCount = 0

    @Synchronized
    fun start(sampleRateHz: Int = OUTPUT_SAMPLE_RATE_HZ) {
        stop()
        val rate = if (sampleRateHz > 0) OUTPUT_SAMPLE_RATE_HZ else OUTPUT_SAMPLE_RATE_HZ
        val minBuf = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val bufferBytes = minBuf * 3

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        pendingCount = 0
        applyTrackVolume(track)
        track.play()
        audioTrack = track
    }

    fun setSoundConfig(isEnabled: Boolean, volumeFraction: Float) {
        enabled.set(isEnabled)
        volumeGain = volumeFraction.coerceIn(0f, 1f)
        audioTrack?.let { applyTrackVolume(it) }
    }

    private fun applyTrackVolume(track: AudioTrack) {
        val gain = if (enabled.get()) volumeGain else 0f
        track.setVolume(gain)
    }

    @Synchronized
    fun writeSamples(samples: ShortArray, count: Int) {
        val track = audioTrack ?: return
        if (count <= 0 && pendingCount <= 0) return

        if (!enabled.get()) {
            // Si el usuario silenció el sonido, drenar sin acumular retraso
            pendingCount = 0
            return
        }

        // 1. Escribir primero cualquier muestra pendiente del ciclo anterior
        if (pendingCount > 0) {
            val writtenPending = track.write(pendingBuffer, 0, pendingCount, AudioTrack.WRITE_NON_BLOCKING)
            if (writtenPending > 0) {
                val remaining = pendingCount - writtenPending
                if (remaining > 0) {
                    System.arraycopy(pendingBuffer, writtenPending, pendingBuffer, 0, remaining)
                }
                pendingCount = remaining
            }
        }

        if (count <= 0) return

        // 2. Si todavía quedan muestras pendientes en cola, encolar las nuevas detrás para mantener el orden exacto
        if (pendingCount > 0) {
            enqueueRemainingSamples(samples, 0, count)
            return
        }

        // 3. Escribir el bloque actual en modo no bloqueante y conservar (count - written) si fue parcial
        val written = track.write(samples, 0, count, AudioTrack.WRITE_NON_BLOCKING)
        val safeWritten = written.coerceAtLeast(0)
        if (safeWritten < count) {
            enqueueRemainingSamples(samples, safeWritten, count - safeWritten)
        }
    }

    private fun enqueueRemainingSamples(src: ShortArray, srcOffset: Int, length: Int) {
        if (length <= 0) return
        val needed = pendingCount + length
        if (needed > MAX_PENDING_SAMPLES) {
            // Si el dispositivo pausó el audio por más de ~340ms, conservar las muestras más recientes
            val keep = (MAX_PENDING_SAMPLES - length).coerceAtLeast(0)
            if (keep > 0 && pendingCount > keep) {
                System.arraycopy(pendingBuffer, pendingCount - keep, pendingBuffer, 0, keep)
            }
            pendingCount = keep
        }
        if (pendingCount + length > pendingBuffer.size) {
            val nextCap = (pendingBuffer.size * 2).coerceAtLeast(pendingCount + length)
            pendingBuffer = pendingBuffer.copyOf(nextCap)
        }
        System.arraycopy(src, srcOffset, pendingBuffer, pendingCount, length)
        pendingCount += length
    }

    @Synchronized
    fun flushPending() {
        pendingCount = 0
        runCatching { audioTrack?.flush() }
    }

    @Synchronized
    fun stop() {
        pendingCount = 0
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        }
        audioTrack = null
    }
}
