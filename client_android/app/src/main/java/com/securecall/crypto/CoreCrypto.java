package com.securecall.crypto;

/**
 * CRYPTO-03:
 * Skeleton für spätere JNI-Bindings zur Rust-CoreCrypto Engine.
 * Noch keine echte Implementierung.
 */
public class CoreCrypto {

    static {
        try {
            System.loadLibrary("securecall_core_crypto"); // libsecurecall_core_crypto.so
        } catch (Throwable t) {
            android.util.Log.e("CORE_CRYPTO", "Failed to load native library", t);
        }
    }

    // Native Stubs (folgen später)
    public static native byte[] encrypt(byte[] key, byte[] data);
    public static native byte[] decrypt(byte[] key, byte[] data);

    public static native byte[] deriveSessionKey(byte[] localPriv, byte[] remotePub);

    // Debug-Call
    public static native boolean selfTest();
}
