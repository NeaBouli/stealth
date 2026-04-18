# LIVE_STATUS — v1.0.22 Launch-Koordination

**Letztes Update:** 19.04.2026
**Status:** CALL-TESTS PASS — TODO-047 + FCM deployed — warte auf Play-Console G1/G2/G8

## Aktueller Stand
Call-Tests alle 5 PASS. Speaker funktioniert (S10/S7). BETA-Codes disabled. FCM Volume-Fix deployed.

## Gate-Matrix Production-Promotion

| Gate | Kriterium | Status | Quelle |
|------|-----------|--------|--------|
| G1 | Crash-Rate <1% | UNVERIFIZIERT | Play Console (Koordinator) |
| G2 | ANR-Rate <0.3% | UNVERIFIZIERT | Play Console (Koordinator) |
| G3 | Keine CRITICAL Findings | PASS | GitHub Issues |
| G4 | Keine HIGH ohne Mitigation | PASS | GitHub Issues |
| G5 | Keine "cannot connect" Reports | PASS | Call-Tests 5/5 |
| G6 | Railway Backend 5xx <0.5% | PASS | health OK, uptime stable |
| G7 | Alpha mind. 12h beobachtet | PASS | seit 17.04. 03:54 EEST |
| G8 | Installationen ausreichend | UNVERIFIZIERT | Play Console (Koordinator) |
| G9 | BETA-Codes deaktiviert | PASS | TODO-047 deployed (6b05874) |

## Erledigte Fixes

### TODO-047 BETA-Codes — DEPLOYED
- Commit: `6b05874` — Blocklist vor Validation
- BETA-PRO0-2026 + BETA-PREM-2026 blockiert
- Bestehende User behalten Tier (subscriptions.json unabhaengig)
- Railway auto-deployed

### Issue #16 FCM-Volume-Permission — DEPLOYED
- PR #18 merged (squash)
- Fix: mkdir + chown BEFORE USER switch in Dockerfile
- Docker-Test: `/app/data` ownership `securecall:securecall` verifiziert
- Railway auto-deployed

### Bug #1 Call-Drop bei WS-Reconnect — DEPLOYED
- Commit: `abeb141` — Session-Cleanup Guard
- Verifiziert: 119s Call stabil

### Bug #3 Zombie-Session — DEPLOYED
- Commit: `068c03e` — isActiveConnection vor Cleanup
- Railway deployed, health OK

### Speaker-Toggle — DEPLOYED
- Commit: `5d32a7d` — AudioTrack.Builder + Logging + UI-Sync
- Verifiziert: S10↔S7 funktioniert
- Tab S4: kein earpiece (Hardware), Speaker ist einziges Output

## Offene Entscheidungen fuer Koordinator
- [ ] Production-Promotion-Zeitpunkt
- [ ] Rollout-Prozentsatz (10% Staged empfohlen — NICHT 100%)

## Offene Aufgaben fuer Koordinator
- [ ] Play-Console-Daten G1, G2, G8 in docs/evidence/
- [ ] Pre-Launch-Report pruefen

## Abort-Signale
- [ ] Crash-Rate >2%
- [ ] ANR-Rate >0.5%
- [ ] >3 "cannot connect" Reports binnen 1h
- [ ] Railway 5xx-Spike

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
| 18.04. 00:51 | Bug#1 FIXED+VERIFIED (server, 119s Call stable), Bug#2 FIXED (client) | Dev-Claude-Code |
| 18.04. 21:52 | Zombie-Session-Bug gefunden+gefixt+deployed (068c03e) | Dev-Claude-Code |
| 19.04. 00:00 | Call-Tests 5/5 PASS, Speaker verifiziert, TODO-047+FCM deployed | Dev-Claude-Code |
