# PATCH 213 — Opus Decoder Skeleton

## Ziel
Vorbereitung eines dedizierten Codec-Layers für Opus, ohne:
- JNI
- libopus
- Rust-Integration

Die Klasse `OpusDecoder` dient als zentrale Schnittstelle, damit:
- die MediaPipeline bereits gegen eine stabile API entwickelt werden kann,
- die native Implementierung später austauschbar ist,
- Tests & Mocks möglich sind, bevor echte Kryptografie/Kompression eingebaut wird.

## Aktueller Status
- `init(sampleRate, channels)`:
  - Merkt sich Initialisierungszustand
  - Loggt Parameter

- `decode(encoded: ByteArray): ShortArray`:
  - KEINE echte Opus-Decodierung
  - Fake-Mapping von Bytes -> ShortArray (für Tests / Pipeline)

- `release()`:
  - setzt Initialisierungsflag zurück
  - später: Ressourcenfreigabe (native)

## Nächste Schritte
- PATCH 214:
  - Integration in AudioDecoder (decode-Pfad nutzt OpusDecoder)
  - Session-Lifecycle: init()/release() an Call-Beginn/-Ende koppeln

- PATCH 215:
  - Entwurf JNI-Signaturen (Kotlin-Seite) + native Stubs

- PATCH 216:
  - Evaluation: Rust-basiertes opus-backend oder klassische libopus
