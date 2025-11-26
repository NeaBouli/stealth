package com.securecall.app.ghostnet.media.crypto

import android.util.Log
import com.securecall.app.ghostnet.media.MediaFrame

/**
 * BACKEND-64:
 * Placeholder-Decryptor.
 * Später ersetzt durch JNI/Rust -> XChaCha20-Poly1305 (oder AES-GCM).
 */
object MediaDecryptor {

    private const val TAG = "MEDIA_DECRYPT"

    fun decrypt(frame: MediaFrame): ByteArray {
        Log.d(TAG, "decrypt(): called, size=${frame.data.size}")

        // !!! PLACEHOLDER !!!
        // Rückgabe = Originaldaten (NICHT entschlüsselt)
        // später:
        // return RustCrypto.decrypt(frame.data)

        return frame.data
    }
}
