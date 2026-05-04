# Stealth TODO

## Priority 1

- [x] F-Droid-/Lizenz-Restdrift — ERLEDIGT (Commit `8064dbd`)
  - CC-Gegenpruefung durchgefuehrt: Alle Codex-Funde bestaetigt und behoben.
  - Marketing/Store/Website/Email/Fastlane/Tools/Android-Code bereinigt.
  - GPL-3.0 → BUSL-1.1 in README, CONTRIBUTING, Website (alle Seiten).
  - Verwaiste Ordner entfernt.
  - Historische Docs (handover/session/bugs/planning) bewusst belassen als Archiv.
  - Verbleibend: `changelogs/42.txt` (historisch), `og-image.svg` (pruefen ob Text drin).

- [ ] Dependabot-/Security-Warnungen weiter abbauen:
  - GitHub meldete beim Push am 2026-05-03: 6 Vulnerabilities.
  - Schweregrade: 1 critical, 1 high, 2 moderate, 2 low.
  - Stand nach Commit `da9b1bb` und erneuter GitHub Dependabot API-Abfrage:
    - Critical behoben: `protobufjs < 7.5.5`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-xq3m-2v4x-88gg`.
    - High behoben: `path-to-regexp < 0.1.13`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-37ch-88jc-xwx2`.
    - Medium behoben: `fast-xml-parser < 5.7.0`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-gh4j-gqv2-49f6`.
    - Low behoben: `rand >= 0.7.0, < 0.8.6`, Manifest `core_crypto/Cargo.lock`, GHSA `GHSA-cq8v-f236-94qc`.
  - Weiterhin offen laut GitHub API:
    - Medium: `uuid < 14.0.0`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-w5hq-g745-h8pq`.
    - Low: `@tootallnate/once < 3.0.1`, Manifest `backend/signaling/package-lock.json`, GHSA `GHSA-vpq2-c234-7xj6`.
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
    - Direkt veraltete Pakete laut `npm outdated`: `express` major 5, `uuid` major 14, `nodemailer` patch 8.0.7.
    - `express@5` und `uuid@14` sind potenziell verhaltensrelevant und sollen nicht ohne gezielten Testplan geaendert werden.

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
| H-01 | /ice-servers public TURN credentials | Braucht Client-Aenderung (WS-only delivery nach REGISTER) |
| H-09 | Certificate Pinning nicht implementiert | Feature-Entscheidung: implementieren oder Claim herabstufen |

### Codex Re-Verify Status

- `cf30743` (H-07 stripe_handler + email_handler Maskierung): re-verifiziert, Code-/Email-Logging deutlich verbessert; weiter nur Log-Hygiene beobachten.
- `c7c7e06` (H-04 invite Rate Limit): PARTIAL; Rate Limit verifiziert, aber kein Invite-Token/Auth-Nachweis sichtbar.
- `1b39f9b` (H-08 JSON Injection Fix): VERIFIED_FIXED; Custom-ID Submission nutzt `JSONObject`.
- `281320f` (M-01 PKD Auth): VERIFIED_FIXED; `PUT /key/:id` und `DELETE /key/:id` hinter `requireAdmin`.
- `0b64d09` (L-01/L-02 Text-Drift): VERIFIED_FIXED fuer FAQ/OG-Image; weitere Lizenz-/Release-Doku-Drift wurde spaeter separat nachverfolgt und teilweise bereinigt.

## Priority 2

- [x] README-/Download-Statusdrift pruefen:
  - Play Store Beta, APK und Website-Links sollen auf denselben aktuellen Stand zeigen.
  - Recheck 2026-05-04: README/Website/Wiki-Index/GitHub latest release stehen auf `v1.0.28` / `versionCode 50`.
  - Rest: Play-Console-Status extern; `UpdateChecker`-Tests fuer Assetnamen ohne `vC` fehlen weiter.
- [ ] Hybrid-Migration ausfuehren (siehe `MIGRATION_PLAN.md`)

## Priority 3

- [ ] ICE/TURN Endpoint `/ice-servers` hinter Auth (H-01 — nach Client-Update).
- [ ] Certificate Pinning implementieren oder Claim herabstufen (H-09).
- [ ] Privacy-/Metadaten-Claims gegen reale FCM/TURN/Signaling-Architektur bereinigen:
  - README/Privacy/FAQ vermeiden absolute Claims wie "No metadata", "No logs", "No personal data stored".
  - Klarstellen: keine Call-Inhalte/Recordings/serverseitige Entschluesselung; Signaling-Metadaten werden fuer Verbindung verarbeitet.
  - FCM Token Persistence, FCM Push-Metadaten, STUN/TURN/IP-Sichtbarkeit und Log-Hygiene ehrlich dokumentieren.
- [ ] Backend-Monolith `backend/signaling/src/server.js` schrittweise modularisieren.
- [x] Privacy-Metadaten-Claims gegen FCM/TURN/Signaling Realitaet pruefen.
