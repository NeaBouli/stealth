# OS-04 – Verified Boot & Bootloader Security

## Ziel
Sicherstellen, dass die Systempartitionen nicht manipuliert werden können.

## Anforderungen

### 1. Verified Boot aktivieren
- AVB (Android Verified Boot)
- Hashes und Keys dokumentieren
- eigene Signierschlüssel generieren

### 2. Bootloader sperren
- nur signierte Images erlauben
- USB-Debug nur im Recovery-Modus

### 3. Recovery Mode sichern
- eigenes Recovery-Menü
- Factory Reset benötigt PIN/Passphrase
- kein ADB ohne explizite Erlaubnis

### 4. Key Management
- signing_key.bin → offline
- public_key.bin → im Image eingebunden
- Key-Rotation: verpflichtend bei jedem Release

## Deliverables
- AVB-Konfiguration
- signierte System-Images
- Schlüsselverwaltung dokumentiert in KEY_MANAGEMENT.md

## Tests
- Boot ohne Warnmeldungen
- Manipulierte Partition → muss Boot verweigern
- Recovery nur mit PIN

