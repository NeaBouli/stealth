//! AEAD-Modul — XChaCha20-Poly1305
//!
//! Verschluesselt und authentifiziert Frames mit XChaCha20-Poly1305.
//! Nonce (24 Byte) wird zufaellig erzeugt und dem Ciphertext vorangestellt.
//!
//! Wire-Format: [nonce (24 bytes)] [ciphertext + auth tag (16 bytes)]

use chacha20poly1305::{
    aead::{Aead, AeadCore, KeyInit, OsRng, Payload},
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
#[derive(Clone, Debug, PartialEq)]
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

    /// Restores a detector from a previously committed `(highest, bitmap)` state.
    ///
    /// Used by the STX-24 media receiver, which keeps the committed state on the
    /// caller side and only replaces it after a frame authenticated successfully.
    pub fn from_state(highest: u64, bitmap: u64) -> Self {
        Self { highest, bitmap }
    }

    /// Returns the `(highest, bitmap)` state for committing after authentication.
    pub fn state(&self) -> (u64, u64) {
        (self.highest, self.bitmap)
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

impl Default for ReplayDetector {
    fn default() -> Self {
        Self::new()
    }
}

// =========================================================
// STX-24: authenticated media frame v3
// =========================================================
//
// Wire format (all multi-byte integers big-endian):
//
//   [0]      version            = 3
//   [1]      frame type         = 1 (audio)
//   [2]      sender role        = 1 caller | 2 callee
//   [3]      reserved           = 0
//   [4..12]  sequence (u64)     1..=MEDIA_MAX_SEQUENCE, strictly increasing per sender
//   [12..44] call context digest (32 bytes, from the signed invite/accept transcripts)
//   [44..]   XChaCha20-Poly1305 ciphertext || tag (16 bytes)
//
// The 44-byte header is the AEAD associated data. Each direction uses its own
// HKDF-derived key, and the nonce is derived from (role, sequence), so a
// sequence never repeats under the same key while the sender counter is kept
// for the lifetime of the call key.

/// Authenticated media frame version.
pub const MEDIA_FRAME_VERSION: u8 = 3;
/// Audio frame type.
pub const MEDIA_TYPE_AUDIO: u8 = 1;
/// Sender role byte of the caller.
pub const MEDIA_ROLE_CALLER: u8 = 1;
/// Sender role byte of the callee.
pub const MEDIA_ROLE_CALLEE: u8 = 2;
/// Size of the call context digest.
pub const MEDIA_CONTEXT_SIZE: usize = 32;
/// Size of the authenticated header.
pub const MEDIA_HEADER_SIZE: usize = 4 + 8 + MEDIA_CONTEXT_SIZE;
/// Largest accepted plaintext. Matches the Android encoder output buffer
/// (`client_android/app/src/main/cpp/native-lib.cpp`, `MAX_PACKET = 4000`).
pub const MEDIA_MAX_PLAINTEXT: usize = 4000;
/// Smallest accepted frame (header + one plaintext byte + tag).
pub const MEDIA_MIN_FRAME: usize = MEDIA_HEADER_SIZE + 1 + TAG_SIZE;
/// Largest accepted frame.
pub const MEDIA_MAX_FRAME: usize = MEDIA_HEADER_SIZE + MEDIA_MAX_PLAINTEXT + TAG_SIZE;
/// Highest usable sequence (kept within the signed 64-bit range of the JVM caller).
pub const MEDIA_MAX_SEQUENCE: u64 = i64::MAX as u64;

/// Media frame errors. No variant carries key or plaintext material.
#[derive(Debug, PartialEq)]
pub enum MediaFrameError {
    /// Length, reserved byte or sequence encoding is invalid.
    Malformed,
    /// Version byte is not [`MEDIA_FRAME_VERSION`].
    UnsupportedVersion,
    /// Frame type is not [`MEDIA_TYPE_AUDIO`].
    UnsupportedType,
    /// Sender role is unknown or not the expected peer role (reflection).
    WrongRole,
    /// Call context digest differs from the current call.
    WrongContext,
    /// Sequence is outside `1..=MEDIA_MAX_SEQUENCE`.
    SequenceExhausted,
    /// Sequence was already accepted or is older than the replay window.
    Replayed,
    /// AEAD tag verification failed (wrong key, header or ciphertext).
    AuthenticationFailed,
    /// Encryption failed internally.
    EncryptionFailed,
}

fn valid_media_role(role: u8) -> bool {
    role == MEDIA_ROLE_CALLER || role == MEDIA_ROLE_CALLEE
}

fn media_header(role: u8, context: &[u8; MEDIA_CONTEXT_SIZE], sequence: u64) -> [u8; MEDIA_HEADER_SIZE] {
    let mut header = [0u8; MEDIA_HEADER_SIZE];
    header[0] = MEDIA_FRAME_VERSION;
    header[1] = MEDIA_TYPE_AUDIO;
    header[2] = role;
    header[3] = 0;
    header[4..12].copy_from_slice(&sequence.to_be_bytes());
    header[12..].copy_from_slice(context);
    header
}

fn media_nonce(role: u8, sequence: u64) -> XNonce {
    let mut nonce = [0u8; NONCE_SIZE];
    nonce[0] = role;
    nonce[16..].copy_from_slice(&sequence.to_be_bytes());
    XNonce::from(nonce)
}

fn digest_equal(a: &[u8], b: &[u8]) -> bool {
    a.len() == b.len() && a.iter().zip(b).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

/// Seals one audio payload as an authenticated v3 media frame.
///
/// `key` must be the sender's own direction key and `sequence` must be fresh for that key.
pub fn seal_media_frame(
    key: &AeadKey,
    sender_role: u8,
    context: &[u8; MEDIA_CONTEXT_SIZE],
    sequence: u64,
    plaintext: &[u8],
) -> Result<Vec<u8>, MediaFrameError> {
    if !valid_media_role(sender_role) {
        return Err(MediaFrameError::WrongRole);
    }
    if sequence == 0 || sequence > MEDIA_MAX_SEQUENCE {
        return Err(MediaFrameError::SequenceExhausted);
    }
    if plaintext.is_empty() || plaintext.len() > MEDIA_MAX_PLAINTEXT {
        return Err(MediaFrameError::Malformed);
    }
    let header = media_header(sender_role, context, sequence);
    let cipher = XChaCha20Poly1305::new(key.bytes.as_ref().into());
    let ciphertext = cipher
        .encrypt(&media_nonce(sender_role, sequence), Payload { msg: plaintext, aad: &header })
        .map_err(|_| MediaFrameError::EncryptionFailed)?;
    let mut frame = Vec::with_capacity(MEDIA_HEADER_SIZE + ciphertext.len());
    frame.extend_from_slice(&header);
    frame.extend_from_slice(&ciphertext);
    Ok(frame)
}

/// Opens an authenticated v3 media frame from the expected peer role.
///
/// All length/version/type/role/context/sequence checks run before any
/// allocation or decryption. The replay check runs on a copy of `replay`;
/// the returned detector state may be committed by the caller only because
/// the frame authenticated. A rejected frame never changes the caller's state.
pub fn open_media_frame(
    key: &AeadKey,
    expected_peer_role: u8,
    context: &[u8; MEDIA_CONTEXT_SIZE],
    replay: &ReplayDetector,
    frame: &[u8],
) -> Result<(Vec<u8>, ReplayDetector), MediaFrameError> {
    if frame.len() < MEDIA_MIN_FRAME || frame.len() > MEDIA_MAX_FRAME {
        return Err(MediaFrameError::Malformed);
    }
    if frame[0] != MEDIA_FRAME_VERSION {
        return Err(MediaFrameError::UnsupportedVersion);
    }
    if frame[1] != MEDIA_TYPE_AUDIO {
        return Err(MediaFrameError::UnsupportedType);
    }
    if !valid_media_role(expected_peer_role) || !valid_media_role(frame[2]) || frame[2] != expected_peer_role {
        return Err(MediaFrameError::WrongRole);
    }
    if frame[3] != 0 {
        return Err(MediaFrameError::Malformed);
    }
    let mut sequence_bytes = [0u8; 8];
    sequence_bytes.copy_from_slice(&frame[4..12]);
    let sequence = u64::from_be_bytes(sequence_bytes);
    if sequence == 0 || sequence > MEDIA_MAX_SEQUENCE {
        return Err(MediaFrameError::SequenceExhausted);
    }
    if !digest_equal(&frame[12..MEDIA_HEADER_SIZE], context) {
        return Err(MediaFrameError::WrongContext);
    }
    let mut next = replay.clone();
    next.check_nonce(sequence).map_err(|_| MediaFrameError::Replayed)?;

    let (header, ciphertext) = frame.split_at(MEDIA_HEADER_SIZE);
    let cipher = XChaCha20Poly1305::new(key.bytes.as_ref().into());
    let plaintext = cipher
        .decrypt(&media_nonce(expected_peer_role, sequence), Payload { msg: ciphertext, aad: header })
        .map_err(|_| MediaFrameError::AuthenticationFailed)?;
    Ok((plaintext, next))
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

    // ---------------- STX-24 media frame v3 ----------------

    const CTX: [u8; MEDIA_CONTEXT_SIZE] = [0x5Au8; MEDIA_CONTEXT_SIZE];

    fn caller_to_callee() -> AeadKey { AeadKey::from_bytes([0x11u8; 32]) }
    fn callee_to_caller() -> AeadKey { AeadKey::from_bytes([0x22u8; 32]) }

    fn seal_caller(seq: u64, payload: &[u8]) -> Vec<u8> {
        seal_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, seq, payload).unwrap()
    }

    fn open_at_callee(state: &ReplayDetector, frame: &[u8]) -> Result<(Vec<u8>, ReplayDetector), MediaFrameError> {
        open_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, state, frame)
    }

    #[test]
    fn media_v3_two_direction_roundtrip() {
        let a = seal_caller(1, b"opus-a");
        let (plain, state) = open_at_callee(&ReplayDetector::new(), &a).unwrap();
        assert_eq!(plain, b"opus-a");
        assert_eq!(state.state().0, 1);

        let b = seal_media_frame(&callee_to_caller(), MEDIA_ROLE_CALLEE, &CTX, 1, b"opus-b").unwrap();
        let (plain_b, _) = open_media_frame(&callee_to_caller(), MEDIA_ROLE_CALLEE, &CTX, &ReplayDetector::new(), &b).unwrap();
        assert_eq!(plain_b, b"opus-b");
        // Same sequence in both directions never shares key or nonce.
        assert_ne!(&a[MEDIA_HEADER_SIZE..], &b[MEDIA_HEADER_SIZE..]);
        assert_eq!(a.len(), MEDIA_HEADER_SIZE + 6 + TAG_SIZE);
    }

    #[test]
    fn media_v3_rejects_reflection_and_unknown_role() {
        // A callee receiving its own outbound frame back.
        let own = seal_media_frame(&callee_to_caller(), MEDIA_ROLE_CALLEE, &CTX, 1, b"x").unwrap();
        assert_eq!(open_media_frame(&callee_to_caller(), MEDIA_ROLE_CALLER, &CTX, &ReplayDetector::new(), &own),
            Err(MediaFrameError::WrongRole));
        // Role byte rewritten to the expected role: header AAD + nonce no longer match.
        let mut rewritten = own.clone();
        rewritten[2] = MEDIA_ROLE_CALLER;
        assert_eq!(open_media_frame(&callee_to_caller(), MEDIA_ROLE_CALLER, &CTX, &ReplayDetector::new(), &rewritten),
            Err(MediaFrameError::AuthenticationFailed));
        let mut unknown = seal_caller(1, b"x");
        unknown[2] = 3;
        assert_eq!(open_at_callee(&ReplayDetector::new(), &unknown), Err(MediaFrameError::WrongRole));
        assert_eq!(seal_media_frame(&caller_to_callee(), 0, &CTX, 1, b"x"), Err(MediaFrameError::WrongRole));
    }

    #[test]
    fn media_v3_rejects_duplicate_old_and_accepts_in_window_reorder() {
        let mut state = ReplayDetector::new();
        for seq in [2u64, 1, 3] {
            state = open_at_callee(&state, &seal_caller(seq, b"p")).unwrap().1;
        }
        assert_eq!(open_at_callee(&state, &seal_caller(2, b"p")), Err(MediaFrameError::Replayed));
        let mut far = state.clone();
        far = open_at_callee(&far, &seal_caller(200, b"p")).unwrap().1;
        assert_eq!(open_at_callee(&far, &seal_caller(3, b"p")), Err(MediaFrameError::Replayed));
        assert!(open_at_callee(&far, &seal_caller(190, b"p")).is_ok());
    }

    #[test]
    fn media_v3_forged_high_sequence_cannot_poison_window() {
        let state = open_at_callee(&ReplayDetector::new(), &seal_caller(1, b"p")).unwrap().1;
        let mut forged = seal_caller(5, b"p");
        forged[4..12].copy_from_slice(&(MEDIA_MAX_SEQUENCE).to_be_bytes());
        assert_eq!(open_at_callee(&state, &forged), Err(MediaFrameError::AuthenticationFailed));
        let forged_key = seal_media_frame(&AeadKey::from_bytes([0x99u8; 32]), MEDIA_ROLE_CALLER, &CTX, 1_000_000, b"p").unwrap();
        assert_eq!(open_at_callee(&state, &forged_key), Err(MediaFrameError::AuthenticationFailed));
        // The caller-held state is unchanged; legitimate next frames still open.
        assert_eq!(state.state(), (1, 1));
        assert!(open_at_callee(&state, &seal_caller(2, b"p")).is_ok());
    }

    #[test]
    fn media_v3_rejects_wrong_version_type_context_key_and_tag() {
        let good = seal_caller(1, b"payload");
        let fresh = ReplayDetector::new();
        let mut v = good.clone(); v[0] = 2;
        assert_eq!(open_at_callee(&fresh, &v), Err(MediaFrameError::UnsupportedVersion));
        let mut t = good.clone(); t[1] = 2;
        assert_eq!(open_at_callee(&fresh, &t), Err(MediaFrameError::UnsupportedType));
        let mut r = good.clone(); r[3] = 1;
        assert_eq!(open_at_callee(&fresh, &r), Err(MediaFrameError::Malformed));
        let other_ctx = [0x5Bu8; MEDIA_CONTEXT_SIZE];
        assert_eq!(open_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &other_ctx, &fresh, &good),
            Err(MediaFrameError::WrongContext));
        let mut c = good.clone(); c[20] ^= 1;
        assert_eq!(open_at_callee(&fresh, &c), Err(MediaFrameError::WrongContext));
        assert_eq!(open_media_frame(&callee_to_caller(), MEDIA_ROLE_CALLER, &CTX, &fresh, &good),
            Err(MediaFrameError::AuthenticationFailed));
        let mut tag = good.clone(); let last = tag.len() - 1; tag[last] ^= 0x80;
        assert_eq!(open_at_callee(&fresh, &tag), Err(MediaFrameError::AuthenticationFailed));
        // The legacy v2 envelope (random nonce || ct) is never accepted as media.
        let legacy = encrypt_frame_aead(&caller_to_callee(), b"payload").unwrap();
        assert!(open_at_callee(&fresh, &legacy).is_err());
    }

    #[test]
    fn media_v3_rejects_truncation_oversize_and_sequence_overflow() {
        let good = seal_caller(1, b"p");
        let fresh = ReplayDetector::new();
        assert_eq!(open_at_callee(&fresh, &good[..MEDIA_MIN_FRAME - 1]), Err(MediaFrameError::Malformed));
        assert_eq!(open_at_callee(&fresh, &[]), Err(MediaFrameError::Malformed));
        assert_eq!(open_at_callee(&fresh, &vec![MEDIA_FRAME_VERSION; MEDIA_MAX_FRAME + 1]), Err(MediaFrameError::Malformed));
        assert!(seal_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, 1, &vec![1u8; MEDIA_MAX_PLAINTEXT]).is_ok());
        assert_eq!(seal_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, 1, &vec![1u8; MEDIA_MAX_PLAINTEXT + 1]),
            Err(MediaFrameError::Malformed));
        assert_eq!(seal_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, 1, b""), Err(MediaFrameError::Malformed));
        assert_eq!(seal_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, 0, b"p"), Err(MediaFrameError::SequenceExhausted));
        assert_eq!(seal_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, MEDIA_MAX_SEQUENCE + 1, b"p"),
            Err(MediaFrameError::SequenceExhausted));
        assert!(seal_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, MEDIA_MAX_SEQUENCE, b"p").is_ok());
        let mut zero = good.clone(); zero[4..12].copy_from_slice(&0u64.to_be_bytes());
        assert_eq!(open_at_callee(&fresh, &zero), Err(MediaFrameError::SequenceExhausted));
        let mut over = good.clone(); over[4..12].copy_from_slice(&u64::MAX.to_be_bytes());
        assert_eq!(open_at_callee(&fresh, &over), Err(MediaFrameError::SequenceExhausted));
    }

    /// Mirrors the JNI path: state leaves as u64 big-endian, is held by Kotlin as a
    /// signed Long and returns as `jlong as u64`. Bit 63 (sign) must survive unchanged.
    fn jni_roundtrip(state: &ReplayDetector) -> ReplayDetector {
        let (h, b) = state.state();
        let (jh, jb) = (i64::from_be_bytes(h.to_be_bytes()), i64::from_be_bytes(b.to_be_bytes()));
        ReplayDetector::from_state(jh as u64, jb as u64)
    }

    #[test]
    fn media_v3_replay_bitmap_bit63_roundtrip() {
        let mut state = ReplayDetector::new();
        state = jni_roundtrip(&open_at_callee(&state, &seal_caller(1, b"p")).unwrap().1);
        state = jni_roundtrip(&open_at_callee(&state, &seal_caller(64, b"p")).unwrap().1);
        let (_, bitmap) = state.state();
        assert!((bitmap as i64) < 0, "bit 63 marks sequence 1 and is the JVM sign bit");
        assert_eq!(open_at_callee(&state, &seal_caller(1, b"p")), Err(MediaFrameError::Replayed));
        state = jni_roundtrip(&open_at_callee(&state, &seal_caller(2, b"p")).unwrap().1);
        // Sign transition: one more step shifts bit 63 out of the window.
        state = jni_roundtrip(&open_at_callee(&state, &seal_caller(65, b"p")).unwrap().1);
        assert_eq!(open_at_callee(&state, &seal_caller(1, b"p")), Err(MediaFrameError::Replayed));
        assert_eq!(open_at_callee(&state, &seal_caller(2, b"p")), Err(MediaFrameError::Replayed));
        assert!(open_at_callee(&state, &seal_caller(3, b"p")).is_ok());
    }

    #[test]
    fn media_v3_encoder_max_packet_boundary() {
        // native-lib.cpp MAX_PACKET = 4000 is the largest encoder output.
        assert_eq!(MEDIA_MAX_PLAINTEXT, 4000);
        let max = seal_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, 1, &vec![7u8; 4000]).unwrap();
        assert_eq!(max.len(), MEDIA_MAX_FRAME);
        assert_eq!(open_at_callee(&ReplayDetector::new(), &max).unwrap().0.len(), 4000);
        assert_eq!(seal_media_frame(&caller_to_callee(), MEDIA_ROLE_CALLER, &CTX, 2, &vec![7u8; 4001]),
            Err(MediaFrameError::Malformed));
        let mut over = max.clone();
        over.push(0);
        assert_eq!(open_at_callee(&ReplayDetector::new(), &over), Err(MediaFrameError::Malformed));
    }
}
