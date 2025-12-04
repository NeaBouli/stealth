# PATCH 211 — MediaRouter Full Pipeline Link

Diese Änderung verbindet:

decrypt() → decode() → playback()

Ein Fake-PCM wird erzeugt, konvertiert und an AudioPlayback weitergeleitet.

## Ablauf
MediaFrame →
  decrypt() →
  decode() →
  toShorts() →
  AudioPlayback.play()

## Debug
- Neuer Button „Pipeline Test“ sendet Zufalls-Frame durch gesamte Pipeline.
- Ausgabe erscheint im Logcat:

MEDIA_ROUTER: route()...
MEDIA_DECRYPT...
MEDIA_DECODE...
AUDIO_PLAYBACK: play()...

## Nächste Schritte
- PATCH 212: AudioTrack Instanz + Thread
- PATCH 213: Opus Decoder Stub
