# BACKEND-05 – Public Key Directory (PKD)

## Ziel
Bereitstellung eines minimalistischen Public-Key-Verzeichnisses zur Session-Einrichtung.

## Eigenschaften

### 1. Pseudonymes System
- Nutzer erhalten Key-ID, nicht Namen
- keine Telefonnummern
- keine E-Mail
- keine Metadaten

### 2. Endpunkte
- POST /key/register
- GET /key/<id>
- optional: rotate keys

### 3. Speicher
- reiner Memory-Store (Phase 1)
- später austauschbar gegen Redis/MemDB

### 4. Sicherheit
- PKD speichert *nur* öffentliche Schlüssel
- keine Logs außer Fehler
- IDs zufällig (128 Bit)

## Tests
- Key speichern/abrufen → OK
- kein Fingerprinting durch PKD
- Key-Rotation ohne Ausfall

