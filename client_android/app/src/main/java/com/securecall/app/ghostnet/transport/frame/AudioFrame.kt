package com.securecall.app.ghostnet.transport.frame

// BACKEND-38 / ANDROID-03:
// Placeholder AudioFrame — Grundstruktur für zukünftige Audio-Pipeline
class AudioFrame(
    val timestamp: Long,
    val type: FrameType,
    val data: ByteArray
) {

    enum class FrameType {
        RAW,        // ungefiltertes PCM oder RTP-Paket
        ENCRYPTED,  // SRTP / Noise-based encryption
        CONTROL     // z.B. DTX, Silence, Heartbeat
    }

    override fun toString(): String {
        return "AudioFrame(ts=$timestamp, type=$type, size=${data.size})"
    }
}
