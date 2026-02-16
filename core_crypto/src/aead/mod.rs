//! AEAD-Modul — XChaCha20-Poly1305
//!
//! Verschluesselt und authentifiziert Frames mit XChaCha20-Poly1305.
//! Nonce (24 Byte) wird zufaellig erzeugt und dem Ciphertext vorangestellt.
//!
//! Wire-Format: [nonce (24 bytes)] [ciphertext + auth tag (16 bytes)]

use chacha20poly1305::{
    aead::{Aead, AeadCore, KeyInit, OsRng},
    XChaCha20Poly1305, XNonce,
};
use zeroize::{Zeroize, ZeroizeOnDrop};

/// 256-Bit AEAD-Schluessel fuer XChaCha20-Poly1305.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct AeadKey {
    bytes: [u8; 32],
}

/// Groesse der Nonce (24 Byte fuer XChaCha20).
pub const NONCE_SIZE: usize = 24;

/// Groesse des Auth-Tags (16 Byte fuer Poly1305).
pub const TAG_SIZE: usize = 16;

impl AeadKey {
    /// Erzeugt einen AEAD-Key aus 32 Byte Schlüsselmaterial.
    pub fn from_bytes(bytes: [u8; 32]) -> Self {
        Self { bytes }
    }

    /// Gibt eine Referenz auf das rohe Key-Material zurueck.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.bytes
    }
}

/// Verschluesselt einen Plaintext-Frame.
///
/// Gibt `[nonce (24 B) | ciphertext | tag (16 B)]` zurueck.
/// Fehler bei interner Verschluesselungsfehler (sollte nicht passieren).
pub fn encrypt_frame_aead(key: &AeadKey, plaintext: &[u8]) -> Result<Vec<u8>, AeadError> {
    let cipher = XChaCha20Poly1305::new(key.bytes.as_ref().into());

    // Zufaellige Nonce erzeugen
    let nonce = XChaCha20Poly1305::generate_nonce(&mut OsRng);

    let ciphertext = cipher
        .encrypt(&nonce, plaintext)
        .map_err(|_| AeadError::EncryptionFailed)?;

    // Nonce + Ciphertext zusammenfuegen
    let mut output = Vec::with_capacity(NONCE_SIZE + ciphertext.len());
    output.extend_from_slice(&nonce);
    output.extend_from_slice(&ciphertext);

    Ok(output)
}

/// Entschluesselt einen Frame (erwartet `[nonce | ciphertext | tag]`).
///
/// Prueft Authentizitaet und gibt den Plaintext zurueck.
pub fn decrypt_frame_aead(key: &AeadKey, data: &[u8]) -> Result<Vec<u8>, AeadError> {
    if data.len() < NONCE_SIZE + TAG_SIZE {
        return Err(AeadError::DataTooShort);
    }

    let (nonce_bytes, ciphertext) = data.split_at(NONCE_SIZE);
    let nonce = XNonce::from_slice(nonce_bytes);

    let cipher = XChaCha20Poly1305::new(key.bytes.as_ref().into());

    cipher
        .decrypt(nonce, ciphertext)
        .map_err(|_| AeadError::DecryptionFailed)
}

/// AEAD-Fehlercodes.
#[derive(Debug, PartialEq)]
pub enum AeadError {
    EncryptionFailed,
    DecryptionFailed,
    DataTooShort,
    ReplayedNonce,
}

/// Replay-Schutz mit Sliding-Window-Ansatz (RFC 6479).
///
/// Verfolgt die letzten 64 Nonces ueber eine Bitmap und lehnt
/// bereits gesehene oder zu alte Nonces ab.
pub struct ReplayDetector {
    /// Hoechste bisher akzeptierte Nonce.
    highest: u64,
    /// Bitmap der letzten 64 Nonces relativ zu `highest`.
    bitmap: u64,
}

impl ReplayDetector {
    /// Erzeugt einen neuen ReplayDetector ohne bisherige Nonces.
    pub fn new() -> Self {
        Self {
            highest: 0,
            bitmap: 0,
        }
    }

    /// Prueft und registriert eine Nonce.
    ///
    /// Gibt `Ok(())` zurueck, wenn die Nonce neu ist, oder
    /// `Err(AeadError::ReplayedNonce)` bei einer Wiederholung
    /// bzw. einer Nonce, die ausserhalb des Fensters liegt.
    pub fn check_nonce(&mut self, nonce: u64) -> Result<(), AeadError> {
        const WINDOW_SIZE: u64 = 64;

        if nonce == 0 {
            return Err(AeadError::ReplayedNonce);
        }

        if nonce > self.highest {
            // Nonce liegt vor dem Fenster — Bitmap verschieben
            let diff = nonce - self.highest;
            if diff >= WINDOW_SIZE {
                self.bitmap = 0;
            } else {
                self.bitmap <<= diff;
            }
            self.bitmap |= 1;
            self.highest = nonce;
            Ok(())
        } else {
            // Nonce liegt innerhalb oder hinter dem Fenster
            let diff = self.highest - nonce;
            if diff >= WINDOW_SIZE {
                // Zu alt — ausserhalb des Fensters
                return Err(AeadError::ReplayedNonce);
            }
            let bit = 1u64 << diff;
            if self.bitmap & bit != 0 {
                // Bereits gesehen
                return Err(AeadError::ReplayedNonce);
            }
            self.bitmap |= bit;
            Ok(())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_key() -> AeadKey {
        AeadKey::from_bytes([0x42u8; 32])
    }

    #[test]
    fn encrypt_decrypt_roundtrip() {
        let key = test_key();
        let plaintext = b"Hello SecureCall!";

        let encrypted = encrypt_frame_aead(&key, plaintext).unwrap();
        assert_ne!(&encrypted[NONCE_SIZE..], plaintext);

        let decrypted = decrypt_frame_aead(&key, &encrypted).unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn encrypt_produces_nonce_prefix() {
        let key = test_key();
        let encrypted = encrypt_frame_aead(&key, b"test").unwrap();

        // nonce (24) + ciphertext (4) + tag (16) = 44
        assert_eq!(encrypted.len(), NONCE_SIZE + 4 + TAG_SIZE);
    }

    #[test]
    fn different_encryptions_produce_different_nonces() {
        let key = test_key();
        let e1 = encrypt_frame_aead(&key, b"same").unwrap();
        let e2 = encrypt_frame_aead(&key, b"same").unwrap();

        // Nonces muessen unterschiedlich sein
        assert_ne!(&e1[..NONCE_SIZE], &e2[..NONCE_SIZE]);
    }

    #[test]
    fn tampered_ciphertext_fails() {
        let key = test_key();
        let mut encrypted = encrypt_frame_aead(&key, b"secret").unwrap();

        // Ein Byte im Ciphertext aendern
        let last = encrypted.len() - 1;
        encrypted[last] ^= 0xFF;

        let result = decrypt_frame_aead(&key, &encrypted);
        assert_eq!(result, Err(AeadError::DecryptionFailed));
    }

    #[test]
    fn wrong_key_fails() {
        let key1 = AeadKey::from_bytes([0x01u8; 32]);
        let key2 = AeadKey::from_bytes([0x02u8; 32]);

        let encrypted = encrypt_frame_aead(&key1, b"secret").unwrap();
        let result = decrypt_frame_aead(&key2, &encrypted);
        assert_eq!(result, Err(AeadError::DecryptionFailed));
    }

    #[test]
    fn data_too_short() {
        let key = test_key();
        let result = decrypt_frame_aead(&key, &[0u8; 10]);
        assert_eq!(result, Err(AeadError::DataTooShort));
    }

    #[test]
    fn empty_plaintext() {
        let key = test_key();
        let encrypted = encrypt_frame_aead(&key, b"").unwrap();
        let decrypted = decrypt_frame_aead(&key, &encrypted).unwrap();
        assert_eq!(decrypted, b"");
    }

    #[test]
    fn large_payload() {
        let key = test_key();
        let plaintext = vec![0xABu8; 8192]; // 8 KB audio frame

        let encrypted = encrypt_frame_aead(&key, &plaintext).unwrap();
        let decrypted = decrypt_frame_aead(&key, &encrypted).unwrap();
        assert_eq!(decrypted, plaintext);
    }
}
