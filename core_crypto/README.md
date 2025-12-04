# securecall_core_crypto (CRYPTO-02 Skeleton)

Dies ist die **Core Crypto Engine** für das SecureCall-Ökosystem.

## Status

- Aktuell: **Skeleton / Platzhalter**
- Noch keine echte Verschlüsselung
- Ziel: später XChaCha20-Poly1305 / AES-GCM, DH-Key-Exchange, KDFs

## Architektur-Idee

- Rust als zentrale Crypto-Bibliothek
- Build als:
  - `rlib` für Rust-native Nutzer
  - `cdylib` für FFI (z. B. Android-JNI)
- Android ruft später über JNI:
  - `encrypt_in_place(key, buf)`
  - `decrypt_in_place(key, buf)`

## Öffentliche API (MVP)

- `SessionKey` (aktuell Dummy-Key)
- `encrypt_in_place(key, buf)`
- `decrypt_in_place(key, buf)`
- `self_test()` für einfache Integrationschecks

## Nächste Schritte (CRYPTO-02+)

1. Echte Key-Generation (z. B. X25519-Diffie-Hellman-Resultat + HKDF)
2. Implementierung von XChaCha20-Poly1305 (oder AES-GCM) über etablierte Library
3. Unit-Tests + Fuzzing
4. JNI-Bindings für Android (separates Modul / crate)
