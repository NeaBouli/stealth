# PATCH 221 — Rust Decoder Registry Cleanup

## Ziele
- Die Registry muss sicher, borrow-checker-freundlich und thread-safe sein.
- Kein Lock darf länger gehalten werden als nötig.
- Kein DecoderContext darf jemals im „schwebenden“ Zustand sein.
- Vorbereitung für echte Opus-Integration (PATCH 222ff).

## Wichtigste Änderung
Neues Pattern:

```rust
with_decoder_mut(handle, |ctx| {
    ctx.decode(...)
})
Vorteile:

kurze kritische Sektion

keine komplizierten Lifetimes

keine Borrow-Sorgen

kompatibel zu JNI & C++

Konsequenzen
Dieser Patch ebnet den Weg für:

OpusDecoder-Pointer-Allocation über opus_decoder_create

Frame-Decoding via opus_decode

sauberes Destroy über opus_decoder_destroy

Status
Keine Funktionalität geändert (Decode = Silence)

Nur Struktur verbessert

Komplett buildbar ohne libopus
