# PATCH 209 — AudioDecoder Skeleton

## Status
- Keine echte Opus-Decodierung
- Nur Bytes → Fake-PCM konvertiert
- Nur Struktur + Lifecycle

## API
- start()
- stop()
- decode(MediaFrame): ShortArray

## Verknüpfung
- Wird automatisch über MediaPipeline.start() gestartet
- Später wird decode() im MediaRouter aufgerufen

## Nächste Schritte
- PATCH 210: Playback (AudioTrack Skeleton)
- PATCH 211: Integration decode() → playback()
- PATCH 212: Echte Opus Decodierung (JNI → libopus / Rust-Codec)
