# PATCH 219 — libopus FFI Bindings

## Ziel
libopus-Funktionen in Rust deklarieren, ohne sie zu verwenden.

Dies ist eine Vorbereitungsphase:

- keine Integration in DecoderContext
- keine Audioverarbeitung
- keine Änderungen an JNI oder C++
- buildbar ohne libopus installiert zu haben

## Enthaltene Funktionen

- `opus_decoder_create`
- `opus_decoder_destroy`
- `opus_decode`
- `opus_decode_float`
- `opus_decoder_get_nb_samples`

## Warum jetzt?
Damit der nächste Patch (PATCH 220) die eigentliche Integration vornehmen kann:

- DecoderContext bekommt echten Opus-Decoder-Pointer
- decode() nutzt opus_decode()
- FFI-Fehlerwege werden abgebildet

aktueller Status: **safest possible stage**.

## Nächste Schritte
- PATCH 220: DecoderContext nutzt opus_bindings
- PATCH 221: Fehlercodes & sichere Rust-Wrappers
- PATCH 222: Qualitäts- & Latenztests
