//! securecall_core_crypto
//!
//! CRYPTO-02: Skeleton der Core Crypto Engine.
//! Ziel: später XChaCha20-Poly1305 / AES-GCM, Key-Derivation, Session-Handling.
//!
//! Aktuell: nur Platzhalter-Funktionen, die Byte-Arrays durchreichen.

pub struct SessionKey {
    /// Placeholder-Key-Material
    pub bytes: [u8; 32],
}

impl SessionKey {
    /// Erzeugt einen Dummy-Key (später: echte KDF / DH-Resultat).
    pub fn dummy() -> Self {
        Self { bytes: [0u8; 32] }
    }
}

/// Placeholder-Encrypt:
/// nimmt Klartext + SessionKey und gibt denselben Buffer zurück.
pub fn encrypt_in_place(_key: &SessionKey, buf: &mut [u8]) {
    // TODO (CRYPTO-02+): echte Verschlüsselung (XChaCha20-Poly1305)
    // Aktuell: No-Op, nur Skeleton.
}

/// Placeholder-Decrypt:
/// nimmt Ciphertext + SessionKey und gibt denselben Buffer zurück.
pub fn decrypt_in_place(_key: &SessionKey, buf: &mut [u8]) {
    // TODO (CRYPTO-02+): echte Entschlüsselung
    // Aktuell: No-Op, nur Skeleton.
}

/// Kleiner Self-Test (wird später über proper Tests ersetzt).
pub fn self_test() -> bool {
    let key = SessionKey::dummy();
    let mut data = [1u8, 2, 3, 4];

    encrypt_in_place(&key, &mut data);
    decrypt_in_place(&key, &mut data);

    data == [1u8, 2, 3, 4]
}
