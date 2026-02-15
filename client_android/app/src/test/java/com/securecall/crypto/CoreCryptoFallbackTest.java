package com.securecall.crypto;

import static org.junit.Assert.*;

import org.junit.Test;

public class CoreCryptoFallbackTest {

    @Test
    public void testIsNativeAvailable_returnsFalseOnJvm() {
        // On JVM (not Android), the static initializer cannot load
        // libsecurecall_core_crypto.so, so nativeLoaded remains false.
        assertFalse(CoreCrypto.isNativeAvailable());
    }
}
