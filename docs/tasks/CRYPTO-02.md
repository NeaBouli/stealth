# CRYPTO-02 – Integration der Core Crypto Engine in Android

## Ziel
Einbindung der Rust-basierten Core Crypto Engine (CRYPTO-01) in die Android-App,
damit Android die Schlüsselgenerierung und Frame-Verschlüsselung nutzen kann.

## Aufgaben

### 1. Rust → JNI/FFI Bridge
- Erstellung eines Android-kompatiblen Rust-Builds (NDK)
- Ausgabe: `libcorecrypto.so` für arm64-v8a & armeabi-v7a
- JNI-Wrapper in Rust: Funktionen exportieren:
  - `generateIdentity()`
  - `startSession(localPriv, remotePub)`
  - `encryptFrame(sessionPtr, frameBytes)`
  - `decryptFrame(sessionPtr, cipherBytes)`

### 2. Android-Seite (Kotlin/Java)
- JNI-Wrapper-Klasse anlegen: `CoreCrypto.kt`
- ByteArray-Konvertierungen implementieren
- sichere Übergabe der Keys (keine Logs!)
- Memory-Wiping nach jeder Operation vorbereiten

### 3. Projekt-Setup
- NDK in Gradle aktivieren
- Cargo config für cross-compiling hinzufügen
- erste Unit-Tests schreiben (AndroidTest)

## Akzeptanzkriterien
- die App kann ein Schlüsselpaar generieren
- encrypt/decrypt funktioniert auf Dummy-Daten
- build.gradle baut erfolgreich ohne Fehler
- JNI funktionsfähig (Crash-free)

## Deliverables
- funktionierende JNI-Bindings
- aktualisierter Android-Code unter `client_android/`
- Datei docs/tasks/CRYPTO-02.md
