package com.securecall.app.ghostnet.media.codec

import android.util.Log

/**
 * PATCH 213:
 * Opus Decoder Skeleton.
 *
 * Aktuell:
 *  - KEINE echte Opus-Decodierung
 *  - KEIN JNI, KEIN Rust, KEIN libopus
 *
 * Zweck:
 *  - Zentrale Schnittstelle, an die später die native Implementierung andockt.
 *  - Kann von AudioDecoder / MediaPipeline verwendet werden, ohne API später zu ändern.
 */
object OpusDecoder {

    private const val TAG = "OPUS_DECODER"

    private var initialised = false

    /**
     * Wird pro Session einmal aufgerufen, wenn ein neuer Call startet.
     * Später:
     *  - Native Opus-Instanz anlegen
     *  - Sampling-Konfiguration setzen
     */
    fun init(sampleRate: Int = 48000, channels: Int = 1) {
        if (initialised) {
            Log.w(TAG, "init(): already initialised")
            return
        }
        initialised = true
        Log.d(TAG, "init(): sampleRate=$sampleRate, channels=$channels (SKELETON, no native)")
    }

    /**
     * Dekodiert einen einzelnen Opus-Frame.
     *
     * Jetzt:
     *  - Fake-Decode: ByteArray -> ShortArray via simple Mapping
     * Später:
     *  - JNI → native libopus / Rust
     */
    fun decode(encoded: ByteArray): ShortArray {
        if (!initialised) {
            Log.w(TAG, "decode(): called before init()")
        }

        if (encoded.isEmpty()) {
            Log.w(TAG, "decode(): empty input, returning empty PCM")
            return ShortArray(0)
        }

        // PLACEHOLDER:
        // Jedes Short aus 2 Bytes rekonstruieren (Fake-PCM)
        val pcm = ShortArray(encoded.size / 2)
        var i = 0
        var j = 0
        while (i < encoded.size - 1) {
            val low = encoded[i].toInt() and 0xFF
            val high = encoded[i + 1].toInt() shl 8
            pcm[j] = (high or low).toShort()
            i += 2
            j += 1
        }

        Log.d(TAG, "decode(): encoded=${encoded.size} bytes -> pcm=${pcm.size} samples (FAKE)")
        return pcm
    }

    /**
     * Session-bezogene Ressourcen freigeben.
     * Später:
     *  - native Instanz zerstören
     *  - Puffer freigeben
     */
    fun release() {
        if (!initialised) {
            Log.w(TAG, "release(): called but not initialised")
            return
        }
        initialised = false
        Log.d(TAG, "release(): decoder state reset (SKELETON)")
    }

    fun isInitialised(): Boolean = initialised
}
