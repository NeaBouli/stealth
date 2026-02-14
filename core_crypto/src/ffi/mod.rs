//! FFI/JNI-Bridge
//!
//! Extern-"C"-Funktionen fuer Android (JNI) und andere FFI-Konsumenten.
//! Alle Funktionen sind `no_mangle` und `unsafe`-frei wo moeglich.

use std::ptr;
use std::slice;

use crate::aead::{self, AeadKey};
use crate::utils;

/// Initialisiert die Crypto-Engine. Aktuell ein No-Op,
/// spaeter fuer Hardwareinitialisierung oder Self-Tests.
#[no_mangle]
pub extern "C" fn core_crypto_init() -> i32 {
    if crate::self_test() { 0 } else { -1 }
}

/// Verschluesselt einen Buffer via XChaCha20-Poly1305.
///
/// # Safety
/// - `key_ptr` muss auf 32 Byte zeigen.
/// - `in_ptr` muss auf `in_len` Byte zeigen.
/// - `out_ptr` muss auf min. `in_len + 40` Byte zeigen (24 Nonce + 16 Tag).
/// - `out_len` wird mit der tatsaechlichen Ausgabelaenge beschrieben.
///
/// Return: 0 bei Erfolg, -1 bei Fehler.
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

    let key_bytes: [u8; 32] = {
        let mut buf = [0u8; 32];
        ptr::copy_nonoverlapping(key_ptr, buf.as_mut_ptr(), 32);
        buf
    };
    let key = AeadKey::from_bytes(key_bytes);
    let plaintext = slice::from_raw_parts(in_ptr, in_len);

    match aead::encrypt_frame_aead(&key, plaintext) {
        Ok(encrypted) => {
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
/// - `key_ptr` muss auf 32 Byte zeigen.
/// - `in_ptr` muss auf `in_len` Byte zeigen (Nonce + Ciphertext + Tag).
/// - `out_ptr` muss auf min. `in_len - 40` Byte zeigen.
/// - `out_len` wird mit der tatsaechlichen Ausgabelaenge beschrieben.
///
/// Return: 0 bei Erfolg, -1 bei Fehler (z.B. Authentifizierung fehlgeschlagen).
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

    let key_bytes: [u8; 32] = {
        let mut buf = [0u8; 32];
        ptr::copy_nonoverlapping(key_ptr, buf.as_mut_ptr(), 32);
        buf
    };
    let key = AeadKey::from_bytes(key_bytes);
    let ciphertext = slice::from_raw_parts(in_ptr, in_len);

    match aead::decrypt_frame_aead(&key, ciphertext) {
        Ok(decrypted) => {
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
/// - `ptr` muss auf `len` Byte zeigen.
#[no_mangle]
pub unsafe extern "C" fn core_crypto_zeroize(ptr: *mut u8, len: usize) {
    if ptr.is_null() {
        return;
    }
    let buf = slice::from_raw_parts_mut(ptr, len);
    utils::secure_zeroize(buf);
}
