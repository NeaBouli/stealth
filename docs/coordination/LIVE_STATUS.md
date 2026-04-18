# LIVE_STATUS — v1.0.22 Launch-Koordination

**Letztes Update:** 17.04.2026
**Status:** HOLD — warte auf Play-Console-Daten G1/G2/G8, BETA-Code-Analyse laeuft

## Aktueller Stand
HOLD — Alpha laeuft seit 03:54 EEST, Production leer, Gate-Check ausstehend

## Gate-Matrix Production-Promotion

| Gate | Kriterium | Status | Quelle |
|------|-----------|--------|--------|
| G1 | Crash-Rate <1% | UNVERIFIZIERT | Play Console (Koordinator) |
| G2 | ANR-Rate <0.3% | UNVERIFIZIERT | Play Console (Koordinator) |
| G3 | Keine CRITICAL Findings | PASS | GitHub Issues |
| G4 | Keine HIGH ohne Mitigation | PASS | GitHub Issues |
| G5 | Keine "cannot connect" Reports | PASS | Alpha-Feedback |
| G6 | Railway Backend 5xx <0.5% | PASS | Monitoring |
| G7 | Alpha mind. 12h beobachtet | PENDING bis 15:54 EEST | Timer |
| G8 | Installationen ausreichend | UNVERIFIZIERT | Play Console (Koordinator) |
| G9 | BETA-Codes deaktiviert | OFFEN | TODO-047 |

## Laufende Fixes

### TODO-047 BETA-Codes
- Status: ANALYSE ABGESCHLOSSEN
- Ergebnis: BETA-Codes im Code bereits deaktiviert (auskommentiert)
- Risiko: activation_codes.json auf Railway Volume UNVERIFIZIERT
- Empfehlung: Option B (Hard-Disable-Flag) — 5 Zeilen Code, schliesst beide Pfade
- Analyse: docs/analysis/todo-047-beta-codes-analysis.md
- Naechster Schritt: Koordinator-Entscheidung (A/B/C)

### Issue #16 FCM-Volume-Permission
- Status: Fix auf Branch feature/fcm-volume-permissions
- Fix: mkdir + chown BEFORE USER switch in Dockerfile
- Wartet auf: lokalen Docker-Test, dann Production Deploy
- Branch: feature/fcm-volume-permissions (1 Commit)

## Offene Entscheidungen fuer Koordinator
- [ ] BETA-Code-Fix-Pfad (A/B/C) nach Analyse
- [ ] Production-Promotion-Zeitpunkt (heute/morgen)
- [ ] Rollout-Prozentsatz (10% Staged — NICHT 100%)

## Offene Aufgaben fuer Koordinator
- [ ] Play-Console-Daten G1, G2, G8 in docs/evidence/play-console-alpha-2026-04-17/
- [ ] Pre-Launch-Report pruefen

## Abort-Signale
- [ ] Crash-Rate >2%
- [ ] ANR-Rate >0.5%
- [ ] >3 "cannot connect" Reports binnen 1h
- [ ] Railway 5xx-Spike
- [ ] BETA-Code-Missbrauch

## Log
| Zeit | Event | Aktor |
|------|-------|-------|
| 17.04. 03:54 | v1.0.22 Closed Alpha Upload | Koordinator |
| 17.04. 09:30 | Status-Korrektur Production!=Alpha | Koordinator |
| 17.04. 09:45 | §13 eingefuehrt | Dev-Claude-Code |
| 17.04. 10:15 | LIVE_STATUS.md angelegt | Dev-Claude-Code |
| 17.04. 10:30 | TODO-047 Analyse abgeschlossen — Empfehlung Option B | Dev-Claude-Code |
| 17.04. 10:35 | #16 FCM Fix auf Branch feature/fcm-volume-permissions | Dev-Claude-Code |
| 17.04. 10:40 | WalletConnect Issue #17 erstellt | Dev-Claude-Code |
| 00:51 | Bug#1 FIXED+VERIFIED (server, 119s Call stable), Bug#2 FIXED (client, APK-Build pending). Session pausiert. | Dev-Claude-Code |
| 21:52 | Zombie-Session-Bug gefunden+gefixt+deployed (068c03e). APKs gebaut+installiert. Call-Tests morgen. | Dev-Claude-Code |
