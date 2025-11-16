# ANDROID-03 – Security Monitor (Free-Version)

## Ziel
Ein Basis-Security-Monitor, der folgende Risiken erkennt:
- Screen Recording
- Developer Options
- USB Debugging
- WLAN-Status (öffentliches/offenes WLAN)

---

## Erwartetes Ergebnis

### 1. Security Checks
- `isScreenRecordingActive()`
- `isAdbEnabled()`
- `isDeveloperOptionsEnabled()`
- `isUntrustedWifi()`

### 2. UI-Verhalten
- Warnungen in CallFragment anzeigen
- Ampelstatus (grün/gelb/rot)

### 3. Logging
- Nur interne, nicht sensitive Logs
- Keine IDs, keine Metadaten speichern

---

## Tests
- Screen Recording aktivieren → Warnung sichtbar
- Developer Options aktivieren → Warnung sichtbar
- WLAN wechseln → Status aktualisiert

---

## Developer FAQ

**Frage:** Soll Root schon erkannt werden?  
Antwort: Nein. Root Detection ist Teil von SEC-01 in der Pro-Version.

**Frage:** Darf der Call automatisch blockiert werden?  
Antwort: In Free-Version: nur Warnung.

**Frage:** Wo laufen die Checks?  
Antwort: Im Hintergrundservice und beim Öffnen des CallScreens.

