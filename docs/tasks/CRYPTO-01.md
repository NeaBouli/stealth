# TASK: CRYPTO-01 – Core Crypto Engine Skeleton (Rust)

## 1. Ziel des Tasks
Erstellung des Grundgerüsts der neuen Core Crypto Engine.  
Keine echte Kryptografie – nur Strukturen, Module, Funktionssignaturen und FFI-Schnittstellen.

Dieses Modul wird von:
- Android-App,
- Backend (optional),
- GHOSTOS

benutzt.

---

## 2. Anforderungen

### 2.1 Projektstruktur (Rust)

Der Ordner `core_crypto/` soll folgende Struktur enthalten:

core_crypto/
├── Cargo.toml
├── src/
│ ├── lib.rs
│ ├── identity.rs
│ ├── session.rs
│ ├── aead.rs
│ ├── utils/
│ │ ├── zeroize.rs
│ │ └── mod.rs
│ └── ffi/
│ ├── mod.rs
│ └── android.rs

yaml
Code kopieren

### 2.2 Modulverantwortungen

#### identity.rs
- Funktionssignaturen:
  - `generate_identity() -> IdentityKeypair`
- Strukturdefinitionen:
  - `struct IdentityPublicKey`
  - `struct IdentitySecretKey`
  - `struct IdentityKeypair`

#### session.rs
- Funktionssignaturen:
  - `start_session(local: &IdentityKeypair, remote_pub: &IdentityPublicKey) -> Session`
- Strukturen:
  - `struct Session`

#### aead.rs
- Funktionssignaturen:
  - `encrypt_frame(session: &Session, plaintext: &[u8]) -> Vec<u8>`
  - `decrypt_frame(session: &Session, ciphertext: &[u8]) -> Result<Vec<u8>, CryptoError>`

#### utils/zeroize.rs
- Dummy-Funktionen für Zeroization
- später Implementierung mit `zeroize` Crate

#### ffi/android.rs
- Dummy-FFI-Funktionssignaturen für JNI-Integration:
  - `jni_generate_identity()`
  - `jni_encrypt_frame()`
  - `jni_decrypt_frame()`

---

## 3. Deliverables

- Vollständige Rust-Projektstruktur
- Kompiliert erfolgreich (Cargo build)
- Keine echte Kryptografie, nur Dummy-Rückgaben
- Saubere Modulgrenzen
- Dokumentation (kurz) im README innerhalb core_crypto/

---

## 4. Testkriterien

### 4.1 Build
- Projekt muss ohne Fehler kompilieren

### 4.2 API-Vollständigkeit
- Alle geforderten Funktionssignaturen vorhanden
- Dummy-Implementationen geben plausible Platzhalter zurück

### 4.3 Ordnung
- Module sauber voneinander getrennt
- lib.rs muss alle APIs „exportieren“

---

## 5. Q&A

**F:** Müssen wir bereits echte Curve25519/Noise/AEAD implementieren?  
**A:** Nein. CRYPTO-01 ist rein strukturell, nicht funktional.

**F:** Müssen wir schon FFI-Typen an Android anpassen?  
**A:** Nur Grund-Signaturen erstellen. Die echte JNI-Implementierung folgt in CRYPTO-02.

**F:** Darf ich Rust-Crates einbinden?  
**A:** Ja, aber für CRYPTO-01 nur Standardbibliothek. Externe Crates kommen erst in CRYPTO-02/03.

---

## 6. Referenzen
- docs/ARCHITECTURE_OVERVIEW.md
- docs/SECURITY_DESIGN.md
- PROJECT_MASTER_PLAN.json
