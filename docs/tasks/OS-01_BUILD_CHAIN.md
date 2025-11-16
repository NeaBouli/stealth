# OS-01 – Build Chain Setup für GHOSTOS BlackRoot

## Ziel
Einrichten der kompletten Build-Umgebung für das neue Spezialbetriebssystem „GHOSTOS BlackRoot“.

## Umfang der Aufgabe

### 1. Auswahl der Basis
- AOSP (Android Open Source Project) **oder**
- GrapheneOS (bevorzugt, wegen Hardened Toolchain, Memory Tagging, Exploit Mitigation)

Wichtig:
- zunächst auf *Pixel 6/7/8* beschränken (beste AOSP/GrapheneOS-Unterstützung)

### 2. Build-Umgebung einrichten
- Ubuntu 22.04 LTS (empfohlen)
- Java 17
- Repo Tool
- Git-LFS (optional)
- 200 GB Diskspace empfohlen

### 3. Reproduzierbarkeit
- alle Toolchain-Versionen dokumentieren:
  - clang Version
  - llvm Version
  - Kernel-Version
  - Python 3.x Version
- Vergleichbarkeit sichern:  
  → `BUILD_NOTES.md` wird automatisch generiert

### 4. Minimal-Image
- Build des *Vanilla-AOSP/GrapheneOS* ohne Modifikationen
- Flash-Test auf Pixel-Gerät
- Dokumentation aller Build-Schritte

## Abgabe / Deliverables
- rom_ghostos/BUILD_CHAIN.md
- funktionierender erster Build
- Liste aller benötigten Pakete
- Hashes der finalen System-Images

## Tests
- Test-Flash auf Pixel: erfolgreich
- Bootvorgang: ohne Fehlermeldungen
- Verified Boot: darf NICHT fehlschlagen

