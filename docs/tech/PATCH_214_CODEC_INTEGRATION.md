# PATCH 214 — Integration AudioDecoder → OpusDecoder

## Ziel
Die Medienpipeline nutzt jetzt die Codec-Schicht.

## Änderungen:
- Neuer AudioDecoder (wrapt OpusDecoder)
- GhostMediaRouter ruft decodeViaCodec() auf
- Testbutton "Codec Pipeline"
- Noch immer FAKE-PCM — kompatibel, risikofrei

## Pipeline:
decrypt()
  → AudioDecoder.decode()
      → OpusDecoder.decode()
         → Fake-PCM
            → Playback

## Nächste Schritte:
- PATCH 215: JNI-Signaturen für echten Opus-Native-Decoder
- PATCH 216: Rust/libopus-Backend vorbereiten
