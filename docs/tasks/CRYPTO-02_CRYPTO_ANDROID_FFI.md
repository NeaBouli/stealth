# CRYPTO-02 – Crypto Engine Integration in Android (JNI/FFI)

## Ziel
Die Rust-Cryptoengine wird über FFI/JNI mit der Android-App verbunden.
Ziel: Dummy-Verschlüsselung → Dummy-Entschlüsselung.

---

## Erwartetes Ergebnis

### 1. JNI/FFI-Brücke
Rust → C-ABI → JNI → Android Kotlin/Java.

Native Funktionen (Stub):
- `jni_generateIdentity()`
- `jni_startSession()`
- `jni_encryptFrame()`
- `jni_decryptFrame()`

### 2. Android-Modulstruktur
Im Ordner `client_android/`:
- Pfad für native Bibliotheken: `app/src/main/jniLibs/`
- Kotlin/Java Wrapper:
  - `CryptoBridge.kt`

### 3. Build-Konfiguration
- Gradle NDK-Konfiguration aktivieren
- Rust-Build über Cargo + cbindgen vorbereiten
- Architekturziele: arm64-v8a, armeabi-v7a (optional: x86)

---

## Tests

### JNI-Funktionstest:
- ByteArray → encrypt → decrypt → Ergebnis identisch

### Build-Test:
- AndroidApp baut erfolgreich mit eingebundenen nativen Bibliotheken

---

## Developer FAQ

**Frage:** Brauchen wir echte Verschlüsselung?  
Antwort: Nein. Nur Roundtrip-Test (data in → data out).

**Frage:** Müssen wir schon Opus oder Audio integrieren?  
Antwort: Nein. Das kommt in ANDROID-02.

**Frage:** Wie viele .so Dateien?  
Antwort: Eine pro Architektur.

