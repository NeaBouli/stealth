# Stealth TODO

## Payment / Etimologio — Owner Codex (2026-07-11)

**Zuweisung:** Alle Checkboxen in diesem Abschnitt sind Codex-Aufgaben, ausser eine Zeile nennt ausdruecklich `Core-Dev`, `Gio` oder `Accountant`. Der Core-Dev baut keinen parallelen Checkout-/Etimologio-Pfad.

**Repository-Zuweisung:** Auch alle Nicht-Payment-Produktaufgaben dieses Public Repos liegen bei Codex. Andere Devs bearbeiten nur explizit uebergebene Teilaufgaben oder Reviews.

- [x] VLABS als kanonischen Checkout fuer SecureCall/StealthX festlegen; Legacy-Direktcheckout default-off.
- [x] Signiertes/idempotentes SecureCall-Fulfillment und Vollrefund-Revoke lokal vorbereiten.
- [x] Produktseiten mit Preis-, Digitalleistungs-, Widerrufs- und Rechtehinweisen angleichen.
- [ ] Runtime-Secrets fuer VLABS -> SecureCall Fulfillment/Revocation in den jeweiligen privaten Deployments setzen; keine Werte in Git/Bridge.
- [ ] Stripe-Testmode E2E: Checkout -> paid webhook -> genau eine Aktivierung -> interne Invoice/Etimologio-Draft; Refund -> Revoke/Accounting Review.
- [ ] Accountant Mapping fuer SecureCall Pro/Premium und Suite freigeben; Provider-Demo danach separat testen.
- [ ] Gio Launch-/Deployment-Freigabe; erst danach Waren in VLABS von Coming Soon auf kaufbar stellen.
- [ ] Reviewer nach Handover: gezieltes Regression-/Security-Review der Fulfillment-/Activation-Grenze; keine Doppelimplementierung.

## Priority 1

- [x] F-Droid-/Lizenz-Restdrift — ERLEDIGT (Commit `8064dbd`)
  - CC-Gegenpruefung durchgefuehrt: Alle Codex-Funde bestaetigt und behoben.
  - Marketing/Store/Website/Email/Fastlane/Tools/Android-Code bereinigt.
  - GPL-3.0 → BUSL-1.1 in README, CONTRIBUTING, Website (alle Seiten).
  - Verwaiste Ordner entfernt.
  - Historische Docs (handover/session/bugs/planning) bewusst belassen als Archiv.
  - Verbleibend: `changelogs/42.txt` (historisch), `og-image.svg` (pruefen ob Text drin).

- [ ] Dependabot-/Security-Warnungen weiter abbauen:
  - Stand 2026-07-01:
    - GitHub API vor Fix: 7 offene Alerts (#25-31): `nodemailer`, `form-data`, `protobufjs`.
    - Fix: ungenutzte direkte `nodemailer`-Dependency entfernt; transitive `form-data` und `protobufjs` per Patch-Override aktualisiert.
    - Verifikation lokal: `npm audit --audit-level=moderate` -> 0 vulnerabilities; `npm test` -> PASS.
    - GitHub Dependabot API nach Push: 0 offene Alerts.
  - GitHub meldete beim Push am 2026-05-03: 6 Vulnerabilities.
  - Schweregrade: 1 critical, 1 high, 2 moderate, 2 low.
  - Stand nach Commit `da9b1bb` und erneuter GitHub Dependabot API-Abfrage:
    - Critical behoben: `protobufjs < 7.5.5`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-xq3m-2v4x-88gg`.
    - High behoben: `path-to-regexp < 0.1.13`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-37ch-88jc-xwx2`.
    - Medium behoben: `fast-xml-parser < 5.7.0`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-gh4j-gqv2-49f6`.
    - Low behoben: `rand >= 0.7.0, < 0.8.6`, Manifest `core_crypto/Cargo.lock`, GHSA `GHSA-cq8v-f236-94qc`.
    - HIGH behoben (2026-05-09): `fast-xml-builder <= 1.1.6`, GHSA `GHSA-5wm8-gmm8-39j9` — Commit `ef28d46`. Updated 1.1.5 → 1.2.0 via `npm audit fix`.
  - Weiterhin offen laut GitHub API:
    - ~~Medium: `uuid < 14.0.0`~~ — FIXED (nicht mehr in npm audit sichtbar, 2026-05-10).
    - Low: `@tootallnate/once < 3.0.1`, GHSA `GHSA-vpq2-c234-7xj6` — transitiv via firebase-admin, nur via firebase-admin Major Upgrade behebbar.
    - Low: firebase-admin chain (@google-cloud/firestore, @google-cloud/storage, google-gax, http-proxy-agent, retry-request, teeny-request) — transitiv, nur via firebase-admin Major-Upgrade behebbar. AKZEPTIERT.
  - Fix-Stand:
    - `backend/signaling/package-lock.json` aktualisiert.
    - `core_crypto/Cargo.lock` aktualisiert: `rand 0.8.5 -> 0.8.6`.
    - Kritische und hohe npm-Audit-Treffer lokal beseitigt: `npm audit --audit-level=high` exit `0`.
    - Rust Tests erfolgreich: `cargo test --locked` mit 34 Tests passed.
    - Backend Syntaxcheck erfolgreich: `node --check src/server.js`.
  - Rest-Risiko:
    - `npm audit` meldet weiterhin moderate/low transitive Risiken, hauptsaechlich `uuid`, `firebase-admin`/Google Cloud-Transitives, `resend`/`svix`, `@tootallnate/once`.
    - `npm audit fix --force` wuerde Breaking Changes/Downgrades vorschlagen (`firebase-admin@10.1.0`, `resend@6.1.3`, `uuid@14.0.0`). Nicht automatisch anwenden, weil Backend-/FCM-/Mail-Flows rolloutkritisch sind.
  - Naechster Schritt:
    - Mit Claude Code/Dev abstimmen, bevor funktionsabhaengige Aenderungen umgesetzt werden.
    - `uuid`:
      - App-Code nutzt inzwischen Node `crypto.randomUUID()` in `src/sessions.js` und `src/server.js`.
      - `package.json` enthaelt keinen direkten `uuid`-Dependency-Eintrag mehr.
      - `package-lock.json` enthaelt weiterhin `node_modules/uuid` und transitive `uuid`-Ketten.
      - Alert-Ziel `uuid@14` ist ein Major-Upgrade und darf nicht blind angewendet werden.
      - Moegliche Optionen: verwaisten Root-Lock-Eintrag gezielt entfernen/refreshen, Upstream-Abhaengigkeiten beobachten/aktualisieren, oder kompatiblen Update-Pfad fuer Google/Firebase/Svix/Resend-Transitives testen.
    - `@tootallnate/once`:
      - Transitiv ueber `firebase-admin -> @google-cloud/storage/google-gax -> retry-request -> teeny-request -> http-proxy-agent`.
      - Kein direkter App-Code-Verbrauch erkannt.
      - Fix vermutlich nur ueber Upstream-Paketupdates oder Dependency-Override moeglich; Override nur nach Test/CC-Abstimmung.
    - Direkt veraltete Pakete laut `npm outdated`: `express` major 5, `uuid` major 14. `nodemailer` auf 8.0.7 aktualisiert (2026-05-09).
    - `express@5` und `uuid@14` sind potenziell verhaltensrelevant und sollen nicht ohne gezielten Testplan geaendert werden.
    - **GitHub API Stand 2026-05-09:** 1 Low (firebase-admin/@tootallnate/once, transitiv, kein Fix ohne firebase-admin Major-Downgrade → Monitoring).

## Done / Monitoring

- [x] Website-Lizenz-/Branding-Texte live pruefen:
  - Website-Texte wurden angepasst.
  - GitHub Pages Deploy erfolgreich.
  - `stealthx.tech` live verifiziert.

## DISKUSSION ERFORDERLICH (CC + Codex)

### 1. ALLOWED_SIGNATURES — ERLEDIGT + LIVE

- Railway Variable gesetzt und deployed (2026-05-04).
- SHA-256: `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
- FORK_PROTECTION_MODE: enforce (Code-Default seit `b9202cb`).
- Server antwortet HTTP 200.

### 2. Lizenz-Entscheidung — ERLEDIGT

- Lizenz ist jetzt BUSL-1.1 (Commit `10a2ca4`).
- Change Date: 2030-05-04, danach GPL-3.0-only.
- Commercial use restricted.

### 3. uuid-Migration — ERLEDIGT

- **Commit:** `d13e8f4` — `uuid` entfernt, `crypto.randomUUID()` eingefuehrt.
- **Codex-Hinweis:** `uuidv4()` existiert nicht mehr. Neue API: `crypto.randomUUID()`.
- **Verbleibend:** Railway Redeploy noetig damit Aenderung live wirkt. Kein Breaking Change — selbes Output-Format.

### 4. @tootallnate/once (Low Priority)

- Transitiv via `firebase-admin` -> Google Cloud chain.
- Kein direkter Code-Verbrauch.
- Fix nur moeglich durch: `firebase-admin` Major-Upgrade ODER npm override.
- **Empfehlung:** Abwarten. `firebase-admin@13.7.0` ist aktuell. Alert ist "low" severity.
- Wenn `firebase-admin` eine neue Version released die das behebt, Lockfile-Update genuegt.
- Override (`"overrides": {"@tootallnate/once": "^3.0.1"}`) waere technisch moeglich, birgt aber Kompatibilitaetsrisiko mit Google-Cloud-Internals.
- **Status:** Monitoring. Kein Handlungsbedarf.

## Security Audit — Fix-Status (2026-05-04)

### Gefixt und gepusht

| ID | Finding | Commit |
|----|---------|--------|
| C-01 | Hardcoded activation codes entfernt (fail-closed) | `21b0957` |
| C-02 | Wildcard CORS auf /licenses/status entfernt | `edc6dc7` |
| C-03 | Stripe webhook ohne secret → 503 reject | `21b0957` |
| H-02 | /metrics hinter requireAdmin | `edc6dc7` |
| H-03 | DEREGISTER nur fuer registrierte Connections | `edc6dc7` |
| H-04 | /invite/accepted Rate Limit (3/10min) | `c7c7e06` |
| H-05 | /stripe/create-checkout + dynamic Rate Limit (5/10min) | `cbbbcd6` |
| H-06 | PHONE_LOOKUP/BATCH/ONLINE_STATUS require registration | `21b0957` |
| H-07 | Alle Codes+Emails in Logs maskiert (server+stripe+email) | `cf30743` |
| H-08 | JSON Injection in SettingsFragment → JSONObject | `1b39f9b` |
| M-01 | PKD PUT/DELETE hinter requireAdmin | `281320f` |
| L-01 | "open source" → "source available" in faq | `0b64d09` |
| L-02 | og-image.svg GPL → Source Available | `0b64d09` |

### Offen — braucht Gio-Entscheidung oder Client-Release

| ID | Finding | Blocker |
|----|---------|---------|
| ~~H-01~~ | ~~TURN credentials exposure~~ | **DONE** — HTTP hinter requireAdmin, WS REGISTERED-Delivery |
| ~~H-09~~ | ~~Certificate Pinning~~ | **DONE** — Commit `5949617`, Expiry 2027-03-12 |

### Codex Re-Verify Status

- `cf30743` (H-07 stripe_handler + email_handler Maskierung): re-verifiziert, Code-/Email-Logging deutlich verbessert; weiter nur Log-Hygiene beobachten.
- `c7c7e06` (H-04 invite Rate Limit): PARTIAL; Rate Limit verifiziert, aber kein Invite-Token/Auth-Nachweis sichtbar.
- `1b39f9b` (H-08 JSON Injection Fix): VERIFIED_FIXED; Custom-ID Submission nutzt `JSONObject`.
- `281320f` (M-01 PKD Auth): VERIFIED_FIXED; `PUT /key/:id` und `DELETE /key/:id` hinter `requireAdmin`.
- `0b64d09` (L-01/L-02 Text-Drift): VERIFIED_FIXED fuer FAQ/OG-Image; weitere Lizenz-/Release-Doku-Drift wurde spaeter separat nachverfolgt und teilweise bereinigt.

## Phase 4 — Release v1.0.29 (vC51) — DONE

- [x] versionCode 50→51, versionName 1.0.28→1.0.29
- [x] assembleFreeRelease + bundleFreeRelease
- [x] GitHub Release erstellt (v1.0.29)
- [x] Play Console: AAB Upload (v1.0.29)
- [x] Railway: Redeployed

## Phase 6 — Release v1.0.33 (vC55) — 2026-05-09

- [x] SECURITY: H-09 Certificate Pinning — network_security_config.xml (LE E7 + ISRG Root X1)
- [x] SECURITY: Privacy Claims bereinigt (Zero → Minimal metadata, FCM/STUN/TURN disclosure)
- [x] Rust deps: zerocopy/libc/typenum/unicode-ident patch updates — 6 tests OK
- [x] nodemailer: 8.0.4 → 8.0.7 patch update
- [x] versionCode 54→55, versionName 1.0.32→1.0.33
- [x] GitHub Release erstellt (v1.0.33) mit arm64 + armeabi APKs
- [x] APK auf S7 + Tab S4 deployed via ADB (v1.0.33-free, vC55001)
- [x] AAB: ~/Desktop/SecureCall-v1.0.33-vC55-FINAL.aab (Play Console ausstehend)
- [ ] Play Console: AAB Upload v1.0.33 (vC55) — ausstehend
- [ ] Railway: bereits live auf c6965e8 (nodemailer + BRIDGE update)

## Phase 5 — Release v1.0.32 (vC54)

- [x] BUG-FIX: Klingeln bei entsperrtem Screen (Service-Ringtone)
- [x] BUG-FIX: App bricht weg nach Remote-Hangup (returnToMain)
- [x] BUG-FIX: FCM-Ringtone-Luecke geschlossen (alle 3 Flavors)
- [x] BUG-FIX: Aktivierungscodes erneuert + SEED_ACTIVATION_CODES env var
- [x] BUG-FIX: Battery Optimization Permission + Dialog + WakeLock-Refresh
- [x] BUG-FIX: Fork Protection enforce → warn (Play Store re-signs APKs)
- [x] BUG-FIX: Dockerfile COPY data/ (activation codes ENOENT auf Railway)
- [x] BUG-FIX: EACCES Railway Volume — DATA_DIR auto-fallback auf /tmp/stealthx-data (Commit c7e17d3)
- [x] BUG-FIX: BUG-029 VPN+VPN kein Audio — IceTransportsType.RELAY wenn GhostVpnService.isActive (Commit 30c87fd, Codex+CC)
- [x] versionCode 51→54, versionName 1.0.29→1.0.32
- [x] GitHub Release erstellt (v1.0.31)
- [x] Play Console: AAB Upload v1.0.32 (vC54) — HOCHGELADEN
- [x] Railway: Redeploy — live auf 30c87fd
- [x] Railway: FORK_PROTECTION_MODE=warn — verifiziert
- [x] Android APK assembleFreeRelease — BUILD SUCCESSFUL, auf S10+S7 deployed via ADB
- [x] BUG-031 Fix: shouldOfferVerify() + showVerifyDialog() — matchesCallContact() Helper mit originalPhone Fallback (Commit 5239f71, NEA-8 Done)
- [x] BUG-023: SecLog CSV Export — bereits implementiert in SecLogManager.kt + SettingsFragment (kein weiterer Fix noetig)
- [x] BUGS.md aktualisiert: BUG-029, BUG-031, BUG-023 auf FIXED
- [ ] Tester: Play-Store-User retest — Server laeuft, FORK_PROTECTION_MODE=warn, connecten sollte funktionieren
- [ ] Manueller Test BUG-029: eingehender Call bei aktivem StealthX-VPN auf S10/S7 — Audio verifizieren
- [ ] Langzeittest: 20-30 Min gesperrt → eingehender Call

## Priority 2

- [x] README-/Download-Statusdrift — DONE
- [x] Play Integrity API fuer SecureCall planen: Plan in `docs/PLAY_INTEGRITY_PLAN.md`; zunaechst nur Risk-Signal/Logging, keine harte Sperre am App-Start.
- [ ] Hybrid-Migration ausfuehren (siehe `MIGRATION_PLAN.md`, Hetzner entschieden)

## Priority 3

- [x] ICE/TURN Endpoint `/ice-servers` hinter Auth (H-01 — ERLEDIGT): HTTP endpoint hinter requireAdmin, IceServerFetcher.kt nutzt WS REGISTERED-Injection (kein HTTP-Fetch mehr).
- [x] Certificate Pinning implementieren (H-09 — ERLEDIGT): `network_security_config.xml` — LE E7 intermediate + ISRG Root X1 Backup. README: "Planned" → "Yes". Expiration: 2027-03-12.
- [x] Privacy-/Metadaten-Claims bereinigt (2026-05-09):
  - "Zero metadata" → "Minimal metadata" in index.html (meta, twitter, hero, stat, table, pricing), privacy.html (meta/OG).
  - Feature card explizit: FCM tokens behalten fuer Push, STUN/TURN fuer WebRTC, kein Call-Content gespeichert.
  - privacy.html hat bereits klare Disclosures fuer FCM/STUN/TURN/signaling-transient (lines 98-109).
- [x] Backend-Monolith `backend/signaling/src/server.js` schrittweise modularisieren.
  - STX-HIGH-03: alle 8 Schritte abgeschlossen (2026-05-10). Commits: b4bf93d–3ff9cf0.
  - Module: state.js, utils/, middleware/, routes/, services/, ws/handlers/, context.js
  - context.smoke.js Smoke-Test PASS: 18 WS-Handler, 4 Helpers, 6 State-Maps (commit e2c358e).
  - CAVEAT: server.js noch unverändert — WS wireWs() Integration wartet auf State-Split-Brain-Fix.
  - Nächster Schritt: server.js importiert state.js + Store-Module, dann wireWs(wss, ctx) ersetzen.
- [x] Privacy-Metadaten-Claims gegen FCM/TURN/Signaling Realitaet pruefen.
