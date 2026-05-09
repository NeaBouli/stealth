# BRIDGE — stealth
# CC ↔ Codex ↔ Gio Kommunikationskanal

---

## 2026-05-09 15:00 [CC]
### STATUS: [DONE]
### TYPE: MEMO

Neuer Rechner. Repo frisch von GitHub geklont nach `~/Desktop/repos/stealth`.
Git-Identity gesetzt: georgios.mariotti@gmail.com

Offene Punkte aus ACTION_LOG.md (Codex-Handover):
- Railway CLI einloggen und `FORK_PROTECTION_MODE` prüfen/auf `warn` setzen
- Railway redeploy ausführen (Dockerfile-Fix + Fork-Protection-Fix müssen live)
- Play-Tester Retest nach Railway-Fix
- ADB-Status der Testgeräte prüfen (S10/S7/TabS4 — noch auf v1.0.31, nicht v1.0.32)

README-Badge auf v1.0.32 aktualisiert (war noch v1.0.28).

### EMPFÄNGER: GIO
### DEADLINE: ASAP

---

## 2026-05-09 17:30 [CC]
### STATUS: [IN_PROGRESS]
### TYPE: TODO + CODEX_ASSIGNMENT

**CC-Status:**
- Railway CLI v4.57.1 installiert (`npm install -g @railway/cli`)
- Railway login ausstehend (braucht Browser — Gio muss `railway login` bestätigen)
- npm audit fix für fast-xml-builder HIGH wird jetzt von CC angewendet

**CODEX — Bitte übernimm:**

### TASK 1 — FORK_PROTECTION_MODE Analyse [PRIORITY: CRITICAL]
Prüfe in `backend/signaling/src/server.js` (Zeile ~651-658):
- Wie ist der Default von FORK_PROTECTION_MODE gesetzt?
- Gibt es Hinweise dass Railway env var FORK_PROTECTION_MODE=enforce gesetzt ist?
- Schreibe in ACTION_LOG.md was du findest, mit Empfehlung ob wir: (a) env var entfernen, (b) auf "warn" setzen, oder (c) Code-Default reicht

### TASK 2 — BUG-026 Analyse (eSIM Call Routing) [PRIORITY: HIGH]
Lese `docs/BUGS.md` BUG-026. Analysiere ob VpnService-basiertes Traffic Steering in Kotlin/Android umsetzbar ist ohne Breaking Change. Schreibe Implementierungsplan in `docs/agent-bridge/BUGS.md` oder direkt hier.

### TASK 3 — BUG-029 Analyse (VPN+VPN kein Audio) [PRIORITY: CRITICAL]
Lese `docs/BUGS.md` BUG-029. Prüfe die WebRTC/TURN/ICE-Konfiguration im Android Client und backend/signaling. Empfehle Fix-Strategie.

### TASK 4 — Dockerfile Review [PRIORITY: HIGH]
Lese `backend/signaling/Dockerfile`. Verifiziere dass der Dockerfile-Fix (COPY data/) korrekt ist und Railway-kompatibel. Schreibe PASS/FAIL.

**Trennlinie CC/Codex:**
- CC übernimmt: npm security fixes, Railway deploy workflow, Linear issues anlegen
- Codex übernimmt: Code-Analyse, Bug-Research, Architektur-Review, Dokument-Updates

### EMPFÄNGER: CODEX
### DEADLINE: ASAP
