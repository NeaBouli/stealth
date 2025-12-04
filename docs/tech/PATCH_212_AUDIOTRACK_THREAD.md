# PATCH 212 — AudioTrack Playback Thread

## Ziele
- Echte AudioTrack-Instanz
- Playback-Thread
- PCM schreiben
- Synchronisiert über BlockingQueue

## Komponenten:
- AudioPlaybackThread
- AudioPlayback (Thread Controller)
- MediaPipeline startet/stopp den Thread

## Debug
Der Button "AudioTrack Test" erzeugt Sinus-Paket und spielt es ab.

## Nächste Schritte
- PATCH 213: Opus Decoder Stub
- PATCH 214: Opus JNI Integration
