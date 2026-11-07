#ifndef SNES_CORE_BRIDGE_H__
#define SNES_CORE_BRIDGE_H__

#include "libretro.h"
#include "rollback_ring_buffer.h"
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

namespace multisnes {

/**
 * Puente nativo C++17 hacia el núcleo oficial Libretro Snes9x (libsnes9x_libretro_android.so).
 * Incluye:
 * - TAREA 1: Resampler Estéreo Hermite de 4 puntos (32040 Hz -> 48000 Hz) + Crossfade post-rollback de 32 muestras.
 * - TAREA 3: Motor de Rewind en memoria RAM (cola circular de 90 estados capturados cada 6 frames en modo local).
 */
class SnesCoreBridge {
public:
    static constexpr double kOutputSampleRate = 48000.0;
    static constexpr size_t kCrossfadeFrames = 32;
    static constexpr size_t kRewindMaxStates = 90;
    static constexpr int32_t kRewindCaptureIntervalFrames = 6;

    static SnesCoreBridge& instance();

    bool initialize(const std::string& coreLibPath, const std::string& systemDir, const std::string& saveDir);
    bool loadRomBytes(const std::string& romPathHint, const uint8_t* rawBytes, size_t byteLength);
    void unloadRom();
    void resetGame();

    // Avanza 1 frame real del hardware SNES (o retrocede 1 snapshot si rewinding_ == true en modo local)
    void stepFrame(bool recordRollbackSnapshot = false);

    // Sincronización determinista 2 Jugadores por Hotspot (Rollback + Replay en RAM nativa)
    bool rollbackAndReplay(
        int32_t targetFrame,
        int32_t currentFrame,
        const uint16_t* p1Inputs,
        const uint16_t* p2Inputs,
        size_t count
    );

    // Motor de Rewind (TAREA 3)
    void setRewinding(bool active);
    bool isRewinding() const { return rewinding_.load(); }
    int32_t getRewindAvailableCount() const;

    void setPlayerInputMask(int port, uint16_t mask);
    void setButtonState(int port, int buttonId, bool pressed);
    void setMacroMaskState(int port, uint16_t mask, bool pressed, bool isTurboLowPhase);
    void clearAllMacros(int port);
    uint16_t getPlayerInputMask(int port) const;

    int32_t getCurrentFrameNumber() const { return frameCounter_.load(); }
    void setCurrentFrameNumber(int32_t frame) { frameCounter_.store(frame); }

    // Video RGBA8888 listo para textura OpenGL ES 3.0 y miniaturas de SaveStateManager
    void copyLatestVideoFrame(std::vector<uint32_t>& outPixels, int& outWidth, int& outHeight);

    // Audio estéreo PCM 16-bit remuestreado a 48000 Hz sin estática
    size_t drainAudioPcm16(int16_t* outBuffer, size_t maxSamples);
    double getTargetFps() const { return targetFps_; }
    double getSampleRate() const { return kOutputSampleRate; }
    bool isHardwareCoreLoaded() const { return coreHandle_ != nullptr && gameLoaded_; }

    // Guardado Rápido (Save State) y SRAM de batería (.srm)
    std::vector<uint8_t> serializeState();
    bool unserializeState(const uint8_t* data, size_t length);
    std::vector<uint8_t> readSram();
    bool writeSram(const uint8_t* data, size_t length);
    uint32_t getCurrentStateChecksum();

    // Callbacks invocados por libsnes9x_libretro_android.so
    bool onEnvironment(unsigned cmd, void* data);
    void onVideoRefresh(const void* data, unsigned width, unsigned height, size_t pitch);
    size_t onAudioBatch(const int16_t* data, size_t frames);
    int16_t onInputState(unsigned port, unsigned device, unsigned index, unsigned id);

private:
    SnesCoreBridge();
    ~SnesCoreBridge();

    void closeCoreLibrary();
    void resetAudioResamplerStateLocked();
    void clearRewindBufferLocked();

    struct CoreSymbols {
        fn_retro_set_environment set_environment = nullptr;
        fn_retro_set_video_refresh set_video_refresh = nullptr;
        fn_retro_set_audio_sample set_audio_sample = nullptr;
        fn_retro_set_audio_sample_batch set_audio_sample_batch = nullptr;
        fn_retro_set_input_poll set_input_poll = nullptr;
        fn_retro_set_input_state set_input_state = nullptr;
        fn_retro_init init = nullptr;
        fn_retro_deinit deinit = nullptr;
        fn_retro_api_version api_version = nullptr;
        fn_retro_get_system_info get_system_info = nullptr;
        fn_retro_get_system_av_info get_system_av_info = nullptr;
        fn_retro_set_controller_port_device set_controller_port_device = nullptr;
        fn_retro_reset reset = nullptr;
        fn_retro_run run = nullptr;
        fn_retro_serialize_size serialize_size = nullptr;
        fn_retro_serialize serialize = nullptr;
        fn_retro_unserialize unserialize = nullptr;
        fn_retro_load_game load_game = nullptr;
        fn_retro_unload_game unload_game = nullptr;
        fn_retro_get_region get_region = nullptr;
        fn_retro_get_memory_data get_memory_data = nullptr;
        fn_retro_get_memory_size get_memory_size = nullptr;
    };

    struct RewindSnapshot {
        int32_t frameNumber = 0;
        std::vector<uint8_t> stateData;
        size_t validSize = 0;
    };

    void* coreHandle_ = nullptr;
    CoreSymbols sym_{};
    bool gameLoaded_ = false;
    bool suppressAvOutput_ = false;
    bool suppressAudioOnly_ = false;

    std::string systemDir_;
    std::string saveDir_;
    std::vector<uint8_t> cleanRomBuffer_;

    retro_pixel_format pixelFormat_ = RETRO_PIXEL_FORMAT_RGB565;
    double targetFps_ = 60.0988;
    double sampleRate_ = 32040.0;

    std::atomic<uint16_t> inputMasks_[2]{0, 0};
    std::atomic<uint16_t> macroHoldMasks_[2]{0, 0};
    std::atomic<uint16_t> macroTurboOffMasks_[2]{0, 0};
    std::atomic<int32_t> frameCounter_{0};

    std::mutex videoMutex_;
    std::vector<uint32_t> rgbaFrame_;
    int frameWidth_ = 256;
    int frameHeight_ = 224;

    // Audio FIFO a 48000 Hz + Estado del Resampler Hermite y Crossfade Post-Rollback
    std::mutex audioMutex_;
    std::vector<int16_t> audioFifo_;
    size_t audioReadPos_ = 0;
    double resamplerPhase_ = 0.0;
    int16_t histL_[4]{0, 0, 0, 0};
    int16_t histR_[4]{0, 0, 0, 0};
    bool resamplerPrimed_ = false;
    int16_t lastValidLeft_ = 0;
    int16_t lastValidRight_ = 0;
    bool needsPostRollbackCrossfade_ = false;

    // Core Mutex + Rollback Online + Ring Buffer de Rewind en RAM (90 estados)
    mutable std::mutex coreMutex_;
    RollbackRingBuffer rollbackBuffer_;
    std::vector<uint8_t> scratchStateBuffer_;
    std::vector<RewindSnapshot> rewindBuffer_;
    size_t rewindHead_ = 0;
    size_t rewindCount_ = 0;
    std::atomic<bool> rewinding_{false};
};

} // namespace multisnes

#endif // SNES_CORE_BRIDGE_H__
