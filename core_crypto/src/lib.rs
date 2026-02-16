//! securecall_core_crypto
//!
//! Core Crypto Engine fuer SecureCall.
//!
//! - XChaCha20-Poly1305 AEAD-Verschluesselung
//! - X25519 Diffie-Hellman Key Exchange
//! - HKDF-SHA256 Key Derivation
//! - Sichere Speicherbereinigung (Zeroize)

pub mod aead;
pub mod identity;
pub mod session;
pub mod utils;
pub mod ffi;

use aead::{AeadKey, encrypt_frame_aead, decrypt_frame_aead};
use zeroize::{Zeroize, ZeroizeOnDrop};

/// Session-Key: 256-Bit Schluessel fuer AEAD-Verschluesselung.
///
/// Wird automatisch gezeroed wenn er aus dem Scope faellt.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct SessionKey {
    pub(crate) bytes: [u8; 32],
}

impl SessionKey {
    /// Erzeugt einen SessionKey aus 32 Byte Schlüsselmaterial.
    pub fn from_bytes(bytes: [u8; 32]) -> Self {
        Self { bytes }
    }

    /// Gibt eine Referenz auf das rohe Key-Material zurueck.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.bytes
    }

    /// Erzeugt einen zufaelligen SessionKey.
    pub fn random() -> Self {
        Self {
            bytes: utils::random_array(),
        }
    }
}

/// Verschluesselt einen Plaintext-Frame mit dem SessionKey.
///
/// Gibt `[nonce (24 B) | ciphertext | tag (16 B)]` zurueck.
pub fn encrypt(key: &SessionKey, plaintext: &[u8]) -> Result<Vec<u8>, aead::AeadError> {
    let aead_key = AeadKey::from_bytes(key.bytes);
    encrypt_frame_aead(&aead_key, plaintext)
}

/// Entschluesselt einen Frame mit dem SessionKey.
///
/// Prueft Authentizitaet (Poly1305 Tag) und gibt den Plaintext zurueck.
pub fn decrypt(key: &SessionKey, data: &[u8]) -> Result<Vec<u8>, aead::AeadError> {
    let aead_key = AeadKey::from_bytes(key.bytes);
    decrypt_frame_aead(&aead_key, data)
}

/// Self-Test: prueft ob die Crypto-Engine korrekt funktioniert.
///
/// Testet AEAD-Roundtrip, Key-Exchange und Key-Derivation.
pub fn self_test() -> bool {
    // 1. AEAD Roundtrip
    let key = SessionKey::random();
    let plaintext = b"SecureCall self-test payload";

    let encrypted = match encrypt(&key, plaintext) {
        Ok(e) => e,
        Err(_) => return false,
    };

    let decrypted = match decrypt(&key, &encrypted) {
        Ok(d) => d,
        Err(_) => return false,
    };

    if decrypted != plaintext {
        return false;
    }

    // 2. Tamper Detection
    let mut tampered = encrypted.clone();
    let last = tampered.len() - 1;
    tampered[last] ^= 0xFF;
    if decrypt(&key, &tampered).is_ok() {
        return false; // Sollte fehlschlagen!
    }

    // 3. Key Exchange
    let alice = identity::IdentityKeyPair::generate();
    let bob = identity::IdentityKeyPair::generate();

    let secret_a = alice.diffie_hellman(bob.public_key());
    let secret_b = bob.diffie_hellman(alice.public_key());

    if secret_a.as_bytes() != secret_b.as_bytes() {
        return false;
    }

    // 4. Session Key Derivation
    let session_a = session::SessionState::from_shared_secret(secret_a.as_bytes(), None);
    let session_b = session::SessionState::from_shared_secret(secret_b.as_bytes(), None);

    let msg = b"end-to-end test";
    let enc = match encrypt_frame_aead(session_a.aead_key(), msg) {
        Ok(e) => e,
        Err(_) => return false,
    };
    let dec = match decrypt_frame_aead(session_b.aead_key(), &enc) {
        Ok(d) => d,
        Err(_) => return false,
    };

    dec == msg
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn self_test_passes() {
        assert!(self_test());
    }

    #[test]
    fn encrypt_decrypt_roundtrip() {
        let key = SessionKey::random();
        let plaintext = b"Hello World";

        let encrypted = encrypt(&key, plaintext).unwrap();
        let decrypted = decrypt(&key, &encrypted).unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn random_keys_are_unique() {
        let k1 = SessionKey::random();
        let k2 = SessionKey::random();
        assert_ne!(k1.bytes, k2.bytes);
    }

    #[test]
    fn full_e2e_flow() {
        // Alice und Bob machen Key Exchange
        let alice = identity::IdentityKeyPair::generate();
        let bob = identity::IdentityKeyPair::generate();

        let secret_a = alice.diffie_hellman(bob.public_key());
        let secret_b = bob.diffie_hellman(alice.public_key());

        // Beide leiten Session-Keys ab
        let session_a = session::SessionState::from_shared_secret(
            secret_a.as_bytes(), Some(b"call-session-42")
        );
        let session_b = session::SessionState::from_shared_secret(
            secret_b.as_bytes(), Some(b"call-session-42")
        );

        // Alice verschluesselt, Bob entschluesselt
        let plaintext = b"Encrypted voice frame";
        let encrypted = aead::encrypt_frame_aead(session_a.aead_key(), plaintext).unwrap();
        let decrypted = aead::decrypt_frame_aead(session_b.aead_key(), &encrypted).unwrap();
        assert_eq!(decrypted, plaintext);

        // Bob verschluesselt, Alice entschluesselt
        let reply = b"Encrypted reply frame";
        let encrypted2 = aead::encrypt_frame_aead(session_b.aead_key(), reply).unwrap();
        let decrypted2 = aead::decrypt_frame_aead(session_a.aead_key(), &encrypted2).unwrap();
        assert_eq!(decrypted2, reply);
    }
}
