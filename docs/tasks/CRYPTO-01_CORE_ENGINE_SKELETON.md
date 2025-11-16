# CRYPTO-01 – Core Crypto Engine Skeleton (Rust)

## Ziel
Ein vollständiges Grundgerüst der Kryptoengine in Rust, ohne echte Kryptographie.
Nur Modulstruktur, Typen, Funktionssignaturen und FFI-Vorbereitung.

---

## Erwartetes Ergebnis

Im Ordner `core_crypto/` soll entstehen:

core_crypto/
├── Cargo.toml
└── src/
├── lib.rs
├── identity/
│ └── mod.rs
├── session/
│ └── mod.rs
├── aead/
│ └── mod.rs
├── ffi/
│ └── mod.rs
└── utils/
├── zeroize.rs
└── mod.rs

yaml
Code kopieren

### API-Signaturen (nur Skeleton)
- `generate_identity() -> IdentityKeypair`
- `start_session(local: Identity, remote_pub: PublicKey) -> Session`
- `encrypt_frame(session: &Session, data: &[u8]) -> Vec<u8>`
- `decrypt_frame(session: &Session, data: &[u8]) -> Vec<u8>`

---

## Anforderungen

- Rust Edition 2021
- keine externen Krypto-Libs (noch nicht!)
- zeroize-Funktionen als leere Platzhalter
- FFI-Modul enthält nur Signatur-Stubs

---

## Tests
Nur Kompilationstests (cargo check).  
Keine Funktionstests notwendig.

---

## Developer FAQ

**Frage:** Wann kommen echte Algorithmen?  
Antwort: Erst in CRYPTO-03 und CRYPTO-04.

**Frage:** JNI/FFI sofort umsetzen?  
Antwort: Nein, nur Gerüst.

**Frage:** Ist Zeroize real?  
Antwort: Noch nicht – nur Stub.

