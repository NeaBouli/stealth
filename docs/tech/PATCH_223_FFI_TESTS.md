# PATCH 223 — Rust FFI Tests (Stub Mode)

## Ziel
Sicherstellen, dass das Rust FFI Backend funktioniert, bevor libopus integriert wird.

## Testfälle

### 1. init + release
- Erstellt ein Decoder-Handle.
- Entfernt ihn wieder.
- Doppelte Release-Calls dürfen nicht zum Absturz führen.

### 2. decode mit ungültigem Handle
- Muss 0 zurückgeben.

### 3. decode mit gültigem Handle
- Muss:
  - >0 Samples zurückgeben
  - Stille (0) erzeugen, da skeleton-mode
  - nie crashen oder UB verursachen

## Wichtig
Alle Tests sind vollständig unabhängig von libopus.
