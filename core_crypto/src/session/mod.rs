//! Session-Modul — HKDF Key Derivation + Session-Verwaltung
//!
//! Leitet aus einem Diffie-Hellman Shared Secret sichere
//! Session-Keys ab (HKDF-SHA256).

use hkdf::Hkdf;
use sha2::Sha256;
use zeroize::ZeroizeOnDrop;

use crate::aead::AeadKey;

/// HKDF-Info-String fuer die AEAD-Key-Ableitung.
const HKDF_INFO_AEAD: &[u8] = b"SecureCall-AEAD-Key-v1";

/// Session-State: enthaelt den abgeleiteten AEAD-Key.
#[derive(ZeroizeOnDrop)]
pub struct SessionState {
    /// Der abgeleitete 256-Bit AEAD-Schluessel.
    #[zeroize(skip)]
    aead_key: AeadKey,
    /// Session aktiv?
    active: bool,
}

impl SessionState {
    /// Erzeugt eine neue Session aus einem DH Shared Secret.
    ///
    /// Nutzt HKDF-SHA256, um einen 32-Byte AEAD-Key abzuleiten.
    /// Optional: ein Salt (z.B. Session-ID oder Nonce).
    pub fn from_shared_secret(shared_secret: &[u8; 32], salt: Option<&[u8]>) -> Self {
        let aead_key_bytes = derive_key(shared_secret, salt, HKDF_INFO_AEAD);
        Self {
            aead_key: AeadKey::from_bytes(aead_key_bytes),
            active: true,
        }
    }

    /// Gibt eine Referenz auf den Session AEAD-Key zurueck.
    pub fn aead_key(&self) -> &AeadKey {
        &self.aead_key
    }

    /// Ist die Session aktiv?
    pub fn is_active(&self) -> bool {
        self.active
    }

    /// Beendet die Session (Key wird bei Drop gezeroed).
    pub fn close(&mut self) {
        self.active = false;
    }
}

/// Leitet einen 32-Byte Key aus Input-Key-Material via HKDF-SHA256 ab.
pub fn derive_key(ikm: &[u8], salt: Option<&[u8]>, info: &[u8]) -> [u8; 32] {
    let hk = Hkdf::<Sha256>::new(salt, ikm);
    let mut okm = [0u8; 32];
    hk.expand(info, &mut okm)
        .expect("HKDF expand failed (output length valid)");
    okm
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn derive_key_deterministic() {
        let ikm = [0x42u8; 32];
        let k1 = derive_key(&ikm, None, b"test-info");
        let k2 = derive_key(&ikm, None, b"test-info");
        assert_eq!(k1, k2);
    }

    #[test]
    fn different_info_different_keys() {
        let ikm = [0x42u8; 32];
        let k1 = derive_key(&ikm, None, b"info-a");
        let k2 = derive_key(&ikm, None, b"info-b");
        assert_ne!(k1, k2);
    }

    #[test]
    fn different_salt_different_keys() {
        let ikm = [0x42u8; 32];
        let k1 = derive_key(&ikm, Some(b"salt-1"), b"info");
        let k2 = derive_key(&ikm, Some(b"salt-2"), b"info");
        assert_ne!(k1, k2);
    }

    #[test]
    fn derived_key_not_zero() {
        let ikm = [0x42u8; 32];
        let key = derive_key(&ikm, None, b"info");
        assert_ne!(key, [0u8; 32]);
    }

    #[test]
    fn session_from_shared_secret() {
        let shared = [0xABu8; 32];
        let session = SessionState::from_shared_secret(&shared, None);

        assert!(session.is_active());
        assert_ne!(session.aead_key().as_bytes(), &[0u8; 32]);
    }

    #[test]
    fn session_close() {
        let shared = [0xABu8; 32];
        let mut session = SessionState::from_shared_secret(&shared, None);

        assert!(session.is_active());
        session.close();
        assert!(!session.is_active());
    }

    #[test]
    fn full_dh_to_session_roundtrip() {
        use crate::identity::IdentityKeyPair;
        use crate::aead::{encrypt_frame_aead, decrypt_frame_aead};

        // Key Exchange
        let alice = IdentityKeyPair::generate();
        let bob = IdentityKeyPair::generate();

        let secret_a = alice.diffie_hellman(bob.public_key());
        let secret_b = bob.diffie_hellman(alice.public_key());

        // Session ableiten
        let session_a = SessionState::from_shared_secret(secret_a.as_bytes(), None);
        let session_b = SessionState::from_shared_secret(secret_b.as_bytes(), None);

        // Verschluesseln und Entschluesseln
        let plaintext = b"SecureCall audio frame";
        let encrypted = encrypt_frame_aead(session_a.aead_key(), plaintext).unwrap();
        let decrypted = decrypt_frame_aead(session_b.aead_key(), &encrypted).unwrap();

        assert_eq!(decrypted, plaintext);
    }
}
