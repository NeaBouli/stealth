# PATCH 222 — lib.rs Cleanup & Konsolidierung

## Ziel

- Alle FFI-Funktionen (`sc_opus_init`, `sc_opus_decode`, `sc_opus_release`)
  in einer einzigen, sauberen Implementationsstelle bündeln.
- Alte Stub-Implementierungen aus vorherigen Append-Patches entfernen.
- Modulstruktur (`internal`, `opus`) klar und stabil halten.

## Wichtigste Punkte

- `sc_opus_init`:
  - erzeugt `DecoderContext` mit `decoder=null`.
  - legt Kontext in Registry ab.
  - gibt i64-Handle zurück.

- `sc_opus_decode`:
  - validiert Pointer und Längen.
  - nutzt `registry::with_decoder_mut(handle, |ctx| ...)`.
  - ruft `ctx.decode(...)` (derzeit: Stille-Ausgabe).
  - liefert 0 bei ungültigem Handle/Pointern.

- `sc_opus_release`:
  - entfernt den Kontext aus der Registry.
  - sorgt für sauberes Handle-Lifecycle.

## Status

- Noch keine echte libopus-Integration.
- Crate bleibt buildbar ohne verlinkte libopus-Bibliothek.
- FFI-Grenze für JNI/C++/Kotlin ist stabil und eindeutig.

## Nächste Schritte

- Eigene Methoden in `DecoderContext`:
  - `init_native_decoder()` / `drop_native_decoder()`
- Später:
  - Nutzung von `opus::opus_bindings`:
    - `opus_decoder_create`
    - `opus_decode`
    - `opus_decoder_destroy`
