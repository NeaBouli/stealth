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

package com.securecall.app.ghostnet.media.crypto

import android.util.Log
import com.securecall.app.ghostnet.crypto.SessionCryptoContext
import com.securecall.app.ghostnet.media.MediaFrame

/**
 * CRYPTO-06:
 * Key-aware decryptor (noch immer placeholder).
 */

object MediaDecryptor {

    private const val TAG = "MEDIA_DECRYPT"

    private fun selectRecvKey(ctx: SessionCryptoContext?): ByteArray? {
        return ctx?.recvKey
    }

    fun decryptWithKey(frame: MediaFrame, ctx: SessionCryptoContext?): MediaFrame {
        val key = selectRecvKey(ctx)

        if (key == null) {
            Log.w(TAG, "decryptWithKey(): NO recvKey → returning original frame")
            return frame
        }

        Log.d(TAG, "decryptWithKey(): using recvKey[${key.size}] (placeholder, no real crypto)")

        // zukünftige Stelle für echte Decryption
        return frame
    }
}

// CRYPTO-28: SessionCipherBinding nutzen
fun decryptWithSession(frame: MediaFrame): ByteArray {
    return com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.decryptFrame(frame)
}

// CRYPTO-34: vollständiger FrameV1 decrypt Pfad
fun decryptFrameV1(frame: MediaFrame): ByteArray {
    val ctx = com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.activeSession
    if (ctx == null) return frame.data
    return com.securecall.app.ghostnet.crypto.binding.SessionCipherBinding.decryptFrameV1(ctx, frame.data)
}
