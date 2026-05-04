# Stealth TODO

## Priority 1

- [ ] F-Droid-/Lizenz-Restdrift nach Entfernung bereinigen:
  - **CC-Gegenpruefung erforderlich, bevor Fixes umgesetzt werden.**
  - Claude Code soll die Codex-Fundliste gegen Repo/Build/Website pruefen und bestaetigen oder korrigieren.
  - Ziel der Gegenpruefung:
    - Sind die genannten F-Droid-Reste real und noch relevant?
    - Sind die leeren/verwaisten Ordner wirklich gefahrlos entfernbar?
    - Welche GPL-3.0-Texte muessen auf BUSL-1.1/source-available angepasst werden?
    - Welche historischen Changelog-/Bug-/Audit-Eintraege sollen bewusst historisch bleiben?
    - Welche Aenderungen sind rein textlich und welche koennten Funktion/Build beeinflussen?
  - Codex-Nachpruefung am 2026-05-04 fand noch F-Droid-Reste trotz Bridge-Status "F-Droid komplett entfernt".
  - Echte F-Droid-Reststellen:
    - `marketing/play_store/de/store_listing.md`: "F-Droid — bald verfuegbar".
    - `docs/PLAY_STORE_LISTING.md` und `docs/PLAY_STORE_LISTING_DE.md`: F-Droid weiterhin als kommend genannt.
    - `website/llms.txt`: F-Droid planned.
    - `backend/signaling/src/payments/email_handler.js`: F-Droid Download-Link in Aktivierungs-Mail.
    - `fastlane/metadata/android/en-US/full_description.txt`: "F-Droid Edition".
    - `fastlane/metadata/android/en-US/changelogs/42.txt`: "Initial F-Droid release".
    - `tools/debug/start-logcat.sh`: sucht noch `com.securecall.app.fdroid`.
    - Android-Code-Kommentare/Legacy-Zweige in `MainActivity.java`, `UpdateChecker.kt`, `WindowSecurityHelper.kt`.
  - Lokale/verwaiste Ordner:
    - `client_android/app/src/fdroid/` existiert noch als leerer Source-Set-Pfad.
    - `fdroid/` existiert noch lokal mit leeren Unterordnern `srclibs/` und `tmp/`.
  - Lizenz-Kollision:
    - `LICENSE` ist BUSL-1.1.
    - `README.md`, `CONTRIBUTING.md`, `website/index.html`, `website/terms.html`, `website/wiki/security-design.html`, `website/llms.txt`, `website/assets/og-image.svg` nennen teils weiter GPL-3.0 client source.
  - Empfehlung:
    - Kleine Textbereinigung in Docs/Website/Fastlane/Marketing/Email-Template.
    - Android-Kommentare/Legacy-FDROID-Zweig bereinigen, ohne Verhalten fuer `free/pro/premium` zu aendern.
    - Leere/verwaiste lokale F-Droid-Ordner nach Freigabe entfernen.
    - Danach erneut `rg -i "f-droid|fdroid|GPL-3.0|GPL client"` laufen lassen und Build/Smoke-Check fuer `free`, `pro`, `premium` pruefen.

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
      - Direkt im Projekt als `uuid@^9.0.1` eingetragen.
      - Im CommonJS-Code genutzt: `src/sessions.js` und `src/server.js` verwenden `require("uuid")` und `v4`.
      - Alert-Ziel `uuid@14` ist ein Major-Upgrade und darf nicht blind angewendet werden.
      - Moegliche Optionen: Code auf Node `crypto.randomUUID()` umstellen, sauber auf neues `uuid`-API/Major migrieren, oder Upstream-Abhaengigkeiten beobachten/aktualisieren.
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

## Priority 2

- [ ] README-/Download-Statusdrift pruefen:
  - Play Store Beta, APK, F-Droid und Website-Links sollen auf denselben aktuellen Stand zeigen.

## Priority 3

- [ ] ICE/TURN Endpoint `/ice-servers` separat sicherheitsauditieren.
- [ ] Backend-Monolith `backend/signaling/src/server.js` schrittweise modularisieren.
- [ ] Privacy-Metadaten-Claims gegen FCM/TURN/Signaling Realitaet pruefen.
