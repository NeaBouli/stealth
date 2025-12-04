# PATCH 218 — DecoderContext & Handle Registry

## Zweck
Stabile, sichere Rust-Struktur für Opus-Decoder-Kontexte.

Dieses Patch legt die Grundlage für:
- echte Opus-Integration (später)
- sichere Speicherverwaltung
- JNI-kompatible Handles
- parallele Decoder (z. B. Multi-Call)

## Komponenten

### DecoderContext
Rust-Struktur:
- sample_rate
- channels
- decode() (noch Fake, Silence-Ausgabe)

### Registry
- globale, Mutex-geschützte HashMap
- handle -> DecoderContext
- handle erzeugt als i64
- remove() löscht Kontext sauber

### FFI-Funktionen
- sc_opus_init: erzeugt Kontext + gibt handle
- sc_opus_decode: wandelt Pointer → Slice → ruft decode()
- sc_opus_release: entfernt Kontext

## Sicherheit
- Kein raw pointer juggling außerhalb Rust
- Kein Memory-Leak
- Thread-safe Registry

## Nächste Schritte
- PATCH 219: libopus FFI-Signaturen (unsafe extern C)
- PATCH 220: DecoderContext nutzt nun echte Opus-Funktion
