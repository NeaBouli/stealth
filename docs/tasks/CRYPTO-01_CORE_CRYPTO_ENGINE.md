# CRYPTO-01 – Core Crypto Engine Skeleton (Rust)

## Ziel
Erstellung der Modulstruktur der Core Crypto Engine in Rust als Grundlage aller späteren Krypto- und Session-Funktionen.

## Erwartetes Ergebnis
Ein Rust-Projekt unter `core_crypto/` mit folgender Struktur:

- identity/
- session/
- aead/
- utils/zeroize/
- ffi/
- lib.rs mit API-Signaturen:
  - generate_identity()
  - start_session()
  - encrypt_frame()
  - decrypt_frame()

## Nicht-Ziele
Keine echte Kryptographie — dies ist nur ein Skeleton-Task.

## Developer FAQ
**Frage:** Muss hier bereits echte Kryptographie implementiert werden?  
**Antwort:** Nein, nur Funktionssignaturen + Struktur.

**Frage:** Welche Rust-Version?  
**Antwort:** Rust Edition 2021.

**Frage:** Soll JNI/FFI in diesem Task schon funktionsfähig sein?  
**Antwort:** Nein, nur vorbereitende Strukturen im Modul ffi/.

