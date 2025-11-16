# OS-08 – Entfernen aller Google Services

## Ziel
Eliminierung sämtlicher Google-Komponenten, um Tracking & Telemetrie vollständig auszuschließen.

## Entfernen / Deaktivieren

### 1. Core Services
- Google Play Services
- Google Services Framework
- Google Login Service
- Google Contacts Sync
- Google Calendar Sync

### 2. Infrastruktur
- Firebase
- SafetyNet / Play Integrity
- Location Services
- Analytics Services

### 3. System-Apps
- Gmail
- Maps
- Chrome
- YouTube
- etc.

### 4. Alternativen
- microG: optional **nicht** empfehlenswert für Militärniveau
- komplett ohne Google-Komponenten

## Tests
- System bootet ohne GMS-Abhängigkeiten
- keine Fehlermeldungen im Systemserver
- keine Calls an Google-Endpoints

