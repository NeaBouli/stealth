package com.securecall.crypto;

import android.util.Log;

/**
 * JNI-Bridge zur Rust Core Crypto Engine (securecall_core_crypto).
 *
 * Alle echte Kryptographie laeuft in Rust:
 * - XChaCha20-Poly1305 AEAD Verschluesselung
 * - X25519 Diffie-Hellman Key Exchange
 * - HKDF-SHA256 Key Derivation
 */
public class CoreCrypto {

    private static final String TAG = "CORE_CRYPTO";
    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("securecall_core_crypto");
            nativeLoaded = true;
            Log.i(TAG, "Native crypto library loaded successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load native library — using fallback", t);
        }
    }

    /** Prueft ob die native Library geladen ist. */
    public static boolean isNativeAvailable() {
        return nativeLoaded;
    }

    // --- Native Methoden (Rust FFI) ---

    /** Verschluesselt data mit key (32 Byte). Gibt [nonce|ciphertext|tag] zurueck. */
    public static native byte[] encrypt(byte[] key, byte[] data);

    /** Entschluesselt data mit key (32 Byte). Gibt plaintext zurueck. */
    public static native byte[] decrypt(byte[] key, byte[] data);

    /** X25519 DH + HKDF: leitet 32-Byte Session-Key ab. */
    public static native byte[] deriveSessionKey(byte[] localPriv, byte[] remotePub);

    /** Erzeugt X25519 Keypair: gibt 64 Byte [private(32)|public(32)] zurueck. */
    public static native byte[] generateKeyPair();

    /** Fuehrt Rust-internen Self-Test durch. */
    public static native boolean selfTest();
}
