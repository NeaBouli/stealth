#include <jni.h>
#include <android/log.h>
#include <opus.h>
#include <mutex>
#include <unordered_map>
#include <atomic>

#define LOG_TAG "native_opus"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 20ms frame at 48kHz = 960 samples
static constexpr int FRAME_SIZE = 960;
// Max encoded packet size
static constexpr int MAX_PACKET = 4000;

struct CodecPair {
    OpusEncoder *encoder;
    OpusDecoder *decoder;
    int sampleRate;
    int channels;
    int frameSize;
};

static std::mutex sMutex;
static std::unordered_map<long, CodecPair *> sCodecs;
static std::atomic<long> sNextHandle{1};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_securecall_app_ghostnet_media_native_NativeOpus_nativeInit(
        JNIEnv *env,
        jobject /* thiz */,
        jint sampleRate,
        jint channels
) {
    int encErr = 0, decErr = 0;

    OpusEncoder *enc = opus_encoder_create(sampleRate, channels,
                                           OPUS_APPLICATION_VOIP, &encErr);
    if (encErr != OPUS_OK || enc == nullptr) {
        LOGE("opus_encoder_create failed: %s", opus_strerror(encErr));
        return -1;
    }

    OpusDecoder *dec = opus_decoder_create(sampleRate, channels, &decErr);
    if (decErr != OPUS_OK || dec == nullptr) {
        LOGE("opus_decoder_create failed: %s", opus_strerror(decErr));
        opus_encoder_destroy(enc);
        return -1;
    }

    // Configure encoder: 32 kbps, complexity 5
    opus_encoder_ctl(enc, OPUS_SET_BITRATE(32000));
    opus_encoder_ctl(enc, OPUS_SET_COMPLEXITY(5));
    opus_encoder_ctl(enc, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));

    int frameSize = sampleRate / 50; // 20ms

    auto *pair = new CodecPair{enc, dec, sampleRate, channels, frameSize};
    long handle = sNextHandle.fetch_add(1);

    {
        std::lock_guard<std::mutex> lock(sMutex);
        sCodecs[handle] = pair;
    }

    LOGD("nativeInit(sr=%d, ch=%d) → handle=%ld, frameSize=%d",
         sampleRate, channels, handle, frameSize);
    return handle;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_securecall_app_ghostnet_media_native_NativeOpus_nativeEncode(
        JNIEnv *env,
        jobject /* thiz */,
        jlong handle,
        jshortArray pcm
) {
    CodecPair *pair = nullptr;
    {
        std::lock_guard<std::mutex> lock(sMutex);
        auto it = sCodecs.find(handle);
        if (it == sCodecs.end()) {
            LOGE("nativeEncode: invalid handle %ld", (long) handle);
            return env->NewByteArray(0);
        }
        pair = it->second;
    }

    jsize pcmLen = env->GetArrayLength(pcm);
    jshort *pcmData = env->GetShortArrayElements(pcm, nullptr);
    if (pcmData == nullptr) {
        LOGE("nativeEncode: GetShortArrayElements returned null");
        return env->NewByteArray(0);
    }

    unsigned char packet[MAX_PACKET];
    int encoded = opus_encode(pair->encoder, pcmData, pcmLen, packet, MAX_PACKET);

    env->ReleaseShortArrayElements(pcm, pcmData, JNI_ABORT);

    if (encoded < 0) {
        LOGE("opus_encode failed: %s", opus_strerror(encoded));
        return env->NewByteArray(0);
    }

    jbyteArray out = env->NewByteArray(encoded);
    env->SetByteArrayRegion(out, 0, encoded, reinterpret_cast<jbyte *>(packet));
    return out;
}

extern "C"
JNIEXPORT jshortArray JNICALL
Java_com_securecall_app_ghostnet_media_native_NativeOpus_nativeDecode(
        JNIEnv *env,
        jobject /* thiz */,
        jlong handle,
        jbyteArray encoded
) {
    CodecPair *pair = nullptr;
    {
        std::lock_guard<std::mutex> lock(sMutex);
        auto it = sCodecs.find(handle);
        if (it == sCodecs.end()) {
            LOGE("nativeDecode: invalid handle %ld", (long) handle);
            return env->NewShortArray(0);
        }
        pair = it->second;
    }

    jsize len = env->GetArrayLength(encoded);
    jbyte *data = env->GetByteArrayElements(encoded, nullptr);
    if (data == nullptr) {
        LOGE("nativeDecode: GetByteArrayElements returned null");
        return env->NewShortArray(0);
    }

    // Output buffer: one frame of PCM
    opus_int16 pcmBuf[FRAME_SIZE * 2]; // max stereo
    int decoded = opus_decode(pair->decoder,
                              reinterpret_cast<const unsigned char *>(data),
                              len,
                              pcmBuf,
                              pair->frameSize,
                              0); // no FEC

    env->ReleaseByteArrayElements(encoded, data, JNI_ABORT);

    if (decoded < 0) {
        LOGE("opus_decode failed: %s", opus_strerror(decoded));
        return env->NewShortArray(0);
    }

    int totalSamples = decoded * pair->channels;
    jshortArray out = env->NewShortArray(totalSamples);
    env->SetShortArrayRegion(out, 0, totalSamples, pcmBuf);
    return out;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_securecall_app_ghostnet_media_native_NativeOpus_nativeRelease(
        JNIEnv *env,
        jobject /* thiz */,
        jlong handle
) {
    std::lock_guard<std::mutex> lock(sMutex);
    auto it = sCodecs.find(handle);
    if (it == sCodecs.end()) {
        LOGE("nativeRelease: invalid handle %ld", (long) handle);
        return;
    }

    CodecPair *pair = it->second;
    opus_encoder_destroy(pair->encoder);
    opus_decoder_destroy(pair->decoder);
    delete pair;
    sCodecs.erase(it);

    LOGD("nativeRelease(handle=%ld): destroyed", (long) handle);
}
