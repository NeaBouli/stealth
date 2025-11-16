// CRYPTO-01 Skeleton: AEAD-Modul
// Hier folgen später AEAD-Typen und -Funktionen (z. B. XChaCha20-Poly1305).

pub struct AeadKey {
    _private: (),
}

pub fn encrypt_frame_aead(_key: &AeadKey, _plaintext: &[u8]) -> Vec<u8> {
    // TODO: Implementierung in späteren Tasks
    unimplemented!();
}

pub fn decrypt_frame_aead(_key: &AeadKey, _ciphertext: &[u8]) -> Vec<u8> {
    // TODO: Implementierung in späteren Tasks
    unimplemented!();
}
