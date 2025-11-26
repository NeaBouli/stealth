# PATCH 207 — GhostNet Media Pipeline (Skeleton)

## Ziel

Struktur für die spätere Media-Verarbeitung:

- Encrypted Frame (Transport)
- Decrypt (Crypto)
- Decode (Audio)
- Playback (Lautsprecher)

MVP (dieser Patch):
- `GhostMediaPipeline` mit `start()`, `stop()`, `isRunning()`
- Keine echten Audio-Funktionen
- Debug-Buttons in `MainActivity`

## Aktueller Flow

- User drückt "Start MediaPipeline":
  - `GhostMediaPipeline.start()` → running = true → Log

- User drückt "Stop MediaPipeline":
  - `GhostMediaPipeline.stop()` → running = false → Log

## Nächste Schritte

- Pipeline an `GhostNetSession.onCallActive()` koppeln
- MediaFrame-Decrypt → MediaFrame-Decode verknüpfen
- AudioTrack-Playback einbauen
- Fehler-Handling und Graceful Shutdown
