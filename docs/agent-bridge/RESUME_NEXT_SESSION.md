# RESUME NEXT SESSION — stealth

**Datum:** 2026-05-10
**Git HEAD:** `d33caa2`
**Version:** v1.0.33 (Play Internal Testing)

---

## Was diese Session erledigte

### STX-HIGH-03: server.js Modularisierung — VOLLSTÄNDIG ABGESCHLOSSEN

| Commit | Inhalt |
|--------|--------|
| `e2c358e` | context.smoke.js — 18 WS-Handler Smoke Test PASS |
| `2e37d6a` | BRIDGE.md: State Split-Brain Warning für server.js |
| `9ab447a` | TODO.md: STX-HIGH-03 DONE, uuid vuln FIXED |
| `2ab058e` | server.js State Split-Brain FIXED — buildContext() + wireWs() integriert |
| `39f8a5b` | handlers.test.js — 45 WS Handler Integration Tests PASS |
| `09588a4` | BRIDGE.md: handler tests log |
| `a1f385e` | BRIDGE + RESUME: docs update |
| `f7bd049` | subscription_webrtc.test.js — 58 Integration Tests PASS |
| `0443cb4` | BRIDGE.md: subscription/webrtc tests log |
| `d33caa2` | fix: test isolation — saveActivationCodes no-op + fcm_tokens.json gitignored |

### Test Coverage — signaling backend — VOLLSTÄNDIG

| Handler-Datei | Test-Suite | Assertions |
|---------------|------------|-----------|
| register.js | handlers.test.js | ✓ |
| call.js | handlers.test.js | ✓ |
| phone.js | handlers.test.js | ✓ |
| subscription.js | subscription_webrtc.test.js | ✓ (async VERIFY_IFR_LOCK excl.) |
| webrtc.js | subscription_webrtc.test.js | ✓ |
| context.js (18 handlers) | context.smoke.js | ✓ |

**`npm test`: 121/121 PASS** (18 smoke + 45 handlers + 58 subscription/webrtc)

**GitHub Actions (HEAD `0443cb4`):** Basic CI ✓ + Security Audit ✓

**Test-Isolation-Fix (HEAD `d33caa2`):**
- `activation_codes.json` wurde durch Tests überschrieben (saveActivationCodes schreibt echte Datei)
- Fix: saveActivationCodes als injizierbares externalDep in buildContext — Tests übergeben no-op
- `fcm_tokens.json` zu .gitignore hinzugefügt (war untracked runtime artifact)

---

## Pending: Gio-Aktionen erforderlich

### NEA-14 — GitHub Actions Node.js 24 Migration (BLOCKED)

Stash liegt bereit: `stash@{0}` (WIP on `b4bf93d`)
Token fehlt `workflow` scope.

**Gio muss ausführen:**
```bash
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

## Nächste CC-Aufgaben (autonom machbar)

### 1. Staging / Railway Manual Smoke Test

Nach Railway Redeploy (oder lokal):
```bash
wscat -c ws://localhost:8080/signal
# {"type":"REGISTER","clientId":"alice","appSignature":"xxx"}
# {"type":"REGISTER","clientId":"bob","appSignature":"xxx"}
# {"type":"CALL_INVITE","to":"bob"}
```

### 2. VERIFY_IFR_LOCK async path testen (optional)

In subscription_webrtc.test.js einen async Test ergänzen, der `ctx.verifyIfrLock`
über einen Monkey-Patch auf dem ctx-Objekt (vor Handler-Konstruktion) mockt.
Erfordert Anpassung in context.js: verifyIfrLock als injizierbares externalDep.

### 3. NEA-14 nach gh auth refresh

Nach `gh auth refresh -s workflow`:
```bash
cd /Users/gio/Desktop/repos/stealth
git stash pop   # WIP: workflow Node.js 24 fix
git push origin main
```

---

## Offene Bugs

| Bug | Status | Assignee |
|-----|--------|---------|
| BUG-026 VpnService eSIM | OPEN — auf v1.1.x verschoben | CC (v1.1.x) |
| BUG-029 VPN+VPN Audio | FIXED `30c87fd` — Retest pending | Gio (S7+S4) |

---

## Wichtige Dateien

```
backend/signaling/src/server.js               thin bootstrap — buildContext + wireWs
backend/signaling/src/context.js              assembler + wireWs/wireRoutes
backend/signaling/src/__tests__/context.smoke.js         18 WS-Handler (npm test)
backend/signaling/src/__tests__/handlers.test.js         45 assertions (npm test)
backend/signaling/src/__tests__/subscription_webrtc.test.js  58 assertions (npm test)
backend/signaling/src/state.js               alle 16 Maps/Arrays (Singletons)
backend/signaling/src/middleware/             ip.js, cors.js, admin.js
backend/signaling/src/utils/                 phone.js, sanitize.js, json_store.js
backend/signaling/src/routes/                health.js, pkd.js, licenses.js
backend/signaling/src/services/              fcm_store.js, activation_store.js, wallet_store.js, ifr.js
backend/signaling/src/ws/index.js            central dispatcher
backend/signaling/src/ws/handlers/           register.js, call.js, webrtc.js, phone.js, subscription.js
backend/signaling/Dockerfile                 mit su-exec + entrypoint.sh
BRIDGE.md                                    vollständiger Session-Log CC+Codex
```

---

## GitHub Actions

Basic CI: PASS (HEAD `0443cb4`).
Security Audit: PASS (HEAD `0443cb4`).
Node.js 24 fix: STASHED — wartet auf `gh auth refresh -s workflow` von Gio.
