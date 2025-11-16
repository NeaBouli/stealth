# OS-07 – No Background Apps Enforcement

## Ziel
Nur SecureCall darf im Hintergrund laufen — alle anderen Apps werden systematisch gestoppt.

## Anforderungen

### 1. Prozesskontrolle
- alle Nicht-System-Apps automatisch killen, sobald sie in den Hintergrund gehen
- Hintergrunddienste nicht zulassen
- Alarme/Jobs deaktivieren

### 2. Erlaubnisliste
- SecureCall
- Systemdienste (minimal)

### 3. Betriebssystemänderungen
- Änderung an ActivityManagerService
- Änderung der WakeLock-Policies
- PowerManager: restriktiver Modus

### 4. UI
- Nutzer kann keine Apps parallel öffnen
- Multitasking entfernt

## Deliverables
- rom_ghostos/app_control/NO_BACKGROUND_APPS.md

## Tests
- Apps bleiben niemals im Hintergrund
- SecureCall bleibt stabil
- kein Multitasking im System

