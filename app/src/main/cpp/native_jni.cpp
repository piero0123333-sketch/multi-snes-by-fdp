#include <jni.h>
#include <string>
#include <vector>
#include "snes_core_bridge.h"

using multisnes::SnesCoreBridge;

namespace {
std::string jstringToStdString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (chars == nullptr) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}
} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeInit(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring coreLibPath,
    jstring systemDir,
    jstring saveDir
) {
    return SnesCoreBridge::instance().initialize(
        jstringToStdString(env, coreLibPath),
        jstringToStdString(env, systemDir),
        jstringToStdString(env, saveDir)
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeLoadRomBytes(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring romPathHint,
    jbyteArray romBytes
) {
    if (romBytes == nullptr) return JNI_FALSE;
    const jsize length = env->GetArrayLength(romBytes);
    if (length <= 0) return JNI_FALSE;

    std::vector<uint8_t> buffer(static_cast<size_t>(length));
    env->GetByteArrayRegion(romBytes, 0, length, reinterpret_cast<jbyte*>(buffer.data()));

    return SnesCoreBridge::instance().loadRomBytes(
        jstringToStdString(env, romPathHint),
        buffer.data(),
        buffer.size()
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeUnloadRom(JNIEnv* /*env*/, jobject /*thiz*/) {
    SnesCoreBridge::instance().unloadRom();
}

JNIEXPORT void JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeReset(JNIEnv* /*env*/, jobject /*thiz*/) {
    SnesCoreBridge::instance().resetGame();
}

JNIEXPORT void JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeStepFrame(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jboolean recordRollbackSnapshot
) {
    SnesCoreBridge::instance().stepFrame(recordRollbackSnapshot == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeSetInputMask(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint port,
    jint mask
) {
    SnesCoreBridge::instance().setPlayerInputMask(port, static_cast<uint16_t>(mask & 0xFFFF));
}

JNIEXPORT void JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeSetButtonState(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint port,
    jint buttonId,
    jboolean pressed
) {
    SnesCoreBridge::instance().setButtonState(port, buttonId, pressed == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeSetMacroMaskState(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint port,
    jint bitMask,
    jboolean pressed,
    jboolean isTurboLowPhase
) {
    SnesCoreBridge::instance().setMacroMaskState(
        port,
        static_cast<uint16_t>(bitMask & 0xFFFF),
        pressed == JNI_TRUE,
        isTurboLowPhase == JNI_TRUE
    );
}

JNIEXPORT void JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeClearAllMacros(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint port
) {
    SnesCoreBridge::instance().clearAllMacros(port);
}

JNIEXPORT jint JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeGetEffectiveInputMask(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jint port
) {
    return static_cast<jint>(SnesCoreBridge::instance().getPlayerInputMask(port));
}

JNIEXPORT jint JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeGetFrameNumber(JNIEnv* /*env*/, jobject /*thiz*/) {
    return SnesCoreBridge::instance().getCurrentFrameNumber();
}

JNIEXPORT jboolean JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeRollbackAndReplay(
    JNIEnv* env,
    jobject /*thiz*/,
    jint targetFrame,
    jint currentFrame,
    jshortArray p1History,
    jshortArray p2History
) {
    if (p1History == nullptr || p2History == nullptr) return JNI_FALSE;
    const jsize len1 = env->GetArrayLength(p1History);
    const jsize len2 = env->GetArrayLength(p2History);
    const jsize count = (len1 < len2) ? len1 : len2;
    if (count <= 0) return JNI_FALSE;

    std::vector<uint16_t> p1(static_cast<size_t>(count));
    std::vector<uint16_t> p2(static_cast<size_t>(count));
    env->GetShortArrayRegion(p1History, 0, count, reinterpret_cast<jshort*>(p1.data()));
    env->GetShortArrayRegion(p2History, 0, count, reinterpret_cast<jshort*>(p2.data()));

    return SnesCoreBridge::instance().rollbackAndReplay(
        targetFrame,
        currentFrame,
        p1.data(),
        p2.data(),
        static_cast<size_t>(count)
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeCopyVideoRgba(
    JNIEnv* env,
    jobject /*thiz*/,
    jintArray outPixels,
    jintArray outDimensions
) {
    if (outPixels == nullptr || outDimensions == nullptr) return 0;
    static thread_local std::vector<uint32_t> tempPixels;
    int width = 0;
    int height = 0;
    SnesCoreBridge::instance().copyLatestVideoFrame(tempPixels, width, height);

    const jsize maxCapacity = env->GetArrayLength(outPixels);
    const jsize needed = static_cast<jsize>(width * height);
    const jsize toCopy = (needed <= maxCapacity) ? needed : maxCapacity;

    if (toCopy > 0) {
        env->SetIntArrayRegion(outPixels, 0, toCopy, reinterpret_cast<const jint*>(tempPixels.data()));
    }
    jint dims[2] = {width, height};
    env->SetIntArrayRegion(outDimensions, 0, 2, dims);
    return toCopy;
}

JNIEXPORT jint JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeDrainAudioPcm16(
    JNIEnv* env,
    jobject /*thiz*/,
    jshortArray outAudio
) {
    if (outAudio == nullptr) return 0;
    const jsize capacity = env->GetArrayLength(outAudio);
    if (capacity <= 0) return 0;

    std::vector<int16_t> temp(static_cast<size_t>(capacity));
    const size_t readSamples = SnesCoreBridge::instance().drainAudioPcm16(
        temp.data(),
        static_cast<size_t>(capacity)
    );
    if (readSamples > 0) {
        env->SetShortArrayRegion(outAudio, 0, static_cast<jsize>(readSamples), temp.data());
    }
    return static_cast<jint>(readSamples);
}

JNIEXPORT jdouble JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeGetTargetFps(JNIEnv* /*env*/, jobject /*thiz*/) {
    return SnesCoreBridge::instance().getTargetFps();
}

JNIEXPORT jdouble JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeGetSampleRate(JNIEnv* /*env*/, jobject /*thiz*/) {
    return SnesCoreBridge::instance().getSampleRate();
}

JNIEXPORT jbyteArray JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeSerializeState(JNIEnv* env, jobject /*thiz*/) {
    std::vector<uint8_t> bytes = SnesCoreBridge::instance().serializeState();
    if (bytes.empty()) return nullptr;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(bytes.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeUnserializeState(
    JNIEnv* env,
    jobject /*thiz*/,
    jbyteArray stateBytes
) {
    if (stateBytes == nullptr) return JNI_FALSE;
    const jsize len = env->GetArrayLength(stateBytes);
    if (len <= 0) return JNI_FALSE;
    std::vector<uint8_t> buf(static_cast<size_t>(len));
    env->GetByteArrayRegion(stateBytes, 0, len, reinterpret_cast<jbyte*>(buf.data()));
    return SnesCoreBridge::instance().unserializeState(buf.data(), buf.size()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeReadSram(JNIEnv* env, jobject /*thiz*/) {
    std::vector<uint8_t> bytes = SnesCoreBridge::instance().readSram();
    if (bytes.empty()) return nullptr;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(bytes.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeWriteSram(
    JNIEnv* env,
    jobject /*thiz*/,
    jbyteArray sramBytes
) {
    if (sramBytes == nullptr) return JNI_FALSE;
    const jsize len = env->GetArrayLength(sramBytes);
    if (len <= 0) return JNI_FALSE;
    std::vector<uint8_t> buf(static_cast<size_t>(len));
    env->GetByteArrayRegion(sramBytes, 0, len, reinterpret_cast<jbyte*>(buf.data()));
    return SnesCoreBridge::instance().writeSram(buf.data(), buf.size()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeGetStateChecksum(JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(SnesCoreBridge::instance().getCurrentStateChecksum());
}

JNIEXPORT void JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeSetRewinding(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jboolean active
) {
    SnesCoreBridge::instance().setRewinding(active == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeIsRewinding(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return SnesCoreBridge::instance().isRewinding() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_fdp_multisnes_core_SnesNativeBridge_nativeGetRewindCount(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return static_cast<jint>(SnesCoreBridge::instance().getRewindAvailableCount());
}

} // extern "C"
