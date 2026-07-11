//! FFI/JNI-Bridge
//!
//! Zwei Ebenen:
//! 1. Reine C-ABI Funktionen (core_crypto_*) fuer generische FFI-Konsumenten
//! 2. JNI-Funktionen (Java_com_securecall_crypto_CoreCrypto_*) fuer Android

use std::ptr;
use std::slice;

use zeroize::Zeroize;

use crate::aead::{self, AeadKey};
use crate::session;
use crate::utils;

// =========================================================
// 1. Reine C-ABI Funktionen
// =========================================================

/// Initialisiert die Crypto-Engine.
#[no_mangle]
pub extern "C" fn core_crypto_init() -> i32 {
    if crate::self_test() { 0 } else { -1 }
}

/// Verschluesselt einen Buffer via XChaCha20-Poly1305.
///
/// # Safety
///
/// `key_ptr` muss fuer 32 Bytes und `in_ptr` fuer `in_len` Bytes lesbar sein.
/// `out_len` muss auf einen les- und schreibbaren `usize` zeigen, dessen
/// Eingangswert die Kapazitaet des von `out_ptr` referenzierten, beschreibbaren
/// Puffers angibt. Eingabe- und Ausgabepuffer duerfen sich nicht ueberlappen.
/// Alle Zeiger muessen fuer die Dauer des Aufrufs gueltig und korrekt
/// ausgerichtet sein.
#[no_mangle]
pub unsafe extern "C" fn core_crypto_encrypt(
    key_ptr: *const u8,
    in_ptr: *const u8,
    in_len: usize,
    out_ptr: *mut u8,
    out_len: *mut usize,
) -> i32 {
    if key_ptr.is_null() || in_ptr.is_null() || out_ptr.is_null() || out_len.is_null() {
        return -1;
    }

    let mut key_buf = [0u8; 32];
    ptr::copy_nonoverlapping(key_ptr, key_buf.as_mut_ptr(), 32);
    let key = AeadKey::from_bytes(key_buf);
    key_buf.zeroize();

    let plaintext = slice::from_raw_parts(in_ptr, in_len);
    let capacity = *out_len;

    match aead::encrypt_frame_aead(&key, plaintext) {
        Ok(encrypted) => {
            if encrypted.len() > capacity {
                *out_len = encrypted.len();
                return -2; // buffer too small
            }
            ptr::copy_nonoverlapping(encrypted.as_ptr(), out_ptr, encrypted.len());
            *out_len = encrypted.len();
            0
        }
        Err(_) => -1,
    }
}

/// Entschluesselt einen Buffer via XChaCha20-Poly1305.
///
/// # Safety
///
/// `key_ptr` muss fuer 32 Bytes und `in_ptr` fuer `in_len` Bytes lesbar sein.
/// `out_len` muss auf einen les- und schreibbaren `usize` zeigen, dessen
/// Eingangswert die Kapazitaet des von `out_ptr` referenzierten, beschreibbaren
/// Puffers angibt. Eingabe- und Ausgabepuffer duerfen sich nicht ueberlappen.
/// Alle Zeiger muessen fuer die Dauer des Aufrufs gueltig und korrekt
/// ausgerichtet sein.
#[no_mangle]
pub unsafe extern "C" fn core_crypto_decrypt(
    key_ptr: *const u8,
    in_ptr: *const u8,
    in_len: usize,
    out_ptr: *mut u8,
    out_len: *mut usize,
) -> i32 {
    if key_ptr.is_null() || in_ptr.is_null() || out_ptr.is_null() || out_len.is_null() {
        return -1;
    }

    let mut key_buf = [0u8; 32];
    ptr::copy_nonoverlapping(key_ptr, key_buf.as_mut_ptr(), 32);
    let key = AeadKey::from_bytes(key_buf);
    key_buf.zeroize();

    let ciphertext = slice::from_raw_parts(in_ptr, in_len);
    let capacity = *out_len;

    match aead::decrypt_frame_aead(&key, ciphertext) {
        Ok(decrypted) => {
            if decrypted.len() > capacity {
                *out_len = decrypted.len();
                return -2; // buffer too small
            }
            ptr::copy_nonoverlapping(decrypted.as_ptr(), out_ptr, decrypted.len());
            *out_len = decrypted.len();
            0
        }
        Err(_) => -1,
    }
}

/// Ueberschreibt einen Buffer sicher mit Nullen.
///
/// # Safety
///
/// Wenn `ptr` nicht null ist, muss er auf einen fuer `len` Bytes gueltigen und
/// beschreibbaren Puffer zeigen. Der Puffer darf waehrend des Aufrufs nicht
/// anderweitig referenziert werden.
#[no_mangle]
pub unsafe extern "C" fn core_crypto_zeroize(ptr: *mut u8, len: usize) {
    if ptr.is_null() {
        return;
    }
    let buf = slice::from_raw_parts_mut(ptr, len);
    utils::secure_zeroize(buf);
}

// =========================================================
// 2. JNI-Funktionen fuer Android (com.securecall.crypto.CoreCrypto)
// =========================================================

use jni::JNIEnv;
use jni::objects::JClass;
use jni::objects::JByteArray;
use jni::sys::jboolean;

/// JNI: CoreCrypto.selfTest() -> boolean
#[no_mangle]
pub extern "system" fn Java_com_securecall_crypto_CoreCrypto_selfTest(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if crate::self_test() { 1 } else { 0 }
}

/// JNI: CoreCrypto.encrypt(byte[] key, byte[] data) -> byte[]
///
/// Verschluesselt `data` mit `key` (32 Byte) via XChaCha20-Poly1305.
/// Gibt [nonce (24B) | ciphertext | tag (16B)] zurueck.
#[no_mangle]
pub extern "system" fn Java_com_securecall_crypto_CoreCrypto_encrypt<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    key: JByteArray<'local>,
    data: JByteArray<'local>,
) -> JByteArray<'local> {
    let result = (|| -> Result<Vec<u8>, String> {
        let key_bytes = env.convert_byte_array(&key)
            .map_err(|e| format!("key read: {}", e))?;
        let data_bytes = env.convert_byte_array(&data)
            .map_err(|e| format!("data read: {}", e))?;

        if key_bytes.len() != 32 {
            return Err("key must be 32 bytes".into());
        }

        let mut key_arr = [0u8; 32];
        key_arr.copy_from_slice(&key_bytes);
        let aead_key = AeadKey::from_bytes(key_arr);
        key_arr.zeroize();

        aead::encrypt_frame_aead(&aead_key, &data_bytes)
            .map_err(|_| "encryption failed".into())
    })();

    match result {
        Ok(encrypted) => {
            env.byte_array_from_slice(&encrypted)
                .unwrap_or_else(|_| JByteArray::default())
        }
        Err(_) => JByteArray::default(),
    }
}

/// JNI: CoreCrypto.decrypt(byte[] key, byte[] data) -> byte[]
///
/// Entschluesselt `data` ([nonce | ciphertext | tag]) mit `key` (32 Byte).
/// Gibt den Plaintext zurueck, oder leeres Array bei Fehler.
#[no_mangle]
pub extern "system" fn Java_com_securecall_crypto_CoreCrypto_decrypt<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    key: JByteArray<'local>,
    data: JByteArray<'local>,
) -> JByteArray<'local> {
    let result = (|| -> Result<Vec<u8>, String> {
        let key_bytes = env.convert_byte_array(&key)
            .map_err(|e| format!("key read: {}", e))?;
        let data_bytes = env.convert_byte_array(&data)
            .map_err(|e| format!("data read: {}", e))?;

        if key_bytes.len() != 32 {
            return Err("key must be 32 bytes".into());
        }

        let mut key_arr = [0u8; 32];
        key_arr.copy_from_slice(&key_bytes);
        let aead_key = AeadKey::from_bytes(key_arr);
        key_arr.zeroize();

        aead::decrypt_frame_aead(&aead_key, &data_bytes)
            .map_err(|_| "decryption failed".into())
    })();

    match result {
        Ok(decrypted) => {
            env.byte_array_from_slice(&decrypted)
                .unwrap_or_else(|_| JByteArray::default())
        }
        Err(_) => JByteArray::default(),
    }
}

/// JNI: CoreCrypto.deriveSessionKey(byte[] localPriv, byte[] remotePub) -> byte[]
///
/// Fuehrt X25519 DH + HKDF-SHA256 durch und gibt einen 32-Byte Session-Key zurueck.
#[no_mangle]
pub extern "system" fn Java_com_securecall_crypto_CoreCrypto_deriveSessionKey<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    local_priv: JByteArray<'local>,
    remote_pub: JByteArray<'local>,
) -> JByteArray<'local> {
    let result = (|| -> Result<[u8; 32], String> {
        let priv_bytes = env.convert_byte_array(&local_priv)
            .map_err(|e| format!("priv read: {}", e))?;
        let pub_bytes = env.convert_byte_array(&remote_pub)
            .map_err(|e| format!("pub read: {}", e))?;

        if priv_bytes.len() != 32 || pub_bytes.len() != 32 {
            return Err("keys must be 32 bytes".into());
        }

        // X25519 DH
        let mut priv_arr = [0u8; 32];
        priv_arr.copy_from_slice(&priv_bytes);
        let secret = x25519_dalek::StaticSecret::from(priv_arr);
        priv_arr.zeroize();

        let mut pub_arr = [0u8; 32];
        pub_arr.copy_from_slice(&pub_bytes);
        let peer_public = x25519_dalek::PublicKey::from(pub_arr);
        pub_arr.zeroize();

        let shared = secret.diffie_hellman(&peer_public);

        // Low-order point check
        if shared.as_bytes().iter().all(|&b| b == 0) {
            return Err("low-order point detected — aborting DH".into());
        }

        // HKDF-SHA256 Key Derivation
        let derived = session::derive_key(
            shared.as_bytes(),
            None,
            b"SecureCall-AEAD-Key-v1",
        );

        Ok(derived)
    })();

    match result {
        Ok(key) => {
            env.byte_array_from_slice(&key)
                .unwrap_or_else(|_| JByteArray::default())
        }
        Err(_) => JByteArray::default(),
    }
}

/// JNI: CoreCrypto.generateKeyPair() -> byte[]
///
/// Erzeugt ein X25519-Schluesselpaar.
/// Gibt 64 Byte zurueck: [private (32B) | public (32B)].
#[no_mangle]
pub extern "system" fn Java_com_securecall_crypto_CoreCrypto_generateKeyPair<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JByteArray<'local> {
    let secret = x25519_dalek::StaticSecret::random_from_rng(rand_core::OsRng);
    let public = x25519_dalek::PublicKey::from(&secret);

    let mut combined = [0u8; 64];
    combined[..32].copy_from_slice(&secret.to_bytes());
    combined[32..].copy_from_slice(public.as_bytes());

    let result = env.byte_array_from_slice(&combined)
        .unwrap_or_else(|_| JByteArray::default());
    combined.zeroize();
    result
}
