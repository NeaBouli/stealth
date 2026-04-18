# Debug-Session 18.04.2026 Nacht

## Erledigt
- APKs v1.0.22 (vC43) alle 3 Flavors gebaut + installiert (S10/S7/Tab S4)
- WS-Connectivity alle 3 Geräte: OK (android-c5dfc682, android-339e72c7, android-63b7fc37)
- Server health: OK (4 clients connected)
- RECORD_AUDIO Tab S4: per ADB granted (war `granted=false`)
- Bug #2 AudioTrack.Builder: im APK-Binary verifiziert (classes6.dex: AudioTrack$Builder + setAudioAttributes)
- Bug #1 Zombie-Session: gefunden + gefixt + deployed (Commit `068c03e`)
- Speaker-Toggle Code-Review: korrekt (CallActivity.java:298-309)

## Bug #1 Zombie-Session Fix (NEU gefunden in dieser Session)
- **Root Cause:** Z.1505-1506 löschte `clientIds[clientId]` VOR dem Session-Cleanup auf Z.1516
- **Effekt:** `clientIds.get(clientId)` war danach `undefined` → Guard griff nie → Sessions blieben als Zombies
- **Fix:** `isActiveConnection` einmal prüfen, dann Sessions erst aufräumen, dann clientId löschen
- **Commit:** `068c03e` — Railway deployed (uptime 53s = fresh restart bestätigt)

## Morgen früh (in dieser Reihenfolge)
1. Railway health prüfen (zombie-fix aktiv?)
2. Call 2: S10→S7 — Speaker ON/OFF mit Logcat verifizieren
3. Call 3: S10→Tab S4
4. Call 4: Tab S4→S7
5. Call 5: S7→Tab S4
6. Pro Call: Uhrzeit + Ergebnis + auffällige Log-Zeilen

## Geräte bereit
| Gerät | Serial | Flavor | Version | WS clientId |
|-------|--------|--------|---------|-------------|
| S10 | RF8N313QMFL | premium-debug | v1.0.22 vC43 | android-c5dfc682 |
| S7 | ce10160adc00152604 | free-debug | v1.0.22 vC43 | android-339e72c7 |
| Tab S4 | ce12182c68644439037e | pro-debug | v1.0.22 vC43 | android-63b7fc37 |
