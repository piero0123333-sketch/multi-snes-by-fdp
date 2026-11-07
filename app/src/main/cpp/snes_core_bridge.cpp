#include "snes_core_bridge.h"
#include <android/log.h>
#include <dlfcn.h>
#include <algorithm>
#include <cmath>
#include <cstring>

#define LOG_TAG "MultiSnesNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace multisnes {

namespace {
bool cbEnvironment(unsigned cmd, void* data) {
    return SnesCoreBridge::instance().onEnvironment(cmd, data);
}
void cbVideoRefresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    SnesCoreBridge::instance().onVideoRefresh(data, width, height, pitch);
}
void cbAudioSample(int16_t left, int16_t right) {
    int16_t pair[2] = {left, right};
    SnesCoreBridge::instance().onAudioBatch(pair, 1);
}
size_t cbAudioBatch(const int16_t* data, size_t frames) {
    return SnesCoreBridge::instance().onAudioBatch(data, frames);
}
void cbInputPoll(void) {
    // El estado de los mandos P1 y P2 se actualiza atómicamente desde UI / UDP Hotspot
}
int16_t cbInputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    return SnesCoreBridge::instance().onInputState(port, device, index, id);
}

inline int16_t hermiteInterpolate4Point(float ym1, float y0, float y1, float y2, float t) {
    const float c0 = y0;
    const float c1 = 0.5f * (y1 - ym1);
    const float c2 = ym1 - 2.5f * y0 + 2.0f * y1 - 0.5f * y2;
    const float c3 = 0.5f * (y2 - ym1) + 1.5f * (y0 - y1);
    const float val = ((c3 * t + c2) * t + c1) * t + c0;
    if (val >= 32767.0f) return 32767;
    if (val <= -32768.0f) return -32768;
    return static_cast<int16_t>(std::lrintf(val));
}
} // namespace

SnesCoreBridge& SnesCoreBridge::instance() {
    static SnesCoreBridge inst;
    return inst;
}

SnesCoreBridge::SnesCoreBridge() {
    rgbaFrame_.resize(512 * 480, 0xFF000000u);
    audioFifo_.reserve(32768);
}

SnesCoreBridge::~SnesCoreBridge() {
    unloadRom();
    closeCoreLibrary();
}

void SnesCoreBridge::resetAudioResamplerStateLocked() {
    audioFifo_.clear();
    audioReadPos_ = 0;
    resamplerPhase_ = 0.0;
    for (int i = 0; i < 4; ++i) {
        histL_[i] = 0;
        histR_[i] = 0;
    }
    resamplerPrimed_ = false;
    lastValidLeft_ = 0;
    lastValidRight_ = 0;
    needsPostRollbackCrossfade_ = false;
}

void SnesCoreBridge::clearRewindBufferLocked() {
    rewindHead_ = 0;
    rewindCount_ = 0;
    rewinding_.store(false);
    for (auto& slot : rewindBuffer_) {
        slot.frameNumber = 0;
        slot.validSize = 0;
    }
}

bool SnesCoreBridge::initialize(
    const std::string& coreLibPath,
    const std::string& systemDir,
    const std::string& saveDir
) {
    std::lock_guard<std::mutex> lock(coreMutex_);
    systemDir_ = systemDir;
    saveDir_ = saveDir;

    if (coreHandle_ != nullptr) {
        return true;
    }

    const char* candidates[] = {
        coreLibPath.c_str(),
        "libsnes9x_libretro_android.so",
        "libsnes9x2010_libretro_android.so"
    };

    for (const char* path : candidates) {
        if (path == nullptr || std::strlen(path) == 0) continue;
        coreHandle_ = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        if (coreHandle_ != nullptr) {
            LOGI("Núcleo oficial Libretro Snes9x cargado: %s", path);
            break;
        }
    }

    if (coreHandle_ == nullptr) {
        LOGE("No se encontró libsnes9x_libretro_android.so (%s)", dlerror());
        return false;
    }

    #define LOAD_SYM(field, symbol_name) \
        sym_.field = reinterpret_cast<decltype(sym_.field)>(dlsym(coreHandle_, symbol_name)); \
        if (!sym_.field) { LOGE("Símbolo faltante en núcleo: %s", symbol_name); closeCoreLibrary(); return false; }

    LOAD_SYM(set_environment, "retro_set_environment");
    LOAD_SYM(set_video_refresh, "retro_set_video_refresh");
    LOAD_SYM(set_audio_sample, "retro_set_audio_sample");
    LOAD_SYM(set_audio_sample_batch, "retro_set_audio_sample_batch");
    LOAD_SYM(set_input_poll, "retro_set_input_poll");
    LOAD_SYM(set_input_state, "retro_set_input_state");
    LOAD_SYM(init, "retro_init");
    LOAD_SYM(deinit, "retro_deinit");
    LOAD_SYM(api_version, "retro_api_version");
    LOAD_SYM(get_system_info, "retro_get_system_info");
    LOAD_SYM(get_system_av_info, "retro_get_system_av_info");
    LOAD_SYM(set_controller_port_device, "retro_set_controller_port_device");
    LOAD_SYM(reset, "retro_reset");
    LOAD_SYM(run, "retro_run");
    LOAD_SYM(serialize_size, "retro_serialize_size");
    LOAD_SYM(serialize, "retro_serialize");
    LOAD_SYM(unserialize, "retro_unserialize");
    LOAD_SYM(load_game, "retro_load_game");
    LOAD_SYM(unload_game, "retro_unload_game");
    LOAD_SYM(get_region, "retro_get_region");
    LOAD_SYM(get_memory_data, "retro_get_memory_data");
    LOAD_SYM(get_memory_size, "retro_get_memory_size");
    #undef LOAD_SYM

    sym_.set_environment(cbEnvironment);
    sym_.set_video_refresh(cbVideoRefresh);
    sym_.set_audio_sample(cbAudioSample);
    sym_.set_audio_sample_batch(cbAudioBatch);
    sym_.set_input_poll(cbInputPoll);
    sym_.set_input_state(cbInputState);
    sym_.init();

    return true;
}

void SnesCoreBridge::closeCoreLibrary() {
    if (coreHandle_ != nullptr) {
        if (sym_.deinit) {
            sym_.deinit();
        }
        dlclose(coreHandle_);
        coreHandle_ = nullptr;
        sym_ = CoreSymbols{};
    }
}

bool SnesCoreBridge::loadRomBytes(
    const std::string& romPathHint,
    const uint8_t* rawBytes,
    size_t byteLength
) {
    std::lock_guard<std::mutex> lock(coreMutex_);
    if (coreHandle_ == nullptr || sym_.load_game == nullptr) {
        LOGE("Núcleo Snes9x no inicializado antes de loadRomBytes");
        return false;
    }
    if (rawBytes == nullptr || byteLength < 1024) {
        LOGE("ROM inválida o vacía (%zu bytes)", byteLength);
        return false;
    }

    if (gameLoaded_ && sym_.unload_game != nullptr) {
        sym_.unload_game();
        gameLoaded_ = false;
    }

    // Eliminación automática de cabecera copiadora SMC/SWC de 512 bytes si está presente
    size_t headerOffset = 0;
    if ((byteLength % 1024) == 512 && byteLength > 512) {
        headerOffset = 512;
        LOGI("Cabecera copiadora SMC de 512 bytes ajustada automáticamente.");
    }

    const uint8_t* cleanPtr = rawBytes + headerOffset;
    const size_t cleanLen = byteLength - headerOffset;
    cleanRomBuffer_.assign(cleanPtr, cleanPtr + cleanLen);

    frameCounter_.store(0);
    inputMasks_[0].store(0);
    inputMasks_[1].store(0);
    macroHoldMasks_[0].store(0);
    macroHoldMasks_[1].store(0);
    macroTurboOffMasks_[0].store(0);
    macroTurboOffMasks_[1].store(0);
    rollbackBuffer_.clear();
    clearRewindBufferLocked();
    {
        std::lock_guard<std::mutex> audioLock(audioMutex_);
        resetAudioResamplerStateLocked();
    }

    retro_game_info info{};
    info.path = romPathHint.empty() ? "game.sfc" : romPathHint.c_str();
    info.data = cleanRomBuffer_.data();
    info.size = cleanRomBuffer_.size();
    info.meta = nullptr;

    pixelFormat_ = RETRO_PIXEL_FORMAT_RGB565;
    if (!sym_.load_game(&info)) {
        LOGE("El núcleo Snes9x rechazó cargar %s", info.path);
        return false;
    }

    // Configurar Puerto 1 y Puerto 2 como mandos estándar SNES (1 vs 1 / Co-op 2 Jugadores)
    sym_.set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    sym_.set_controller_port_device(1, RETRO_DEVICE_JOYPAD);

    retro_system_av_info avInfo{};
    sym_.get_system_av_info(&avInfo);
    targetFps_ = (avInfo.timing.fps > 10.0) ? avInfo.timing.fps : 60.0988;
    sampleRate_ = (avInfo.timing.sample_rate > 8000.0) ? avInfo.timing.sample_rate : 32040.0;

    const size_t stateSize = sym_.serialize_size();
    if (stateSize > 0) {
        scratchStateBuffer_.resize(stateSize);
        rollbackBuffer_.configureStateSize(stateSize);

        // Pre-reservar Ring Buffer de Rewind para 90 estados en RAM (TAREA 3)
        rewindBuffer_.resize(kRewindMaxStates);
        for (auto& slot : rewindBuffer_) {
            slot.stateData.resize(stateSize);
            slot.validSize = 0;
            slot.frameNumber = 0;
        }
    }

    gameLoaded_ = true;
    LOGI("ROM cargada con éxito: %zu bytes | %.4f FPS | DSP %.1f Hz -> Out %.1f Hz | State %zu B",
         cleanLen, targetFps_, sampleRate_, kOutputSampleRate, stateSize);
    return true;
}

void SnesCoreBridge::unloadRom() {
    std::lock_guard<std::mutex> lock(coreMutex_);
    if (gameLoaded_ && coreHandle_ != nullptr && sym_.unload_game != nullptr) {
        sym_.unload_game();
    }
    gameLoaded_ = false;
    cleanRomBuffer_.clear();
    rollbackBuffer_.clear();
    clearRewindBufferLocked();
}

void SnesCoreBridge::resetGame() {
    std::lock_guard<std::mutex> lock(coreMutex_);
    frameCounter_.store(0);
    rollbackBuffer_.clear();
    clearRewindBufferLocked();
    {
        std::lock_guard<std::mutex> audioLock(audioMutex_);
        resetAudioResamplerStateLocked();
    }
    if (gameLoaded_ && coreHandle_ != nullptr && sym_.reset != nullptr) {
        sym_.reset();
    }
}

void SnesCoreBridge::setRewinding(bool active) {
    bool prev = rewinding_.exchange(active);
    if (prev != active) {
        std::lock_guard<std::mutex> audioLock(audioMutex_);
        if (!active) {
            // Al soltar Rewind, activar crossfade suave de 32 muestras
            needsPostRollbackCrossfade_ = true;
        }
    }
}

int32_t SnesCoreBridge::getRewindAvailableCount() const {
    std::lock_guard<std::mutex> lock(coreMutex_);
    return static_cast<int32_t>(rewindCount_);
}

void SnesCoreBridge::stepFrame(bool recordRollbackSnapshot) {
    std::lock_guard<std::mutex> lock(coreMutex_);
    if (!gameLoaded_ || coreHandle_ == nullptr || sym_.run == nullptr) return;

    // En modo Online (recordRollbackSnapshot == true), el Rewind se desactiva automáticamente
    if (recordRollbackSnapshot) {
        if (rewinding_.load()) {
            rewinding_.store(false);
        }
    } else if (rewinding_.load()) {
        // TAREA 3: Ejecución de Rewind en partidas Locales (extrae estados en orden inverso LIFO)
        if (rewindCount_ > 0 && sym_.unserialize != nullptr && !rewindBuffer_.empty()) {
            rewindHead_ = (rewindHead_ + kRewindMaxStates - 1) % kRewindMaxStates;
            rewindCount_--;
            const auto& snap = rewindBuffer_[rewindHead_];
            if (snap.validSize > 0 && sym_.unserialize(snap.stateData.data(), snap.validSize)) {
                frameCounter_.store(snap.frameNumber);
                // Ejecutar 1 frame silenciando el audio para refrescar la textura de video en pantalla
                suppressAudioOnly_ = true;
                sym_.run();
                suppressAudioOnly_ = false;
            }
        }
        return;
    }

    const int32_t currentFrame = frameCounter_.load();
    const uint16_t p1 = getPlayerInputMask(0);
    const uint16_t p2 = getPlayerInputMask(1);

    if (recordRollbackSnapshot && sym_.serialize != nullptr && !scratchStateBuffer_.empty()) {
        if (sym_.serialize(scratchStateBuffer_.data(), scratchStateBuffer_.size())) {
            rollbackBuffer_.saveSnapshot(
                currentFrame,
                p1,
                p2,
                scratchStateBuffer_.data(),
                scratchStateBuffer_.size()
            );
        }
    } else if (!recordRollbackSnapshot &&
               (currentFrame % kRewindCaptureIntervalFrames) == 0 &&
               sym_.serialize != nullptr &&
               !rewindBuffer_.empty()) {
        // TAREA 3: Capturar snapshot en el Ring Buffer de 90 estados cada 6 frames (partidas Locales)
        auto& slot = rewindBuffer_[rewindHead_];
        if (!slot.stateData.empty() && sym_.serialize(slot.stateData.data(), slot.stateData.size())) {
            slot.frameNumber = currentFrame;
            slot.validSize = slot.stateData.size();
            rewindHead_ = (rewindHead_ + 1) % kRewindMaxStates;
            if (rewindCount_ < kRewindMaxStates) {
                rewindCount_++;
            }
        }
    }

    sym_.run();
    frameCounter_.store(currentFrame + 1);
}

bool SnesCoreBridge::rollbackAndReplay(
    int32_t targetFrame,
    int32_t currentFrame,
    const uint16_t* p1Inputs,
    const uint16_t* p2Inputs,
    size_t count
) {
    std::lock_guard<std::mutex> lock(coreMutex_);
    if (!gameLoaded_ || coreHandle_ == nullptr || sym_.unserialize == nullptr || sym_.run == nullptr) {
        return false;
    }
    if (targetFrame < 0 || currentFrame <= targetFrame) {
        return false;
    }
    const size_t framesToReplay = static_cast<size_t>(currentFrame - targetFrame);
    if (framesToReplay > count) {
        return false;
    }

    const auto* snap = rollbackBuffer_.findSnapshot(targetFrame);
    if (snap == nullptr || !sym_.unserialize(snap->stateData.data(), snap->validSize)) {
        return false;
    }

    const uint16_t savedLiveP1 = inputMasks_[0].load();
    const uint16_t savedLiveP2 = inputMasks_[1].load();
    const uint16_t savedHoldP1 = macroHoldMasks_[0].exchange(0);
    const uint16_t savedHoldP2 = macroHoldMasks_[1].exchange(0);
    const uint16_t savedTurboOffP1 = macroTurboOffMasks_[0].exchange(0);
    const uint16_t savedTurboOffP2 = macroTurboOffMasks_[1].exchange(0);

    {
        // TAREA 1: Armar crossfade de 32 muestras tras el rollback usando lastValidLeft_/lastValidRight_
        std::lock_guard<std::mutex> audioLock(audioMutex_);
        needsPostRollbackCrossfade_ = true;
    }

    for (size_t i = 0; i < framesToReplay; ++i) {
        const int32_t f = targetFrame + static_cast<int32_t>(i);
        const bool isLastReplayFrame = (i + 1 == framesToReplay);
        suppressAvOutput_ = !isLastReplayFrame;

        inputMasks_[0].store(p1Inputs[i]);
        inputMasks_[1].store(p2Inputs[i]);

        if (sym_.serialize != nullptr && !scratchStateBuffer_.empty()) {
            if (sym_.serialize(scratchStateBuffer_.data(), scratchStateBuffer_.size())) {
                rollbackBuffer_.saveSnapshot(
                    f,
                    p1Inputs[i],
                    p2Inputs[i],
                    scratchStateBuffer_.data(),
                    scratchStateBuffer_.size()
                );
            }
        }
        sym_.run();
    }

    suppressAvOutput_ = false;
    inputMasks_[0].store(savedLiveP1);
    inputMasks_[1].store(savedLiveP2);
    macroHoldMasks_[0].store(savedHoldP1);
    macroHoldMasks_[1].store(savedHoldP2);
    macroTurboOffMasks_[0].store(savedTurboOffP1);
    macroTurboOffMasks_[1].store(savedTurboOffP2);
    frameCounter_.store(currentFrame);
    return true;
}

void SnesCoreBridge::setPlayerInputMask(int port, uint16_t mask) {
    if (port == 0 || port == 1) {
        inputMasks_[port].store(mask);
    }
}

void SnesCoreBridge::setButtonState(int port, int buttonId, bool pressed) {
    if ((port != 0 && port != 1) || buttonId < 0 || buttonId > 15) {
        return;
    }
    const uint16_t bit = static_cast<uint16_t>(1u << buttonId);
    if (pressed) {
        macroHoldMasks_[port].fetch_or(bit);
        macroTurboOffMasks_[port].fetch_and(static_cast<uint16_t>(~bit));
    } else {
        macroHoldMasks_[port].fetch_and(static_cast<uint16_t>(~bit));
        macroTurboOffMasks_[port].fetch_and(static_cast<uint16_t>(~bit));
    }
}

void SnesCoreBridge::setMacroMaskState(int port, uint16_t mask, bool pressed, bool isTurboLowPhase) {
    if (port != 0 && port != 1) {
        return;
    }
    if (pressed) {
        macroHoldMasks_[port].fetch_or(mask);
        macroTurboOffMasks_[port].fetch_and(static_cast<uint16_t>(~mask));
    } else {
        macroHoldMasks_[port].fetch_and(static_cast<uint16_t>(~mask));
        if (isTurboLowPhase) {
            macroTurboOffMasks_[port].fetch_or(mask);
        } else {
            macroTurboOffMasks_[port].fetch_and(static_cast<uint16_t>(~mask));
        }
    }
}

void SnesCoreBridge::clearAllMacros(int port) {
    if (port == 0 || port == 1) {
        macroHoldMasks_[port].store(0);
        macroTurboOffMasks_[port].store(0);
    }
}

uint16_t SnesCoreBridge::getPlayerInputMask(int port) const {
    if (port != 0 && port != 1) return 0;
    const uint16_t base = inputMasks_[port].load();
    const uint16_t hold = macroHoldMasks_[port].load();
    const uint16_t turboOff = macroTurboOffMasks_[port].load();
    return static_cast<uint16_t>((base | hold) & static_cast<uint16_t>(~turboOff));
}

void SnesCoreBridge::copyLatestVideoFrame(std::vector<uint32_t>& outPixels, int& outWidth, int& outHeight) {
    std::lock_guard<std::mutex> lock(videoMutex_);
    outWidth = frameWidth_;
    outHeight = frameHeight_;
    const size_t total = static_cast<size_t>(frameWidth_ * frameHeight_);
    if (outPixels.size() < total) {
        outPixels.resize(total);
    }
    std::memcpy(outPixels.data(), rgbaFrame_.data(), total * sizeof(uint32_t));
}

size_t SnesCoreBridge::drainAudioPcm16(int16_t* outBuffer, size_t maxSamples) {
    std::lock_guard<std::mutex> lock(audioMutex_);
    const size_t available = audioFifo_.size() - audioReadPos_;
    const size_t toCopy = std::min(available, maxSamples);
    if (toCopy > 0) {
        std::memcpy(outBuffer, audioFifo_.data() + audioReadPos_, toCopy * sizeof(int16_t));
        audioReadPos_ += toCopy;
        if (audioReadPos_ >= audioFifo_.size()) {
            audioFifo_.clear();
            audioReadPos_ = 0;
        } else if (audioReadPos_ > 8192) {
            audioFifo_.erase(audioFifo_.begin(), audioFifo_.begin() + static_cast<ptrdiff_t>(audioReadPos_));
            audioReadPos_ = 0;
        }
    }
    return toCopy;
}

std::vector<uint8_t> SnesCoreBridge::serializeState() {
    std::lock_guard<std::mutex> lock(coreMutex_);
    if (!gameLoaded_ || coreHandle_ == nullptr || !sym_.serialize_size || !sym_.serialize) {
        return {};
    }
    const size_t sz = sym_.serialize_size();
    if (sz == 0) return {};
    std::vector<uint8_t> state(sz);
    return sym_.serialize(state.data(), sz) ? state : std::vector<uint8_t>{};
}

bool SnesCoreBridge::unserializeState(const uint8_t* data, size_t length) {
    std::lock_guard<std::mutex> lock(coreMutex_);
    if (!gameLoaded_ || coreHandle_ == nullptr || !sym_.unserialize || data == nullptr || length == 0) {
        return false;
    }
    const bool ok = sym_.unserialize(data, length);
    if (ok) {
        std::lock_guard<std::mutex> audioLock(audioMutex_);
        needsPostRollbackCrossfade_ = true;
    }
    return ok;
}

std::vector<uint8_t> SnesCoreBridge::readSram() {
    std::lock_guard<std::mutex> lock(coreMutex_);
    if (!gameLoaded_ || coreHandle_ == nullptr || !sym_.get_memory_data || !sym_.get_memory_size) {
        return {};
    }
    const size_t sz = sym_.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    const void* ptr = sym_.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (sz == 0 || ptr == nullptr) return {};

    std::vector<uint8_t> sram(sz);
    std::memcpy(sram.data(), ptr, sz);
    return sram;
}

bool SnesCoreBridge::writeSram(const uint8_t* data, size_t length) {
    std::lock_guard<std::mutex> lock(coreMutex_);
    if (!gameLoaded_ || coreHandle_ == nullptr || data == nullptr || length == 0) {
        return false;
    }
    const size_t sz = sym_.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void* ptr = sym_.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (sz == 0 || ptr == nullptr) return false;

    std::memcpy(ptr, data, std::min(sz, length));
    return true;
}

uint32_t SnesCoreBridge::getCurrentStateChecksum() {
    std::lock_guard<std::mutex> lock(coreMutex_);
    if (!gameLoaded_ || coreHandle_ == nullptr || !sym_.serialize || scratchStateBuffer_.empty()) {
        return 0;
    }
    if (sym_.serialize(scratchStateBuffer_.data(), scratchStateBuffer_.size())) {
        return rollbackBuffer_.computeFastCrc32(scratchStateBuffer_.data(), scratchStateBuffer_.size());
    }
    return 0;
}

bool SnesCoreBridge::onEnvironment(unsigned cmd, void* data) {
    const unsigned baseCmd = cmd & ~0x10000u;
    switch (baseCmd) {
        case RETRO_ENVIRONMENT_GET_OVERSCAN: {
            if (data) *static_cast<bool*>(data) = false;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            if (data) *static_cast<bool*>(data) = true;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            if (data) *static_cast<const char**>(data) = systemDir_.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            if (data) *static_cast<const char**>(data) = saveDir_.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            if (!data) return false;
            pixelFormat_ = *static_cast<const retro_pixel_format*>(data);
            return (pixelFormat_ == RETRO_PIXEL_FORMAT_RGB565 ||
                    pixelFormat_ == RETRO_PIXEL_FORMAT_XRGB8888 ||
                    pixelFormat_ == RETRO_PIXEL_FORMAT_0RGB1555);
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            if (!data) return false;
            auto* var = static_cast<retro_variable*>(data);
            var->value = nullptr;
            return false;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            if (data) *static_cast<bool*>(data) = false;
            return true;
        }
        default:
            return false;
    }
}

void SnesCoreBridge::onVideoRefresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (suppressAvOutput_ || data == nullptr || width == 0 || height == 0) {
        return;
    }

    std::lock_guard<std::mutex> lock(videoMutex_);
    frameWidth_ = static_cast<int>(width);
    frameHeight_ = static_cast<int>(height);
    const size_t totalPixels = width * height;
    if (rgbaFrame_.size() < totalPixels) {
        rgbaFrame_.resize(totalPixels);
    }

    const auto* srcBytes = static_cast<const uint8_t*>(data);

    if (pixelFormat_ == RETRO_PIXEL_FORMAT_RGB565) {
        for (unsigned y = 0; y < height; ++y) {
            const auto* row = reinterpret_cast<const uint16_t*>(srcBytes + y * pitch);
            uint32_t* dst = rgbaFrame_.data() + y * width;
            for (unsigned x = 0; x < width; ++x) {
                const uint16_t px = row[x];
                const uint32_t r = ((px >> 11) & 0x1Fu) * 255u / 31u;
                const uint32_t g = ((px >> 5)  & 0x3Fu) * 255u / 63u;
                const uint32_t b = ( px        & 0x1Fu) * 255u / 31u;
                dst[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        }
    } else if (pixelFormat_ == RETRO_PIXEL_FORMAT_XRGB8888) {
        for (unsigned y = 0; y < height; ++y) {
            const auto* row = reinterpret_cast<const uint32_t*>(srcBytes + y * pitch);
            uint32_t* dst = rgbaFrame_.data() + y * width;
            for (unsigned x = 0; x < width; ++x) {
                const uint32_t px = row[x];
                const uint32_t r = (px >> 16) & 0xFFu;
                const uint32_t g = (px >> 8)  & 0xFFu;
                const uint32_t b =  px        & 0xFFu;
                dst[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        }
    } else {
        for (unsigned y = 0; y < height; ++y) {
            const auto* row = reinterpret_cast<const uint16_t*>(srcBytes + y * pitch);
            uint32_t* dst = rgbaFrame_.data() + y * width;
            for (unsigned x = 0; x < width; ++x) {
                const uint16_t px = row[x];
                const uint32_t r = ((px >> 10) & 0x1Fu) * 255u / 31u;
                const uint32_t g = ((px >> 5)  & 0x1Fu) * 255u / 31u;
                const uint32_t b = ( px        & 0x1Fu) * 255u / 31u;
                dst[x] = 0xFF000000u | (b << 16) | (g << 8) | r;
            }
        }
    }
}

/**
 * TAREA 1: Resampling Estéreo en C++ (de 32040 Hz a 48000 Hz con interpolación Hermite de 4 puntos)
 * + Suavizado Post-Rollback / Post-Rewind de 32 muestras (<1 ms) para eliminar pops/clicks.
 */
size_t SnesCoreBridge::onAudioBatch(const int16_t* data, size_t frames) {
    if (data == nullptr || frames == 0) {
        return frames;
    }

    std::lock_guard<std::mutex> lock(audioMutex_);
    if (suppressAvOutput_ || suppressAudioOnly_) {
        needsPostRollbackCrossfade_ = true;
        return frames;
    }

    const double inRate = (sampleRate_ > 8000.0) ? sampleRate_ : 32040.0;
    const double ratio = inRate / kOutputSampleRate; // ~0.6675 (32040 / 48000)

    if (!resamplerPrimed_) {
        for (int i = 0; i < 4; ++i) {
            histL_[i] = data[0];
            histR_[i] = data[1];
        }
        lastValidLeft_ = data[0];
        lastValidRight_ = data[1];
        resamplerPhase_ = 0.0;
        resamplerPrimed_ = true;
    }

    const size_t startFifoSize = audioFifo_.size();

    for (size_t f = 0; f < frames; ++f) {
        const int16_t inL = data[f * 2];
        const int16_t inR = data[f * 2 + 1];

        histL_[0] = histL_[1];
        histL_[1] = histL_[2];
        histL_[2] = histL_[3];
        histL_[3] = inL;

        histR_[0] = histR_[1];
        histR_[1] = histR_[2];
        histR_[2] = histR_[3];
        histR_[3] = inR;

        while (resamplerPhase_ < 1.0) {
            const float t = static_cast<float>(resamplerPhase_);
            const int16_t outL = hermiteInterpolate4Point(
                static_cast<float>(histL_[0]),
                static_cast<float>(histL_[1]),
                static_cast<float>(histL_[2]),
                static_cast<float>(histL_[3]),
                t
            );
            const int16_t outR = hermiteInterpolate4Point(
                static_cast<float>(histR_[0]),
                static_cast<float>(histR_[1]),
                static_cast<float>(histR_[2]),
                static_cast<float>(histR_[3]),
                t
            );
            audioFifo_.push_back(outL);
            audioFifo_.push_back(outR);
            resamplerPhase_ += ratio;
        }
        resamplerPhase_ -= 1.0;
    }

    const size_t generatedSamples = audioFifo_.size() - startFifoSize;
    const size_t generatedFrames = generatedSamples / 2;

    // Crossfade post-rollback / post-rewind de 32 muestras estéreo (<1 ms a 48 kHz)
    if (needsPostRollbackCrossfade_ && generatedFrames > 0) {
        const size_t fadeFrames = std::min(generatedFrames, kCrossfadeFrames);
        for (size_t i = 0; i < fadeFrames; ++i) {
            const float alpha = static_cast<float>(i + 1) / static_cast<float>(fadeFrames + 1);
            const float invAlpha = 1.0f - alpha;
            const size_t idx = startFifoSize + i * 2;
            audioFifo_[idx] = static_cast<int16_t>(
                std::lrintf(invAlpha * static_cast<float>(lastValidLeft_) + alpha * static_cast<float>(audioFifo_[idx]))
            );
            audioFifo_[idx + 1] = static_cast<int16_t>(
                std::lrintf(invAlpha * static_cast<float>(lastValidRight_) + alpha * static_cast<float>(audioFifo_[idx + 1]))
            );
        }
        needsPostRollbackCrossfade_ = false;
    }

    // Guardar la última muestra L/R válida producida antes de cualquier posible rollback futuro
    if (audioFifo_.size() >= 2) {
        lastValidLeft_ = audioFifo_[audioFifo_.size() - 2];
        lastValidRight_ = audioFifo_[audioFifo_.size() - 1];
    }

    // Protección contra acumulación excesiva si el hilo de UI estuvo pausado (>350 ms a 48 kHz)
    if ((audioFifo_.size() - audioReadPos_) > 36000) {
        audioFifo_.erase(
            audioFifo_.begin(),
            audioFifo_.begin() + static_cast<ptrdiff_t>(audioFifo_.size() - 8192)
        );
        audioReadPos_ = 0;
        needsPostRollbackCrossfade_ = true;
    }

    return frames;
}

int16_t SnesCoreBridge::onInputState(unsigned port, unsigned device, unsigned /*index*/, unsigned id) {
    if (device != RETRO_DEVICE_JOYPAD && device != RETRO_DEVICE_NONE) {
        return 0;
    }
    if (port > 1 || id > 15) {
        return 0;
    }
    const uint16_t mask = getPlayerInputMask(static_cast<int>(port));
    return ((mask >> id) & 1u) ? 1 : 0;
}

} // namespace multisnes
