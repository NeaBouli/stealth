package com.securecall.app.ghostnet.media.crypto

import android.util.Log
import com.securecall.app.ghostnet.media.MediaFrame

object MediaDecrypt {

    private const val TAG = "MEDIA_DECRYPT"

    // BACKEND-64: Platzhalter-Decryption
    // Später: XChaCha20-Poly1305 / AES-GCM etc. mit Session-Key
    fun decrypt(frame: MediaFrame): MediaFrame {
        val size = frame.data.size
        Log.d(TAG, "decrypt(): got frame size=$size (placeholder: no real decryption)")

        // MVP: unveränderte Kopie zurückgeben
        val copy = frame.data.copyOf()
        return MediaFrame(copy, frame.timestamp)
    }
}
