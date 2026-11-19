//! FFI/JNI-Bridge
//!
//! Zwei Ebenen:
//! 1. Reine C-ABI Funktionen (core_crypto_*) fuer generische FFI-Konsumenten
//! 2. JNI-Funktionen (Java_com_securecall_crypto_CoreCrypto_*) fuer Android
//!
//! # Security notes (STX-25 / STX-27)
//!
//! - Key-material zeroization: every Rust-side intermediate buffer holding
//!   private or session key bytes is zeroized — explicitly via [`Zeroize`],
//!   or on drop via [`Zeroizing`], the `ZeroizeOnDrop` impls of `AeadKey` /
//!   `SessionKey`, and x25519-dalek's `zeroize` feature (`StaticSecret` and
//!   `SharedSecret`). Copies that the JVM/GC makes of Java `byte[]` arguments
//!   are outside Rust's reach and are not scrubbed by this layer.
//! - Drop-based identity zeroization (current behaviour): `IdentityKeyPair`
//!   overwrites its X25519 secret with zeros on `Drop` (see the `identity`
//!   module). This is best-effort scrubbing of freed memory, not a guarantee
//!   about transient copies made by the compiler or the JVM.
//! - No AAD parameter on the generic API: `encrypt_frame_aead` /
//!   `decrypt_frame_aead` authenticate the frame only
//!   (`[nonce | ciphertext | tag]`) and stay unchanged for unrelated callers.
//!   Call media uses the narrow STX-24 `mediaSeal` / `mediaOpen` JNI functions,
//!   which bind version, role, sequence and call context as AAD.
//! - Panic containment (STX-27): every exported function runs through
//!   [`catch_ffi`] (`catch_unwind`). A panic returns the function's existing
//!   error value (`-1` / null array / `false`) instead of unwinding across
//!   the C ABI or into the JVM. The default panic hook still reports.

use std::panic::{catch_unwind, AssertUnwindSafe, UnwindSafe};
use std::ptr;
use std::slice;

use zeroize::{Zeroize, Zeroizing};

use crate::aead::{self, AeadKey};
use crate::session;
use crate::utils;

/// Runs `f` at the FFI boundary and returns its result.
///
/// If `f` panics, the panic is caught and `fallback` — the function's
/// existing error value — is returned, so unwinding never crosses the C ABI
/// or the JNI boundary.
fn catch_ffi<F, T>(f: F, fallback: T) -> T
where
    F: FnOnce() -> T + UnwindSafe,
{
    match catch_unwind(f) {
        Ok(value) => value,
        Err(_) => fallback,
    }
}

// =========================================================
// 1. Reine C-ABI Funktionen
// =========================================================

/// Initialisiert die Crypto-Engine.
#[no_mangle]
pub extern "C" fn core_crypto_init() -> i32 {
    catch_ffi(AssertUnwindSafe(|| if crate::self_test() { 0 } else { -1 }), -1)
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
    catch_ffi(AssertUnwindSafe(|| unsafe {
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
    }), -1)
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
    catch_ffi(AssertUnwindSafe(|| unsafe {
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
    }), -1)
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
    catch_ffi(AssertUnwindSafe(|| unsafe {
        if ptr.is_null() {
            return;
        }
        let buf = slice::from_raw_parts_mut(ptr, len);
        utils::secure_zeroize(buf);
    }), ())
}

// =========================================================
// 2. JNI-Funktionen fuer Android (com.securecall.crypto.CoreCrypto)
// =========================================================

use jni::JNIEnv;
use jni::objects::JClass;
use jni::objects::JByteArray;
use jni::sys::{jboolean, jint, jlong};

/// JNI: CoreCrypto.selfTest() -> boolean
#[no_mangle]
pub extern "system" fn Java_com_securecall_crypto_CoreCrypto_selfTest(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    catch_ffi(AssertUnwindSafe(|| if crate::self_test() { 1 } else { 0 }), 0)
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
    catch_ffi(AssertUnwindSafe(|| {
        let result = (|| -> Result<Vec<u8>, String> {
            // Zeroizing: the Vec holding the raw key bytes is scrubbed on drop.
            let key_bytes = Zeroizing::new(env.convert_byte_array(&key)
                .map_err(|e| format!("key read: {}", e))?);
            let data_bytes = env.convert_byte_array(&data)
                .map_err(|e| format!("data read: {}", e))?;

            if key_bytes.len() != 32 {
                return Err("key must be 32 bytes".into());
            }

            let mut key_arr = [0u8; 32];
            key_arr.copy_from_slice(&key_bytes[..]);
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
    }), JByteArray::default())
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
    catch_ffi(AssertUnwindSafe(|| {
        let result = (|| -> Result<Vec<u8>, String> {
            // Zeroizing: the Vec holding the raw key bytes is scrubbed on drop.
            let key_bytes = Zeroizing::new(env.convert_byte_array(&key)
                .map_err(|e| format!("key read: {}", e))?);
            let data_bytes = env.convert_byte_array(&data)
                .map_err(|e| format!("data read: {}", e))?;

            if key_bytes.len() != 32 {
                return Err("key must be 32 bytes".into());
            }

            let mut key_arr = [0u8; 32];
            key_arr.copy_from_slice(&key_bytes[..]);
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
    }), JByteArray::default())
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
    catch_ffi(AssertUnwindSafe(|| {
        let result = (|| -> Result<Zeroizing<[u8; 32]>, String> {
            // Zeroizing: the Vec holding the private key bytes is scrubbed on drop.
            let priv_bytes = Zeroizing::new(env.convert_byte_array(&local_priv)
                .map_err(|e| format!("priv read: {}", e))?);
            let pub_bytes = env.convert_byte_array(&remote_pub)
                .map_err(|e| format!("pub read: {}", e))?;

            if priv_bytes.len() != 32 || pub_bytes.len() != 32 {
                return Err("keys must be 32 bytes".into());
            }

            // X25519 DH
            let mut priv_arr = [0u8; 32];
            priv_arr.copy_from_slice(&priv_bytes[..]);
            let secret = x25519_dalek::StaticSecret::from(priv_arr);
            priv_arr.zeroize();

            let mut pub_arr = [0u8; 32];
            pub_arr.copy_from_slice(&pub_bytes);
            let peer_public = x25519_dalek::PublicKey::from(pub_arr);
            pub_arr.zeroize();

            // `secret` and `shared` are scrubbed on drop (x25519-dalek `zeroize` feature).
            let shared = secret.diffie_hellman(&peer_public);

            // Low-order point check
            if shared.as_bytes().iter().all(|&b| b == 0) {
                return Err("low-order point detected — aborting DH".into());
            }

            // HKDF-SHA256 Key Derivation
            let derived = Zeroizing::new(session::derive_key(
                shared.as_bytes(),
                None,
                b"SecureCall-AEAD-Key-v1",
            ));

            Ok(derived)
        })();

        match result {
            Ok(key) => {
                env.byte_array_from_slice(&key[..])
                    .unwrap_or_else(|_| JByteArray::default())
            }
            Err(_) => JByteArray::default(),
        }
    }), JByteArray::default())
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
    catch_ffi(AssertUnwindSafe(|| {
        // StaticSecret is scrubbed on drop (x25519-dalek `zeroize` feature).
        let secret = x25519_dalek::StaticSecret::random_from_rng(rand_core::OsRng);
        let public = x25519_dalek::PublicKey::from(&secret);

        // Zeroizing: the private-key copies are scrubbed on drop, also on panic.
        let mut combined = Zeroizing::new([0u8; 64]);
        {
            let secret_bytes = Zeroizing::new(secret.to_bytes());
            combined[..32].copy_from_slice(&secret_bytes[..]);
        }
        combined[32..].copy_from_slice(public.as_bytes());

        env.byte_array_from_slice(&combined[..])
            .unwrap_or_else(|_| JByteArray::default())
    }), JByteArray::default())
}

fn media_key_from(bytes: &[u8]) -> Option<AeadKey> {
    if bytes.len() != 32 {
        return None;
    }
    let mut key_arr = [0u8; 32];
    key_arr.copy_from_slice(bytes);
    let key = AeadKey::from_bytes(key_arr);
    key_arr.zeroize();
    Some(key)
}

fn media_context_from(bytes: &[u8]) -> Option<[u8; aead::MEDIA_CONTEXT_SIZE]> {
    if bytes.len() != aead::MEDIA_CONTEXT_SIZE {
        return None;
    }
    let mut context = [0u8; aead::MEDIA_CONTEXT_SIZE];
    context.copy_from_slice(bytes);
    Some(context)
}

fn media_role_from(role: jint) -> Option<u8> {
    match role {
        1 => Some(aead::MEDIA_ROLE_CALLER),
        2 => Some(aead::MEDIA_ROLE_CALLEE),
        _ => None,
    }
}

/// JNI: CoreCrypto.mediaSeal(byte[] key, int senderRole, byte[] context, long sequence, byte[] plaintext) -> byte[]
///
/// STX-24 media v3 sender. Returns the authenticated frame, or null on any error.
#[no_mangle]
pub extern "system" fn Java_com_securecall_crypto_CoreCrypto_mediaSeal<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    key: JByteArray<'local>,
    sender_role: jint,
    context: JByteArray<'local>,
    sequence: jlong,
    plaintext: JByteArray<'local>,
) -> JByteArray<'local> {
    catch_ffi(AssertUnwindSafe(|| {
        let result = (|| -> Option<Vec<u8>> {
            let role = media_role_from(sender_role)?;
            if sequence <= 0 {
                return None;
            }
            let plain_len = env.get_array_length(&plaintext).ok()?;
            if plain_len <= 0 || plain_len as usize > aead::MEDIA_MAX_PLAINTEXT {
                return None;
            }
            let key_bytes = Zeroizing::new(env.convert_byte_array(&key).ok()?);
            let aead_key = media_key_from(&key_bytes)?;
            let context = media_context_from(&env.convert_byte_array(&context).ok()?)?;
            let plain = Zeroizing::new(env.convert_byte_array(&plaintext).ok()?);
            aead::seal_media_frame(&aead_key, role, &context, sequence as u64, &plain).ok()
        })();
        match result {
            Some(frame) => env.byte_array_from_slice(&frame).unwrap_or_else(|_| JByteArray::default()),
            None => JByteArray::default(),
        }
    }), JByteArray::default())
}

/// JNI: CoreCrypto.mediaOpen(byte[] key, int expectedPeerRole, byte[] context, long highest, long bitmap, byte[] frame) -> byte[]
///
/// STX-24 media v3 receiver. `highest`/`bitmap` are the caller's last committed
/// replay state. Returns `[newHighest (8B BE) | newBitmap (8B BE) | plaintext]`
/// only for an authenticated, fresh frame; null otherwise. The caller commits the
/// new state atomically with its own lock; a rejected frame leaves it unchanged.
#[no_mangle]
pub extern "system" fn Java_com_securecall_crypto_CoreCrypto_mediaOpen<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    key: JByteArray<'local>,
    expected_peer_role: jint,
    context: JByteArray<'local>,
    highest: jlong,
    bitmap: jlong,
    frame: JByteArray<'local>,
) -> JByteArray<'local> {
    catch_ffi(AssertUnwindSafe(|| {
        let result = (|| -> Option<Zeroizing<Vec<u8>>> {
            let role = media_role_from(expected_peer_role)?;
            if highest < 0 {
                return None;
            }
            // Bound the untrusted frame before copying it out of the JVM.
            let frame_len = env.get_array_length(&frame).ok()?;
            if frame_len < 0 || (frame_len as usize) < aead::MEDIA_MIN_FRAME
                || frame_len as usize > aead::MEDIA_MAX_FRAME {
                return None;
            }
            let key_bytes = Zeroizing::new(env.convert_byte_array(&key).ok()?);
            let aead_key = media_key_from(&key_bytes)?;
            let context = media_context_from(&env.convert_byte_array(&context).ok()?)?;
            let frame_bytes = env.convert_byte_array(&frame).ok()?;
            let replay = aead::ReplayDetector::from_state(highest as u64, bitmap as u64);
            let (plain, next) = aead::open_media_frame(&aead_key, role, &context, &replay, &frame_bytes).ok()?;
            let plain = Zeroizing::new(plain);
            let (next_highest, next_bitmap) = next.state();
            let mut out = Zeroizing::new(Vec::with_capacity(16 + plain.len()));
            out.extend_from_slice(&next_highest.to_be_bytes());
            out.extend_from_slice(&next_bitmap.to_be_bytes());
            out.extend_from_slice(&plain);
            Some(out)
        })();
        match result {
            Some(out) => env.byte_array_from_slice(&out).unwrap_or_else(|_| JByteArray::default()),
            None => JByteArray::default(),
        }
    }), JByteArray::default())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Runs `f` with the panic hook silenced, then restores the previous hook.
    fn silenced<T>(f: impl FnOnce() -> T) -> T {
        let previous = std::panic::take_hook();
        std::panic::set_hook(Box::new(|_| {}));
        let out = f();
        std::panic::set_hook(previous);
        out
    }

    #[test]
    fn catch_ffi_returns_fallback_on_panic() {
        let r = silenced(|| catch_ffi(AssertUnwindSafe(|| -> i32 { panic!("boom") }), -1));
        assert_eq!(r, -1);
    }

    #[test]
    fn catch_ffi_passes_through_result() {
        let r = catch_ffi(AssertUnwindSafe(|| 42), -1);
        assert_eq!(r, 42);
    }

    #[test]
    fn c_abi_encrypt_decrypt_roundtrip() {
        let key = [0x11u8; 32];
        let plaintext = b"ffi boundary roundtrip";

        let mut enc_buf = vec![0u8; 1024];
        let mut enc_len = enc_buf.len();
        let rc = unsafe {
            core_crypto_encrypt(
                key.as_ptr(),
                plaintext.as_ptr(),
                plaintext.len(),
                enc_buf.as_mut_ptr(),
                &mut enc_len,
            )
        };
        assert_eq!(rc, 0);

        let mut dec_buf = vec![0u8; 1024];
        let mut dec_len = dec_buf.len();
        let rc = unsafe {
            core_crypto_decrypt(
                key.as_ptr(),
                enc_buf.as_ptr(),
                enc_len,
                dec_buf.as_mut_ptr(),
                &mut dec_len,
            )
        };
        assert_eq!(rc, 0);
        assert_eq!(&dec_buf[..dec_len], plaintext);
    }

    #[test]
    fn c_abi_encrypt_reports_small_buffer() {
        let key = [0x22u8; 32];
        let plaintext = b"some payload";
        let mut out = [0u8; 4];
        let mut out_len = out.len();
        let rc = unsafe {
            core_crypto_encrypt(
                key.as_ptr(),
                plaintext.as_ptr(),
                plaintext.len(),
                out.as_mut_ptr(),
                &mut out_len,
            )
        };
        assert_eq!(rc, -2);
        assert!(out_len > 4);
    }

    #[test]
    fn c_abi_encrypt_rejects_null_pointers() {
        let plaintext = b"x";
        let mut out = [0u8; 64];
        let mut out_len = out.len();
        let rc = unsafe {
            core_crypto_encrypt(
                ptr::null(),
                plaintext.as_ptr(),
                plaintext.len(),
                out.as_mut_ptr(),
                &mut out_len,
            )
        };
        assert_eq!(rc, -1);
    }

    #[test]
    fn c_abi_zeroize_clears_buffer() {
        let mut buf = vec![0xAAu8; 64];
        unsafe { core_crypto_zeroize(buf.as_mut_ptr(), buf.len()) };
        assert!(buf.iter().all(|&b| b == 0));
    }

    #[test]
    fn c_abi_zeroize_null_is_noop() {
        unsafe { core_crypto_zeroize(ptr::null_mut(), 16) };
    }

    /// Compile-time guard: the x25519-dalek `zeroize` feature must stay
    /// enabled so DH secret material is scrubbed on drop.
    #[test]
    fn x25519_secret_types_implement_zeroize() {
        fn assert_zeroize<T: Zeroize>() {}
        assert_zeroize::<x25519_dalek::StaticSecret>();
        assert_zeroize::<x25519_dalek::SharedSecret>();
    }
}
