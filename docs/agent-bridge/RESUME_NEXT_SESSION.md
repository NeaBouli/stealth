# RESUME NEXT SESSION — stealth

**Datum:** 2026-05-10
**Git HEAD:** `2ab058e`
**Version:** v1.0.33 (Play Internal Testing)

---

## Was diese Session erledigte

### STX-HIGH-03: server.js Modularisierung — VOLLSTÄNDIG ABGESCHLOSSEN

| Commit | Inhalt |
|--------|--------|
| `e2c358e` | context.smoke.js — 18 WS-Handler Smoke Test PASS |
| `2e37d6a` | BRIDGE.md: State Split-Brain Warning für server.js |
| `9ab447a` | TODO.md: STX-HIGH-03 DONE, uuid vuln FIXED |
| `2ab058e` | **server.js State Split-Brain FIXED** — buildContext() + wireWs() integriert |

**Commit `2ab058e` Details:**
- process.env.{FCM_TOKENS_FILE,CODES_FILE,WALLETS_FILE} nach DATA_DIR gesetzt
- state.js + Store-Module + middleware/ip.js + services/ifr.js + context.js importiert
- 1087-Zeilen wss.on("connection",...) Monolith entfernt
- Alle inline Map-Deklarationen ersetzt durch Singletons
- buildContext(externalDeps) + wireWs(wss, ctx) aufgerufen
- node --check PASS + context.smoke.js PASS (18 WS-Handler)

### Cross-Repo Crypto Test Coverage (vorherige Session)

| Repo | Commits | Inhalt |
|------|---------|--------|
| securechat | `9de5242` | BUG-001 fix: SodiumInitializer JVM fallback |
| securechat | `d8303b4` | 5 Argon2id-Tests |
| securechat | `c040c9a` | BRIDGE.md update |
| securechat | `126e334` | 8 DoubleRatchet-Tests |
| chameleon  | `e025bfa` | 5 Argon2id-Tests + LOGBUCH S-02 DONE |
| chameleon  | `da22245` | BRIDGE.md update |
| chameleon  | `28e4aeb` | 8 DoubleRatchet-Tests |
| chameleon  | `926500a` | LOGBUCH: 24/24 crypto tests |

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

## Nächste CC-Aufgaben

### 1. Staging / Railway Manual Smoke Test

Nach Railway Redeploy (oder lokal):
```bash
# REGISTER + CALL_INVITE Smoke Test
wscat -c ws://localhost:8080/signal
# {"type":"REGISTER","clientId":"alice","appSignature":"xxx"}
# {"type":"REGISTER","clientId":"bob","appSignature":"xxx"}
# {"type":"CALL_INVITE","to":"bob"}
```

### 2. NEA-14 nach gh auth refresh

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
backend/signaling/src/__tests__/context.smoke.js  18 WS-Handler Test (npm test)
backend/signaling/src/state.js               alle 16 Maps/Arrays (Singletons)
backend/signaling/src/middleware/             ip.js, cors.js, admin.js
backend/signaling/src/utils/                 phone.js, sanitize.js, json_store.js
backend/signaling/src/routes/                health.js, pkd.js, licenses.js
backend/signaling/src/services/              fcm_store.js, activation_store.js, wallet_store.js, ifr.js
backend/signaling/src/ws/index.js            central dispatcher
backend/signaling/src/ws/handlers/           register.js, call.js, webrtc.js, phone.js, subscription.js
backend/signaling/Dockerfile                 mit su-exec + entrypoint.sh
BRIDGE.md                                    State Split-Brain RESOLVED + Codex-Log
```

---

## GitHub Actions

Basic CI: PASS.
Security Audit: PASS.
Node.js 24 fix: STASHED — wartet auf `gh auth refresh -s workflow` von Gio.
