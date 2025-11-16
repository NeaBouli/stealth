pub mod identity;
pub mod session;
pub mod aead;
pub mod utils;
pub mod ffi;

/// Öffentliche High-Level-API der Core Crypto Engine.
/// In CRYPTO-01 bleiben alle Funktionen als Skeleton ohne echte Implementierung.

/// Erzeugt ein neues Identitätsschlüsselpaar.
pub fn generate_identity() {
    // TODO: Implementierung in zukünftigen Tasks (CRYPTO-10x)
    unimplemented!();
}

/// Startet eine neue verschlüsselte Session.
pub fn start_session() {
    // TODO: Implementierung in zukünftigen Tasks
    unimplemented!();
}

/// Verschlüsselt einen Audioframe.
pub fn encrypt_frame() {
    // TODO: Implementierung in zukünftigen Tasks
    unimplemented!();
}

/// Entschlüsselt einen Audioframe.
pub fn decrypt_frame() {
    // TODO: Implementierung in zukünftigen Tasks
    unimplemented!();
}
