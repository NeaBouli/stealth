# JNI Skeleton (CRYPTO-03)

Dies ist die JNI-Schicht zwischen Android und der Rust CoreCrypto Engine.

Status:
- Keine native Rust-Library eingebunden
- C-Skeleton implementiert die JNI-Signaturen
- Platzhalter: Daten werden 1:1 zurückgegeben

Später:
- Rust CoreCrypto baut libsecurecall_core_crypto.so
- C-Skeleton wird durch Cargo erzeugte Header ersetzt
- Encrypt/Decrypt/DeriveSessionKey nutzen echte Rust-Funktionen
