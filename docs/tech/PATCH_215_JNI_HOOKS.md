# PATCH 215 — JNI Hooks für Opus

## Ziel
Vorbereitung nativer Funktionalität ohne Implementierung.

## Komponenten:
- NativeOpus.kt (JNI-Signaturen, safe fallback)
- native-lib.cpp (Stub)
- CMakeLists.txt (Projektstruktur)
- build.gradle (native build skeleton)

## Benefits:
- Pipeline bleibt unverändert
- Kein Build-Fehler
- Rust/libopus kann später einfach angebunden werden

## Nächste Schritte:
- PATCH 216: Entscheidung Rust vs libopus
- PATCH 217: Rust-FFI Layout definieren
- PATCH 218: glue code für echte Decode-Funktion
