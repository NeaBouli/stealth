# SecureCall – Core Crypto Engine

Dieser Ordner enthält die plattformübergreifende Kryptografie-Engine
(fokus: Rust), die von **Android**, **Backend** und **GHOSTOS** genutzt wird.

## Hauptaufgaben
- Identitätsschlüssel (Curve25519 / Ed25519)
- Session-Keys (Double Ratchet oder Noise)
- Frame-Verschlüsselung (AEAD: XChaCha20-Poly1305)
- zeroize Memory Management
- FFI/JNI Bindings für Android
- Fuzzing + Known Answer Tests
- keine Persistenz von Klartext-Schlüsseln

## Build-Ziele
- kompakte Library (libsecurecall_crypto.so)
- reproduzierbare Builds
- stabile API für Android-Integration

## Relevante Dokumente
- docs/SECURITY_DESIGN.md
- docs/ARCHITECTURE_OVERVIEW.md
- docs/tasks/CRYPTO-01.md
