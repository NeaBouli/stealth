# BACKEND-06 – Management API (Premium/OS)

## Ziel
Backend-API für Premium- und OS-Geräteverwaltung, inkl.:

- Device-Owner Provisioning
- App-Whitelist
- Policy-Updates
- Remote-Wipe (Config)

## Endpunkte

### 1. Device Provisioning
- POST /device/register
- POST /device/policy/update
- POST /device/whitelist/update

### 2. Remote-Wipe
- POST /device/wipe

### 3. Security
- API-Key geschützt
- klare Audit-Logs (nur technische Logs)

### 4. Verhalten
- Premium-Geräte holen Policies auf Abruf
- OS-Geräte laden Policies beim Boot

## Tests
- Gerät registrieren → OK
- Whitelist ändern → Client reagiert
- Remote-Wipe löscht lokale Config

