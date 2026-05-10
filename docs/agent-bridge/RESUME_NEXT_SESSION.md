# RESUME NEXT SESSION — stealth

**Datum:** 2026-05-10
**Git HEAD:** `8d7e1ec`
**Version:** v1.0.33 (Play Internal Testing)
**Devices connected:** S4, S9 (not S10)

---

## Was diese Session erledigte

### STX-HIGH-03 Backend Modularization — ALLE 8 SCHRITTE DONE

| Commit | Step | Inhalt |
|--------|------|--------|
| b4bf93d | 1 | state.js — pure mutable singleton (Codex) |
| f2d55dc | 2 | utils/phone.js + sanitize.js + json_store.js (CC) |
| 0a345f7 | 3 | middleware/ip.js + cors.js + admin.js (CC) |
| c8c7ff8 | 4 | routes/health.js + pkd.js + licenses.js (CC) |
| 2176745 | 5 | services/fcm_store.js + activation_store.js + wallet_store.js (CC) |
| 92c5808 | 6 | ws/index.js central dispatcher (CC) |
| 611cd7d | 7 | ws/handlers/ — register, call, webrtc, phone, subscription (CC) |
| 3ff9cf0 | 8 | context.js assembler + services/ifr.js (CC) |

**server.js bleibt unverändert** — alle neuen Module sind syntax-gecheckt, deployed, bereit.

### Fix GitHub Issue #16 — FCM/Railway Volume uid Mismatch

- Commit: `8d7e1ec`
- Problem: Railway mountet Volumes als root, Dockerfile `RUN chown` greift nicht
- Fix: `entrypoint.sh` + `su-exec` — runtime chown vor privilege drop
- Issue #16 geschlossen

### Linear

- NEA-10: Done (STX-HIGH-03)

---

## Pending: Gio-Aktionen erforderlich

### NEA-14 — GitHub Actions Node.js 24 Migration

Stash liegt bereit: `stash@{0}` (WIP on `b4bf93d`)
Token fehlt `workflow` scope.

**Gio muss ausführen:**
```
gh auth refresh -s workflow
```

Danach CC: `git stash pop && git push origin main`

### NEA-11 — Play Console Upload (URGENT)

```
~/Desktop/SecureCall-v1.0.33-vC55-FINAL.aab
```

### NEA-12 — BUG-029 Retest

VPN+VPN Audio auf S7 + Tab S4. Fix ist in Commit `30c87fd` (relay ICE bei VPN aktiv).

### NEA-13 — Hetzner Migration

Fragen in `MIGRATION_PLAN.md` beantworten.

---

## Nächste CC-Aufgaben

### 1. Codex-Antwort zu server.js Wiring abwarten (BRIDGE.md)

Codex soll minimalen Patch für server.js schreiben:
```js
const { buildContext, wireRoutes, wireWs } = require("./context");
const ctx = buildContext({ pkd, subscriptions, fcm, customIds, licenses, ICE_SERVERS, ... });
wireRoutes(app, ctx);
wireWs(wss, ctx);
```

### 2. Nach server.js-Wiring: Smoke-Test

Wenn Codex den Patch schreibt:
1. `node --check src/server.js`
2. Manueller REGISTER + CALL_INVITE test (lokal oder staging)
3. Commit + push

### 3. BUG-029 Diagnostic-Verbesserung

Aus BUGS.md: in SecLog aktive ICE-Kandidaten mit Typ/Protokoll loggen (nicht nur IDs).
Hilft beim Testen ob `relay/tcp` genutzt wird.

---

## Offene Bugs

| Bug | Status | Assignee |
|-----|--------|---------|
| BUG-026 VpnService eSIM | OPEN — auf v1.1.x verschoben | CC (v1.1.x) |
| BUG-029 VPN+VPN Audio | FIXED `30c87fd` — Retest pending | Gio (S7+S4) |

---

## Wichtige Dateien

```
backend/signaling/src/context.js          assembler fuer alle neuen Module
backend/signaling/src/state.js            alle 16 Maps/Arrays
backend/signaling/src/middleware/         ip.js, cors.js, admin.js
backend/signaling/src/utils/             phone.js, sanitize.js, json_store.js
backend/signaling/src/routes/            health.js, pkd.js, licenses.js
backend/signaling/src/services/          fcm_store.js, activation_store.js, wallet_store.js, ifr.js
backend/signaling/src/ws/index.js        central dispatcher
backend/signaling/src/ws/handlers/       register.js, call.js, webrtc.js, phone.js, subscription.js
backend/signaling/Dockerfile             jetzt mit su-exec + entrypoint.sh
backend/signaling/entrypoint.sh          runtime chown fix fuer Railway volumes
BRIDGE.md                                Codex-Handover fuer server.js wiring
```

---

## GitHub Actions

Basic CI: PASS auf allen commits.
Security Audit: laeuft (kein blocking finding erwartet).
Node.js 24 fix: STASHED — wartet auf workflow scope (`gh auth refresh -s workflow`).
