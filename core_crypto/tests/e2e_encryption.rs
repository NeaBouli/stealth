//! E2E Encryption Test
//!
//! Simuliert den kompletten Android Call-Flow:
//! 1. Beide Peers generieren X25519 Keypairs
//! 2. DH Key Exchange → Shared Secret
//! 3. HKDF → Session Key
//! 4. Encrypt/Decrypt mit XChaCha20-Poly1305

use securecall_core_crypto::aead::{self, AeadKey};
use securecall_core_crypto::identity::IdentityKeyPair;
use securecall_core_crypto::session;

/// Kompletter E2E-Flow: KeyGen → DH → HKDF → Encrypt → Decrypt
#[test]
fn e2e_full_call_flow() {
    // --- Phase 1: Key Generation (beide Peers) ---
    let alice = IdentityKeyPair::generate();
    let bob = IdentityKeyPair::generate();

    // Public Keys sind unterschiedlich
    assert_ne!(alice.public_key_bytes(), bob.public_key_bytes());

    // --- Phase 2: DH Key Exchange ---
    let alice_shared = alice.diffie_hellman(bob.public_key());
    let bob_shared = bob.diffie_hellman(alice.public_key());

    // Shared Secrets muessen identisch sein
    assert_eq!(alice_shared.as_bytes(), bob_shared.as_bytes());

    // --- Phase 3: Session Key Derivation (HKDF-SHA256) ---
    let alice_session = session::derive_key(
        alice_shared.as_bytes(),
        None,
        b"SecureCall-AEAD-Key-v1",
    );
    let bob_session = session::derive_key(
        bob_shared.as_bytes(),
        None,
        b"SecureCall-AEAD-Key-v1",
    );

    assert_eq!(alice_session, bob_session);
    assert_eq!(alice_session.len(), 32);

    // --- Phase 4: Encrypt (Alice → Bob) ---
    let key = AeadKey::from_bytes(alice_session);
    let audio_frame = b"PCM audio data 160 bytes simulated payload for voice";

    let encrypted = aead::encrypt_frame_aead(&key, audio_frame).unwrap();

    // Encrypted muss groesser sein (24B nonce + 16B tag)
    assert_eq!(encrypted.len(), audio_frame.len() + 24 + 16);
    // Encrypted darf nicht gleich plaintext sein
    assert_ne!(&encrypted[24..encrypted.len() - 16], &audio_frame[..]);

    // --- Phase 5: Decrypt (Bob empfaengt) ---
    let bob_key = AeadKey::from_bytes(bob_session);
    let decrypted = aead::decrypt_frame_aead(&bob_key, &encrypted).unwrap();

    assert_eq!(decrypted, audio_frame);
}

/// Tamper Detection: modifizierte Ciphertexte werden erkannt
#[test]
fn e2e_tamper_detection() {
    let alice = IdentityKeyPair::generate();
    let bob = IdentityKeyPair::generate();

    let shared = alice.diffie_hellman(bob.public_key());
    let session_key = session::derive_key(shared.as_bytes(), None, b"SecureCall-AEAD-Key-v1");

    let key = AeadKey::from_bytes(session_key);
    let plaintext = b"sensitive audio data";

    let mut encrypted = aead::encrypt_frame_aead(&key, plaintext).unwrap();

    // Flip ein Byte im Ciphertext
    let mid = encrypted.len() / 2;
    encrypted[mid] ^= 0xFF;

    // Decrypt muss fehlschlagen
    assert!(aead::decrypt_frame_aead(&key, &encrypted).is_err());
}

/// Falscher Key → Decrypt fehlschlaegt
#[test]
fn e2e_wrong_key_fails() {
    let alice = IdentityKeyPair::generate();
    let bob = IdentityKeyPair::generate();
    let eve = IdentityKeyPair::generate();

    // Alice und Bob teilen einen Key
    let ab_shared = alice.diffie_hellman(bob.public_key());
    let ab_key = session::derive_key(ab_shared.as_bytes(), None, b"SecureCall-AEAD-Key-v1");

    // Eve hat einen anderen Key mit Bob
    let eb_shared = eve.diffie_hellman(bob.public_key());
    let eve_key = session::derive_key(eb_shared.as_bytes(), None, b"SecureCall-AEAD-Key-v1");

    // Keys muessen unterschiedlich sein
    assert_ne!(ab_key, eve_key);

    // Alice verschluesselt
    let key = AeadKey::from_bytes(ab_key);
    let plaintext = b"top secret audio";
    let encrypted = aead::encrypt_frame_aead(&key, plaintext).unwrap();

    // Eve kann nicht entschluesseln
    let eve_aead = AeadKey::from_bytes(eve_key);
    assert!(aead::decrypt_frame_aead(&eve_aead, &encrypted).is_err());
}

/// Mehrere Frames hintereinander (simuliert Audio-Stream)
#[test]
fn e2e_multi_frame_stream() {
    let alice = IdentityKeyPair::generate();
    let bob = IdentityKeyPair::generate();

    let shared = alice.diffie_hellman(bob.public_key());
    let session_key = session::derive_key(shared.as_bytes(), None, b"SecureCall-AEAD-Key-v1");
    let key = AeadKey::from_bytes(session_key);

    // 100 Audio-Frames verschluesseln und entschluesseln
    for i in 0..100u32 {
        let frame = format!("audio-frame-{:04}", i);
        let encrypted = aead::encrypt_frame_aead(&key, frame.as_bytes()).unwrap();
        let decrypted = aead::decrypt_frame_aead(&key, &encrypted).unwrap();
        assert_eq!(decrypted, frame.as_bytes());
    }
}

/// Leerer Plaintext funktioniert
#[test]
fn e2e_empty_plaintext() {
    let alice = IdentityKeyPair::generate();
    let bob = IdentityKeyPair::generate();

    let shared = alice.diffie_hellman(bob.public_key());
    let session_key = session::derive_key(shared.as_bytes(), None, b"SecureCall-AEAD-Key-v1");
    let key = AeadKey::from_bytes(session_key);

    let encrypted = aead::encrypt_frame_aead(&key, b"").unwrap();
    // Nur nonce + tag
    assert_eq!(encrypted.len(), 24 + 16);

    let decrypted = aead::decrypt_frame_aead(&key, &encrypted).unwrap();
    assert!(decrypted.is_empty());
}

/// Self-Test der gesamten Library
#[test]
fn e2e_self_test() {
    assert!(securecall_core_crypto::self_test());
}
