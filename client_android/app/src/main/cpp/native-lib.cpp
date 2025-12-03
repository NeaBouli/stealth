#include <jni.h>
#include <android/log.h>

#define LOG_TAG "native_opus_stub"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/**
 * PATCH 215:
 * C++ JNI Stub für Opus Decoder.
 *
 * Noch keine Opus-Funktionalität!
 */

extern "C"
JNIEXPORT jlong JNICALL
Java_com_securecall_app_ghostnet_media_native_NativeOpus_nativeInit(
        JNIEnv *env,
        jobject thiz,
        jint sampleRate,
        jint channels
) {
    LOGD("nativeInit(sampleRate=%d, channels=%d) — STUB", sampleRate, channels);
    return 1; // fake handle
}

extern "C"
JNIEXPORT jshortArray JNICALL
Java_com_securecall_app_ghostnet_media_native_NativeOpus_nativeDecode(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jbyteArray encoded
) {
    jsize len = env->GetArrayLength(encoded);
    LOGD("nativeDecode(len=%d) — STUB", len);

    // Return empty PCM (skeleton)
    jshortArray out = env->NewShortArray(len / 2);
    return out;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_securecall_app_ghostnet_media_native_NativeOpus_nativeRelease(
        JNIEnv *env,
        jobject thiz,
        jlong handle
) {
    LOGD("nativeRelease() — STUB");
}
