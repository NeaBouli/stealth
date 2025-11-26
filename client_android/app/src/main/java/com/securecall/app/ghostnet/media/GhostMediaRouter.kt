package com.securecall.app.ghostnet.media

import android.util.Log

object GhostMediaRouter {

    private const val TAG = "MEDIA_ROUTER"

    // BACKEND-63: Entry-Point für MediaFrames
    fun route(frame: MediaFrame) {
        Log.d(TAG, "MediaFrame routed: size=${frame.data.size}, ts=${frame.timestamp}")

        // später:
        // - decrypt
        // - jitter buffer
        // - decode (Opus)
        // - play
    }
}

    // BACKEND-64: Decrypt-Schicht vor weiterer Verarbeitung
    private fun decryptFrame(frame: MediaFrame): MediaFrame {
        return com.securecall.app.ghostnet.media.crypto.MediaDecrypt.decrypt(frame)
    }

    // BACKEND-64: Decrypt-Pipeline einbauen
    private fun decrypt(frame: MediaFrame): ByteArray {
        return com.securecall.app.ghostnet.media.crypto.MediaDecryptor.decrypt(frame)
    }

    // innerhalb von route():
    val raw = decrypt(frame)

    // später: decode(raw) → play()

    // BACKEND-65: Decode-Etappe
    private fun decodeAudio(bytes: ByteArray): ShortArray {
        return com.securecall.app.ghostnet.media.decode.MediaDecoder.decode(bytes)
    }

    // BACKEND-65: Audio decoding
    val pcm = decodeAudio(raw)

    // später: playAudio(pcm)

    // BACKEND-65: Debug Logging
    android.util.Log.d(TAG, "Decoded PCM samples: len=" + pcm.size)

    // BACKEND-66: Playback-Schicht – ruft AudioTrack-Wrapper auf
    private fun playPcm(pcm: ShortArray) {
        try {
            com.securecall.app.ghostnet.media.playback.AudioPlayer.play(pcm)
            android.util.Log.d(TAG, "playPcm(): sent ${pcm.size} samples to AudioPlayer")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "playPcm() failed", t)
        }
    }

    // BACKEND-66: nach decodeAudio(raw) aufrufen
    // Beispiel (innerhalb von route()):
    // val raw = decrypt(frame)
    // val pcm = decodeAudio(raw)
    // playPcm(pcm)
