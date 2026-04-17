# SecureCall / StealthX — Agent Debug Session 2026-04-16

## Status Überblick (Stand 2026-04-16 22:35 Athen / 19:35 UTC)

| Problem | Status | Deployment |
|---|---|---|
| **P0 Connection-Loop** (alle User können nicht verbinden) | ✅ GEFIXT | Railway `ALLOWED_SIGNATURES` entfernt, Deploy `44c59057` SUCCESS |
| **CRIT-001 WebRTC-Relay-Hijack** (Audit-Claim) | ❌ FALSE POSITIVE | `getSessionPeer()` prüft bereits Teilnehmerschaft, kein Bug |
| **CRIT-002 PII-Broadcast** (`SECUREID_CHANGED` sendet Raw-Phone) | ✅ GEFIXT | Commit `b610663`, Deploy `efeca16f` SUCCESS |
| **CRIT-003 Concurrent File-Writes** (Race auf JSON-Persistenz) | ✅ GEFIXT | Commit `b610663`, Deploy `efeca16f` SUCCESS |
| **CRIT-004 Railway Persistent Volume** | ✅ GEFIXT | Volume `143a832d` @ `/app/data`, Deploy `36509e3d`/`be900c28` SUCCESS — FCM persistiert |
| **HIGH-001 CORS Wildcard-Fallback** | ✅ GEFIXT | Commit `b79d9f7`, Railway Env `ALLOWED_ORIGINS=stealthx.tech,www.stealthx.tech` |
| **HIGH-003 Stripe-Webhook-Idempotenz** | ✅ GEFIXT | Commit `b79d9f7`, `stripe_processed_events.json` mit 14d-TTL |
| **HIGH-006 Password aus Stripe-Metadata** | ✅ GEFIXT | Commit `b79d9f7`, nur `pending_token` in Stripe-Metadata |
| **HIGH-007 Admin-Key vereinheitlicht** | ✅ GEFIXT | Commit `b79d9f7`, alle Endpoints nutzen `ADMIN_API_KEY` |
| **Regression nach HIGH-001** (Android rejected wg. fehlendem Origin) | ✅ GEFIXT | Commit `be3c47d`, `verifyClient` erlaubt origin-less native Clients |
| **CLIENT-HIGH-002 PII im Logcat + LOGGING_LEVEL** | ✅ CODE GEFIXT — APK-REBUILD NÖTIG | Commit `35ce2f2` — User bekommt Fix erst mit v1.0.22-Rollout |
| **HIGH-002, -004, -005, CLIENT-CRIT-001/-002, CLIENT-HIGH-001, MED-\*** | 🟠 OFFEN | siehe Audit-Sektion |

## Folgendes wurde nach Items 1-6 zusätzlich abgeschlossen (Session Fortsetzung)

| Problem | Status | Deploy / APK |
|---|---|---|
| **HIGH-002 FCM supersede hardening** | ✅ GEFIXT | Commit `32c6ba3` live |
| **HIGH-005 Heartbeat-Grace in Active-Calls** | ✅ GEFIXT | Commit `32c6ba3` live — 180s Timeout statt 60s |
| **HIGH-004 Activation-Code-Race** | ✅ N/A | Node single-threaded, kein echter Race — dokumentiert |
| **CRIT-001 WebRTC-Relay-Hijack** | ✅ N/A | `getSessionPeer()` filtert bereits — False Positive des Audits |
| **Custom-ID Token-Validation + Password-Persistence** | ✅ GEFIXT | Commit `32c6ba3` — `pending_activations.json` mit 1h-TTL, Token single-use |
| **Subscription Verify Endpoint** | ✅ GEFIXT | `POST /subscription/status` live auf Railway |
| **CLIENT-CRIT-001 + CLIENT-MED-002 REGISTER_ACK Flow** | ✅ GEFIXT in v1.0.22 | Commit `354dd81` — flush nur nach `REGISTERED`, nicht mehr nach 1.5s-Timer |
| **CLIENT-HIGH-001 4003-Stop-Retry** | ✅ GEFIXT in v1.0.22 | `HeartbeatClient.onClosing` stoppt bei 4000-4099 |
| **CLIENT-CRIT-002 Subscription Re-Sync** | ✅ GEFIXT in v1.0.22 | `verifyAgainstServer()` auf onResume, 6h-Rate-Limit |
| **APK v1.0.22 Build + Install auf allen 3 Geräten** | ✅ VERIFIZIERT | S10 premium (43-premium), S7 free, Tab S4 free — alle REGISTERN sauber, FCM persistiert |

## Test-Ergebnisse v1.0.22 (stand 2026-04-16 23:52 Athen)
- Railway Runtime-Logs zeigen:
  - `android-3bdecc0e` (S10, +491752536807) → REGISTER + FCM Token persisted ✅
  - `android-29f5caae` (S7/TabS4, +306982138623) → REGISTER + FCM Token persisted ✅
  - **0 `not_registered`-Errors** nach v1.0.22-Install (vorher bei jedem Reconnect)
- S10 Logcat: keine `unauthorized_client`, kein 4003-Loop, kein reconnect-Spin

## Noch TODO vor Launch
1. **APK v1.0.22-fdroid** build (läuft im Hintergrund) + F-Droid Update-Request
2. **APK-Release**: free APK auf stealthx.tech hochladen + Release-Notes schreiben
3. **Fork-Protection reaktivieren** → GitHub Issue [#15](https://github.com/NeaBouli/stealth/issues/15) (release-blocker-v1.0.23, security, priority-high). Plan: Adoption messen → Grace-Period → Railway Env-Var setzen → 24h Monitoring. Details + Risiko-Analyse im Issue.

## v1.0.23 Hotfix-Pipeline

- **Branch:** `release/v1.0.23-hotfix` (basiert auf Tag `v1.0.22` / `b974a1e`)
- **versionCode:** 44, **versionName:** 1.0.23
- **Template:** `docs/handover/v1.0.23-hotfix-template.md`
- **Build-Trockenlauf:** ✅ AAB signiert, 30MB, versionCode 44 > 43
- **Auslöser:** TBD — Rot-Alarm, Pre-Launch CRITICAL, oder >3 User-Reports/1h
- **Bereitschaft:** <30min von Fix-Commit bis Play-Console-Upload

## Offene Items (nicht kritisch für Launch)
- Railway Volume Permission EACCES → [#16](https://github.com/NeaBouli/stealth/issues/16) (severity-medium, release-target-v1.0.23)
- Restliche MEDIUM-Findings aus Audit (Phone-Lookup Rate-Limit, SDP-Bandwidth, Gift-Code-String-Dates, Auth-Failure-Logs)
- Global Uncaught Exception Handler in SecureCallApplication (Audit-Empfehlung)

---

## ✅ GELÖSTES PROBLEM — "App verbindet nicht seit letztem Upgrade"

### Root Cause
- Commit `d6cb024` (15.04.2026) fügte **App-Signatur-Prüfung beim REGISTER** hinzu
  — `backend/signaling/src/server.js:572-587` + `WebSocketService.kt:510-540`
- Railway Env-Var `ALLOWED_SIGNATURES` wurde auf Server gesetzt
  (Wert: `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`)
- **ABER:** Das auf User-Geräten installierte APK war v1.0.21 (vC42) — pre-d6cb024
  → Sendet KEIN `appSignature`-Feld im REGISTER
  → Server lehnt mit `close(4003, "Unauthorized client")` ab
  → Client reconnect-loop alle 4s

### Evidenz
- S10 Logcat: `Server error: unauthorized_client — App signature not authorized` (Loop)
- Railway Runtime Logs: `[REGISTER] Rejected — unauthorized signature:  from android-3bdecc0e` (leerer Wert!)
- 6+ distinkte User-IDs gleichzeitig betroffen: `android-3bdecc0e`, `android-edd41397`, `android-b53d17b5`, `android-f5ff5313`, `android-3ede4f81`, `android-3bdecc0e`
- APK-Strings-Scan: kein `appSignature` in installiertem APK enthalten

### Angewandter Fix (2026-04-16 18:31 UTC)
1. Railway Env-Var `ALLOWED_SIGNATURES` via GraphQL API **GELÖSCHT**
   - Backup: `/tmp/railway_allowed_signatures_backup.txt`
   - Server-Code ist rückwärtskompatibel: "no env var = all clients allowed"
2. Railway-Redeploy getriggert (Deployment `44c59057-071b-4de7-9659-809ec16766ea` → SUCCESS)
3. Verifiziert: Railway-Logs zeigen erfolgreiche REGISTERs + FCM-Token-Persistierung
4. S10 Logcat: keine `unauthorized`-Errors mehr, OkHttp WebSocket stabil

### Status auf den 3 lokalen Geräten
- ✅ S10 (RF8N313QMFL, com.securecall.app.premium): **CONNECTED**
- ⚠️ S7 (ce10160adc00152604, com.securecall.app.free): App läuft, keine WS-Aktivität im Logcat (vermutlich User-Input nötig)
- ⚠️ Tab S4 (ce12182c68644439037e, com.securecall.app.free): App läuft, keine WS-Aktivität im Logcat (vermutlich User-Input nötig)

**Alle echten User (Railway-Logs) registrieren erfolgreich.**

### ⚠️ WICHTIG: Fork-Protection wieder aktivieren NACH APK-Rollout
- Nach Release des neuen APK (v1.0.22 mit `getAppSignature()`-Code)
- UND ausreichend Grace-Period (~2 Wochen für F-Droid + Sideload-User)
- DANN Railway `ALLOWED_SIGNATURES` wieder setzen auf `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
- Alternative: Server-Code so erweitern, dass fehlende `appSignature` toleriert wird, aber vorhandene + falsche weiterhin rejected werden (sanfter Rollout)

---

## 🔴 WEITERE GEFUNDENE BUGS (Audit vor Launch in 3 Tagen)

### Backend (`backend/signaling/src/`)

#### CRITICAL
- ~~**CRIT-001**~~ ❌ FALSE POSITIVE vom Audit — `getSessionPeer()` in `server.js:329-336` gibt bereits `null` zurück wenn `myClientId` weder `session.from` noch `session.to` ist. Der anschließende `if (peerClientId) { sendToClient(...) }` wird dann übersprungen. Kein Hijacking möglich. **Keine Änderung nötig.**
- ~~**CRIT-002**~~ ✅ GEFIXT in Commit `b610663` (Railway Deploy `efeca16f`). `phoneNumber` aus `SECUREID_CHANGED`-Payload entfernt, Server-Log auf `phone-hash` umgestellt.
- ~~**CRIT-003**~~ ✅ GEFIXT in Commit `b610663`. Atomic writes (`writeFileSync → tmp + renameSync`) in: `server.js` (3 Stellen via `writeJsonAtomic()` Helper), `subscriptions.js`, `custom_ids.js`, `payments/sold_codes.js`, `licenses.js`.
- 🔴 **NEU — CRIT-004 Railway-Persistenz**: `/app/data` ist auf Railway nicht beschreibbar (`EACCES: permission denied, mkdir '/app/data'`). Kein Persistent Volume gemountet (via Railway-API verifiziert: `volumes.edges = []`). **Konsequenz:** `fcm_tokens.json`, `activation_codes.json`, `sold_codes.json`, `subscriptions.json`, `custom_ids.json`, `wallet_mappings.json`, `licenses.json` gehen bei jedem Deploy/Restart verloren. In-Memory-State funktioniert, aber FCM-Tokens + Stripe-Activation-Codes + User-Subscriptions verschwinden beim Restart. Fix-Optionen:
  1. Railway Persistent Volume unter `/app/data` mounten (empfohlen, kleiner Change, ~1GB reicht)
  2. Alternativ: auf externe Storage umstellen (Supabase/Redis/KV) — größerer Refactor

#### HIGH (Fix innerhalb 1 Woche)
- **HIGH-001** — `server.js:61-72`: CORS-Fallback auf `"*"` wenn `ALLOWED_ORIGINS` leer. Kombiniert mit CRIT-001 gefährlich.
- **HIGH-002** — `server.js:1099-1101`: FCM-Token bei REGISTER_FCM_TOKEN ohne Validierung überschrieben → Push-Hijacking wenn zweiter Client gleiche `clientId` nutzt.
- **HIGH-003** — `payments/stripe_handler.js:296`: Keine Idempotenz-Prüfung auf `session.id` → Stripe-Webhook-Retry = doppelte Aktivierungscodes = Revenue-Verlust.
- **HIGH-004** — `server.js:1262-1304`: Activation-Code `usedBy`-Race — bei parallelen Redeem-Requests kann `maxUses` überschritten werden.
- **HIGH-005** — `heartbeat.js:35-40`: 60s-Heartbeat-Timeout killt aktive Calls bei kurzzeitigem Media-Stall.
- **HIGH-006** — `custom_ids.js:238-239`: Password-Hash im Klartext in Stripe-Metadata gespeichert → wenn Stripe-Account kompromittiert, alle Passwörter exponiert.
- **HIGH-007** — `server.js:47/310/469`: Inkonsistenz `ADMIN_API_KEY` vs `ADMIN_KEY` — Admin-Routen prüfen teilweise gegen andere Env-Vars.

#### MEDIUM
- **MED-001** — Tote Datei `rateLimit.js` (nicht importiert) parallel zu `rate_limit.js` → löschen.
- **MED-003/004** — Phone-Lookup + Batch-Lookup Rate-Limit ist pro Connection, nicht pro ClientId/IP → Bruteforce möglich.
- **MED-006** — Mehrere `JSON.parse()` ohne try-catch → Server-Crash bei korrupten Daten.

### Android-Client (`client_android/`)

#### CRITICAL
- **CLIENT-CRIT-001** — `WebSocketService.kt:828-834`: Call-Queue wächst unendlich wenn REGISTER serverseitig ablehnt. Kombiniert mit `isRegistered`-Timer-Bug (MEDIUM-002) → Battery-Drain + silent failures.
- **CLIENT-CRIT-002** — `SubscriptionManager.kt:49-84`: Subscription-State wird nur lokal in SharedPrefs gehalten. Bei serverseitiger Kündigung/Chargeback nutzt User PRO-Features unbegrenzt weiter ohne Re-Verification.

#### HIGH
- **CLIENT-HIGH-001** — `HeartbeatClient.kt:253-275`: Bei Close-Code `4003` retried Client mit gleichen (invaliden) Credentials → CPU-Spin, Battery-Drain. Fix: bei 4003 stop nach N Versuchen.
- **CLIENT-HIGH-002** — `app/build.gradle:69,157` + diverse `.kt`-Logs: FREE/fdroid-Builds haben `LOGGING_LEVEL=DEBUG` → PII (Phone-Numbers, Session-IDs, Caller-Names) in Release-Logcat. F-Droid wird das flaggen. GDPR-Problem.

#### MEDIUM
- **CLIENT-MED-001** — `ContactRepository.kt:52-69`: Kontakt-Delete prüft nicht, ob aktiver Call mit diesem Kontakt läuft.
- **CLIENT-MED-002** — `WebSocketService.kt:420-433`: `isRegistered=true` wird nach 1.5s-Timer gesetzt, nicht nach `REGISTER_ACK` vom Server → Race + doppelte Calls möglich.
- **CLIENT-MED-003** — `BillingManager.kt:183-200`: Google-Play-Purchase wird nicht gegen Backend validiert (kein `/subscription/verify`-Call).

---

## 📋 EMPFOHLENE LAUNCH-PRIORITÄT

### Vor Launch (nächste 48h)
1. **CRIT-001** (WebRTC-Relay-Auth) — hochkritisch, Call-Hijacking
2. **CRIT-002** (PII-Broadcast) — GDPR + Doxing-Risiko
3. **CRIT-003** (atomic writes) — State-Korruption unter Launch-Traffic
4. **HIGH-001** (CORS-Fix) — verstärkt CRIT-001
5. **CLIENT-HIGH-002** (Logging-Level) — F-Droid-Blocker
6. **CLIENT-CRIT-001** (Call-Queue) — schlechte UX bei Netzwerkproblemen

### Kurzfristig (<1 Woche)
7. HIGH-002, HIGH-003, HIGH-006, HIGH-007
8. CLIENT-CRIT-002 (Subscription-Resync)
9. CLIENT-HIGH-001 (4003-Handling)
10. CLIENT-MED-002 (REGISTER_ACK)

### Nice-to-have
- MED-001 (Dead-Code löschen)
- Restliche MEDIUM-Findings
