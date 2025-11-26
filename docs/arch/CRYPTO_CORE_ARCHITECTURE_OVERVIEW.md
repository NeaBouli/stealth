# SecureCall 🔐 — Core Crypto Architektur (High-Level Overview)

**Version:** CRYPTO-02 Skeleton  
**Status:** Android (MVP active), Rust CoreCrypto (Skeleton), Backend (Signaling MVP)

---

## 1. Motivation

SecureCall benötigt eine *einheitliche* Crypto-Schicht, die:

1. Plattformunabhängig ist (Android, iOS, Backend, optional Desktop)
2. determiniert funktioniert (keine abweichenden Implementierungen)
3. sicher ist (XChaCha20-Poly1305 / AES-GCM)
4. upgradebar ist (post-quantum-ready Design möglich)
5. sauber in GhostNet Session Lifecycle integriert wird

Daher wird **kein Android-Java-Crypto**, sondern eine **Rust-basierte Engine** verwendet.

---

## 2. Gesamtfluss (vereinfachtes Diagramm)

ANDROID APP
↓ (JNI)
Rust CoreCrypto (securecall_core_crypto)
↓ (Session Key)
GhostNet Session Engine
↓ (Frame Encrypt/Decrypt)
Transport (QUIC/WebSocket)
↓
Backend / Relay Nodes

yaml
Code kopieren

---

## 3. Rollen

### Android
- baut Frames (Control, Media)
- ruft Rust über JNI an
- nutzt decrypt() / encrypt() für Audio/Control Payloads
- verwaltet SessionState (ACTIVE/CONNECTING/DEAD)

### Rust CoreCrypto Engine
- generiert SessionKeys
- implementiert XChaCha20-Poly1305 (oder AES-GCM)
- garantiert konsistente Crypto auf allen Plattformen
- stellt FFI/JNI-APIs bereit:
  - `encrypt_in_place(key, buf)`
  - `decrypt_in_place(key, buf)`
  - `derive_session_key(...)`

### Backend (Signaling Server)
- hat **keine** Zugriff auf Klartext
- verteilt Schlüsselmaterial (ECDH-Public Keys)
- verwaltet Rendezvous, Session-IDs, Routing

---

## 4. Schlüssel-Austausch (ECDH → KDF → SessionKey)

Die eigentliche SessionKey-Erzeugung läuft später so:

Alice ECDH private key + Bob ECDH public key
↓
ECDH
↓
KDF
↓
32-Byte SessionKey

yaml
Code kopieren

**Dieser Ablauf läuft vollständig in Rust**, nicht in Android-Java.

---

## 5. Integration in die Media-Pipeline (bestehendes MVP)

Bestehender Flow:

TransportThread → MediaRouter → decrypt() → decode() → playback()

makefile
Code kopieren

Später:

decrypt() = Rust decrypt_in_place()
encrypt() = Rust encrypt_in_place()

yaml
Code kopieren

---

## 6. JNI-Schicht (CRYPTO-03, folgt später)

Es wird ein separates Modul geben:

android/app/src/main/jni/
libsecurecall.so → gebaut aus Rust (cargo + NDK)

cpp
Code kopieren

APIs:

```java
extern "C"
jbyteArray Java_com_securecall_crypto_CoreCrypto_encrypt(JNIEnv*, jclass, jbyteArray, jbyteArray key);

extern "C"
jbyteArray Java_com_securecall_crypto_CoreCrypto_decrypt(JNIEnv*, jclass, jbyteArray, jbyteArray key);
7. Wichtig für Entwickler
Android hält niemals den Klartext-Schlüssel im Java-Heap.

Nur Rust verwaltet Schlüsselmaterial.

SessionKeys werden direkt in native memory gehalten.

Memory-Wiping (zeroing) wird in Rust implementiert.

8. Nächste Schritte (CRYPTO-03 bis CRYPTO-05)
CRYPTO-03
JNI-Layer vorbereiten (Schnittstellen, Loader, stub native functions)

CRYPTO-04
Rust: echte Verschlüsselung (XChaCha20-Poly1305) implementieren

CRYPTO-05
Bindings:

Android CryptoEngine

decrypt() / encrypt() ersetzen Platzhalter

Dieses Dokument bildet den ersten offiziellen Baustein der gesamtarchitektonischen Crypto-Schicht.
