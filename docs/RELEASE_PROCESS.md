# StealthX Platform — Release Process

## Die drei Stufen (PFLICHT für alle StealthX Apps)

### Stufe 1 — Internal Alpha (Entwickler-intern)
- Nur Entwickler und direkte Tester
- Google Play: Interner Test Track
- Ziel: Neue Features stabilisieren
- Voraussetzung für Stufe 2: Alle Unit Tests grün,
  kein kritischer Bug in LOGBUCH.md

### Stufe 2 — Closed Beta (Pre-Live)
- Geschlossene Testergruppe (max. 100 Tester)
- Google Play: Geschlossener Test Track (Alpha)
- Ziel: Realworld-Feedback, letzte Bugs
- Voraussetzung für Stufe 3: Keine P0/P1 Bugs,
  mindestens 7 Tage stabil

### Stufe 3 — Production Release (Live)
- Öffentlich auf Google Play / F-Droid
- Voraussetzung: Externer Security Audit abgeschlossen,
  Release Checklist vollständig

## KRITISCHE REGEL: Stable Builds sichern

BEVOR eine neue Funktion entwickelt wird:
1. Aktuellen stabilen Stand taggen: git tag stable-DATUM
2. Tag pushen: git push origin stable-DATUM
3. Niemals stabile Funktionen ohne separaten Branch anfassen
4. Feature Branches: feature/NAME — nie direkt auf main

## Aktueller Status
- SecureCall: Stufe 3 (Live-Einreichung bei Google Play)
- SecureChat: Stufe 1 (In Entwicklung)
- Chameleon: Stufe 1 (v0.1.0-alpha)
