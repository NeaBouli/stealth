# PATCH 220 — DecoderContext vorbereitet für libopus

## Ziel

`DecoderContext` so erweitern, dass er einen libopus-Decoder-Zustand
aufnehmen kann, ohne bereits echte Opus-Funktionalität zu verlangen.

Damit bleibt:

- das Crate kompilierbar ohne verlinkte libopus-Bibliothek,
- die FFI-Struktur konsistent mit `opus_bindings`,
- der Umstieg auf echtes Decoding in einem späteren Patch klein und überschaubar.

## Änderungen

### 1. Neues Feld im DecoderContext

```rust
pub struct DecoderContext {
    pub sample_rate: i32,
    pub channels: i32,
    pub decoder: *mut OpusDecoder,
}
decoder zeigt aktuell immer auf null_mut().

Später: decoder wird durch opus_decoder_create() initialisiert.

2. new()
rust
Code kopieren
pub fn new(sample_rate: i32, channels: i32) -> Self {
    DecoderContext {
        sample_rate,
        channels,
        decoder: std::ptr::null_mut(),
    }
}
3. decode()
Unverändert „Fake-Decode“:

encoded wird ignoriert.

out_pcm wird mit Stille gefüllt.

Rückgabewert = Anzahl der geschriebenen Samples.

Später:

Wenn decoder != null:

Aufruf von opus_decode(decoder, encoded, …) aus opus_bindings.

Fehlercodes werden sauber bewertet und in Rust-Result-Typen gegossen.

Warum dieser Zwischenschritt?
Trennung von:

Datenstruktur/Handles (jetzt eingeführt)

externer Abhängigkeit / libopus-Linking (kommt später)

Der Architekt kann auf Basis dieses Patches

die FFI-Grenze dokumentieren,

Tests für Handle-Management planen,

ohne dass direkt ein NDK/libopus-Setup nötig ist.

Nächste Schritte
PATCH 221 (oder ähnlich):

DecoderContext erhält init_decoder() / close_decoder() Methoden.

Aufruf von opus_decoder_create / opus_decoder_destroy.

decode() nutzt dann Open-Source-Opus-Bibliothek wirklich.
