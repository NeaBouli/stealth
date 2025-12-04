# PATCH 210 — AudioPlayback Skeleton

## Ziel
Playback-Schicht vorbereiten:
- später AudioTrack
- später echte PCM-Wiedergabe
- jetzt nur Struktur

## Komponenten

### AudioPlayback
- start()
- play(pcm)
- stop()
- isRunning()

### MediaPipeline
läuft jetzt so:

start():
  - Decoder.start()
  - Playback.start()

stop():
  - Playback.stop()
  - Decoder.stop()

## Debug
- Fake Playback erzeugt Sinus-Daten und sendet sie an play()

## Nächste Schritte
- PATCH 211: MediaRouter: decrypt → decode → playback verbinden
- PATCH 212: echte AudioTrack-Instanz erzeugen
- PATCH 213: Opus (libopus) Integration
