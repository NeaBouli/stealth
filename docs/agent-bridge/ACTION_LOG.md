# Stealth Action Log

## 2026-05-03 - Codex: Lizenz-/Rollout-Text leicht korrigiert

- Agent: Codex
- Nutzerziel: Play Store/APK/F-Droid-Rollout nicht gefaehrden, aber Lizenz-/Branding-Widerspruch bereinigen.
- Aktion:
  - `README.md`, `CONTRIBUTING.md`, `docs/REPO_SECURITY.md` angepasst.
  - Commit `5d56f1e` auf `main` gepusht.
- Ergebnis:
  - GPL-3.0 fuer Client-Code bleibt stabil.
  - Offizielle SecureCall/StealthX-Marke, Backend-Services, Store-Releases und paid Pro/Premium-Lizenzierung sind klar als kontrolliert durch Vendetta Labs beschrieben.
- GitHub-Hinweis beim Push:
  - 6 Dependabot/Security-Warnungen gemeldet: 1 critical, 1 high, 2 moderate, 2 low.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.

## 2026-05-03 - Codex: Website-Textangleichung und Stealth-Bridge angelegt

- Agent: Codex
- Aktion:
  - Stealth Bridge unter `docs/agent-bridge/` angelegt.
  - Dependabot/Security-Warnungen in `TODO.md` aufgenommen.
  - Website-Texte lokal leicht angeglichen: `source-available`/unklare Open-Source-Claims werden auf `GPL-3.0 client source + official services controlled` gefuehrt.
- Geaenderte Dateien:
  - `website/index.html`
  - `website/privacy.html`
  - `website/terms.html`
  - `website/wiki/security-design.html`
  - `website/llms.txt`
  - `website/assets/og-image.svg`
  - `docs/agent-bridge/README.md`
  - `docs/agent-bridge/PROJECT_STATE.md`
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Secrets gelesen oder ausgegeben.
- Noch kein Commit/Push/Deployment fuer diesen zweiten Schritt.

## 2026-05-03 - Codex: Website-Lizenztexte live veroeffentlicht

- Agent: Codex
- Aktion:
  - Commit `6ecf7f9` auf `main` gepusht.
  - GitHub Pages Workflow `Deploy to GitHub Pages` lief erfolgreich.
- Live-Verifikation:
  - `https://stealthx.tech/` liefert neue Texte wie `GPL-3.0 client source`.
  - `https://stealthx.tech/privacy.html` liefert `GPL Client Source` und den Hinweis, dass offizielle Marke, Backend-Services, Store-Releases und paid Pro/Premium-Lizenzierung von Vendetta Labs betrieben werden.
- Ergebnis:
  - Website ist mit GitHub-Repo-Lizenzkommunikation konsistenter.
  - Play/APK/F-Droid-Rollout bleibt unveraendert.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment; GitHub Pages Auto-Deploy durch Workflow.

## 2026-05-03 - Codex: Dependabot Alerts ausgelesen

- Agent: Codex
- Aktion: GitHub Dependabot Alerts fuer `NeaBouli/stealth` read-only per GitHub API ausgelesen.
- Offene Alerts:
  - Critical: `protobufjs < 7.5.5`
  - High: `path-to-regexp < 0.1.13`
  - Medium: `uuid < 14.0.0`
  - Medium: `fast-xml-parser < 5.7.0`
  - Low: `rand >= 0.7.0, < 0.8.6`
  - Low: `@tootallnate/once < 3.0.1`
- Betroffene Manifeste:
  - `backend/signaling/package-lock.json`
  - `core_crypto/Cargo.lock`
- Keine Secrets gelesen oder ausgegeben.
- Noch keine Dependency-Fixes committed.

## 2026-05-03 - Codex: Dependabot High/Critical Fix lokal vorbereitet

- Agent: Codex
- Aktion:
  - `backend/signaling/package-lock.json` mit `npm update --package-lock-only` aktualisiert.
  - `core_crypto/Cargo.lock` mit `cargo update -p rand --precise 0.8.6` aktualisiert.
- Lokal behobene bekannte Alerts:
  - Critical: `protobufjs < 7.5.5` -> lockfile jetzt `protobufjs 7.5.6`.
  - High: `path-to-regexp < 0.1.13` -> lockfile jetzt `path-to-regexp 0.1.13`.
  - Medium: `fast-xml-parser < 5.7.0` -> lockfile jetzt `fast-xml-parser 5.7.2`.
  - Low: `rand >= 0.7.0, < 0.8.6` -> lockfile jetzt `rand 0.8.6`.
- Checks:
  - `node --check src/server.js` erfolgreich.
  - `npm audit --audit-level=high` erfolgreich; keine high/critical npm-Audit-Treffer mehr.
  - `cargo test --locked` erfolgreich; 34 Tests passed.
- Bewusst nicht automatisch gefixt:
  - Moderate/low transitive npm-Audit-Treffer, fuer die `npm audit fix --force` Breaking Changes/Downgrades vorschlaegt.
- Keine Secrets gelesen oder ausgegeben.
- Noch kein Commit/Push fuer diesen Dependency-Fix.

## 2026-05-04 - Codex: Dependabot High/Critical Fix gepusht und verifiziert

- Agent: Codex
- Aktion:
  - Commit `da9b1bb` auf `main` gepusht.
  - GitHub Dependabot Alerts danach read-only per GitHub API erneut abgefragt.
- Geaenderte Dateien:
  - `backend/signaling/package-lock.json`
  - `core_crypto/Cargo.lock`
  - `docs/agent-bridge/ACTION_LOG.md`
  - `docs/agent-bridge/TODO.md`
- Ergebnis laut GitHub API nach Push:
  - Behoben: `protobufjs < 7.5.5` (critical).
  - Behoben: `path-to-regexp < 0.1.13` (high).
  - Behoben: `fast-xml-parser < 5.7.0` (medium).
  - Behoben: `rand >= 0.7.0, < 0.8.6` (low).
  - Weiterhin offen: `uuid < 14.0.0` (medium).
  - Weiterhin offen: `@tootallnate/once < 3.0.1` (low).
- Checks:
  - `node --check src/server.js` erfolgreich.
  - `npm audit --audit-level=high` erfolgreich.
  - `cargo test --locked` erfolgreich; 34 Tests passed.
- Hinweis:
  - Weitere npm-Audit-Fixes nicht erzwungen, weil `npm audit fix --force` Breaking Changes/Downgrades fuer rolloutkritische FCM-/Mail-/Backend-Flows vorschlaegt.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment.

## 2026-05-04 - Codex: Restliche Dependabot Alerts analysiert

- Agent: Codex
- Aktion:
  - Dependency-Tree fuer `uuid` und `@tootallnate/once` im Signaling-Backend analysiert.
  - Direkte Nutzung von `uuid` im Backend-Code geprueft.
  - Keine Produktcodeaenderung vorgenommen.
- Gelesene Dateien/Befehle:
  - `backend/signaling/package.json`
  - `backend/signaling/src/sessions.js`
  - `backend/signaling/src/server.js`
  - `npm ls uuid @tootallnate/once`
  - `npm explain uuid`
  - `npm explain @tootallnate/once`
  - `npm outdated`
- Ergebnis:
  - `uuid` ist direkt als `uuid@^9.0.1` eingebunden und wird in CommonJS-Code mit `require("uuid")`/`v4` genutzt.
  - Der GitHub-Alert verlangt `uuid@14`, also ein Major-Upgrade mit moeglicher API-/Module-System-Auswirkung.
  - `@tootallnate/once` kommt transitiv ueber Google/Firebase-Storage-Pfade und wird nicht direkt vom App-Code genutzt.
  - `npm outdated` nennt ausserdem `express@5` als Major-Upgrade und `nodemailer@8.0.7` als Patch.
- Entscheidung:
  - Keine funktionsabhaengigen Aenderungen ohne Abstimmung mit Claude Code/Dev.
  - Restliche Alerts bleiben als gezielter Abstimmungspunkt in `TODO.md`.
- Keine Secrets gelesen oder ausgegeben.
- Kein Commit/Push/Deployment fuer Produktcode.

## 2026-05-04 - CC: F-Droid/GPL Restdrift bereinigt (Codex-Gegenpruefung)

- Agent: Claude Code
- Ausloeser: Codex fand nach Phase-3-Cleanup noch F-Droid-Reste und GPL-3.0 Texte.
- CC-Gegenpruefung: Alle Funde bestaetigt und in einem Commit behoben (`8064dbd`).
- Geaenderte Dateien (15):
  - README.md, CONTRIBUTING.md — GPL → BUSL-1.1
  - website/index.html, terms.html, faq.html, wiki/security-design.html, llms.txt — GPL → BUSL-1.1, F-Droid entfernt
  - marketing/play_store/de/store_listing.md — F-Droid Zeile entfernt
  - docs/PLAY_STORE_LISTING.md, PLAY_STORE_LISTING_DE.md — F-Droid entfernt
  - backend/signaling/src/payments/email_handler.js — F-Droid Link aus Mail entfernt
  - fastlane/metadata/android/en-US/full_description.txt — F-Droid Edition → Free Edition
  - tools/debug/start-logcat.sh — fdroid Package-ID entfernt
  - client_android: MainActivity.java, UpdateChecker.kt — Kommentare korrigiert
- Bewusst belassen: Historische Session-/Handover-/Bug-Docs (Archiv-Charakter).
- Keine funktionalen Aenderungen. Nur Text/Kommentare.
- Keine Secrets gelesen oder ausgegeben.

## 2026-05-04 - CC: BUSL-1.1 Lizenz + Fork Protection enforce

- Agent: Claude Code
- Aktionen:
  1. LICENSE ersetzt: GPL-3.0 → BUSL-1.1 (Commit `10a2ca4`)
     - Change Date: 2030-05-04
     - Change License: GPL-3.0-only
     - Commercial use restricted
  2. SHA-256 Fingerprint ermittelt via apksigner:
     - `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
  3. FORK_PROTECTION_MODE Default geaendert: "warn" → "enforce" (Commit `b9202cb`)
  4. docs/LIZENZ_VORSCHLAG.md entfernt (war nur Entwurf)
- Railway-Konfiguration (MUSS MANUELL GESETZT WERDEN):
  - `ALLOWED_SIGNATURES=1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
  - `FORK_PROTECTION_MODE=enforce` (oder weglassen, ist jetzt Default)
- Codex-Info:
  - Lizenz ist jetzt BUSL-1.1, NICHT mehr GPL-3.0.
  - Fork-Schutz ist enforce by default. Ohne ALLOWED_SIGNATURES env var passiert nichts (Code prueft `if (allowedSigs && allowedSigs.trim().length > 0)`).
  - Railway Redeploy noetig damit beides live wirkt.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment (Railway manuell).

## 2026-05-04 - CC: F-Droid komplett entfernt (3 Phasen)

- Agent: Claude Code
- Aktionen:
  Phase 1 (Commit `e59f966`):
  - GitLab MR !37087 geschlossen via API
  - docs/FDROID_SETUP.md + docs/FDROID_SUBMISSION.md geloescht
  - F-Droid Button aus website/index.html entfernt
  - README, RELEASE_PROCESS, GITHUB_RELEASES, privacy.html bereinigt
  Phase 2 (Commit `58ec3c0`):
  - GitLab Fork TrueRepublic/securecall-fdroid (81115682) geloescht via API
  - Alle fdroid APK Assets aus GitHub Releases v1.0.17-v1.0.28 entfernt
  - fdroid/ Metadata-Ordner aus Repo entfernt
  Phase 3 (Commits `5c877c2` + `82b67dc`):
  - fdroid productFlavor aus build.gradle entfernt
  - fdroid Source Set (11 Dateien + proguard) entfernt
  - AndroidManifest fdroid-Package-Queries entfernt
  - UpdateManager: FDROID InstallSource + openFDroid() entfernt
  - CallActivity: Kommentar korrigiert
  - BUILD SUCCESSFUL: free, pro, premium (alle 3 OK)
- Codex-Info:
  - F-Droid existiert NICHT mehr im Projekt.
  - Nur noch 3 Flavors: `free` (public), `pro` + `premium` (internal mit -Pinternal).
  - UpdateManager hat nur noch: PLAY_STORE, SIDELOAD, OTHER_STORE.
  - BUSL-1.1 Lizenz-Entwurf liegt in `docs/LIZENZ_VORSCHLAG.md` (nicht committet).
  - ALLOWED_SIGNATURES wird NACH Lizenz-Entscheidung reaktiviert.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment.

## 2026-05-04 - CC: uuid-Migration + BUSL-1.1 Entwurf

- Agent: Claude Code
- Aktionen:
  1. uuid-Migration umgesetzt:
     - `uuid` Paket aus package.json entfernt.
     - `require("uuid")` durch `crypto.randomUUID()` ersetzt in server.js + sessions.js.
     - Lockfile regeneriert via `npm ci`.
     - Syntax-Check bestanden.
     - Commit `d13e8f4` auf main gepusht.
     - GitHub Dependabot: uuid-Alert sollte sich aufloesen (direkte Dep entfernt).
  2. Lizenz-Entwurf erstellt:
     - `docs/LIZENZ_VORSCHLAG.md` — BUSL-1.1 angepasst fuer StealthX/SecureCall.
     - Change Date: 2030-05-04, Change License: GPL-3.0.
     - NICHT committet — wartet auf Nutzer-Review und Entscheidungen.
  3. Bridge aktualisiert fuer Codex-Sichtbarkeit.
- Codex-Info:
  - `uuid` ist NICHT mehr direkte Dependency. `crypto.randomUUID()` ist die neue API.
  - Falls Codex am Backend arbeitet: `uuidv4()` existiert nicht mehr, `crypto.randomUUID()` verwenden.
  - Lizenz-Diskussion laeuft. LICENSE-Datei noch NICHT aendern bis Gio entscheidet.
- Keine Secrets gelesen oder ausgegeben.
- Kein Server-Deployment (Railway muss separat redeployed werden fuer uuid-Fix).

## 2026-05-04 - CC: Rollback-Tag + uuid-Analyse + Koordinationsprotokoll

- Agent: Claude Code
- Aktionen:
  1. Rollback-Tag `rollback-stable-vC50` auf HEAD `d24fbc7` erstellt und nach origin gepusht.
  2. uuid-Nutzung im Backend analysiert:
     - 4 Stellen: server.js (Z.4, 596, 818, 1187) + sessions.js (Z.11)
     - Nur `uuidv4()` Aufrufe, triviales Drop-in fuer `crypto.randomUUID()`
     - Node >= 18 ist Engine-Requirement → `crypto.randomUUID()` immer verfuegbar
  3. Empfehlung: `uuid` Paket entfernen, durch native Node-API ersetzen → Alert geloest, keine Dep mehr.
  4. `@tootallnate/once`: Monitoring empfohlen, kein Handlungsbedarf (transitiv, low severity).
  5. Diskussionspunkte in TODO.md dokumentiert: ALLOWED_SIGNATURES + Lizenz-Entscheidung.
  6. Koordinationsprotokoll: CC liest/updated Bridge ab sofort bei jeder Stealth-Arbeit.
- Keine Produktcode-Aenderung.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Claude Code Dev Handover erstellt

- Agent: Codex
- Aktion:
  - Handover und Prompt fuer Claude Code Dev erstellt.
  - Bridge-Kommunikationsregeln explizit dokumentiert.
  - README-Dateiliste um Handover-Datei erweitert.
- Geaenderte Dateien:
  - `docs/agent-bridge/CC_DEV_HANDOVER.md`
  - `docs/agent-bridge/README.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Inhalt:
  - Claude Code soll vor jeder Arbeit die Bridge lesen.
  - Claude Code soll nach jeder Arbeit `ACTION_LOG.md`, `PROJECT_STATE.md` und `TODO.md` aktualisieren, falls betroffen.
  - Restliche Dependabot-Themen `uuid` und `@tootallnate/once` sind als vorsichtige Abstimmungspunkte beschrieben.
- Keine Produktcodeaenderung.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: Security Audit gestartet und Findings an CC uebergeben

- Agent: Codex
- Aktion:
  - Bridge gelesen.
  - CC/Codex Autonomie- und Kommunikationsprotokoll in `README.md` ergaenzt.
  - `CODEX_FINDINGS.md` angelegt.
  - Security Audit 1 gestartet und erste Findings priorisiert dokumentiert.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/README.md`
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Hinweise:
  - Keine Secret-Werte in Findings aufgenommen.
  - CC soll Env-/Token-/Key-Status nur als Ja/Nein/Risiko bewerten, keine Werte ausgeben.
  - `docs/agent-bridge/CC_RESPONSE.md` existiert lokal als CC-Datei und wurde nicht von Codex ueberschrieben.
- Keine Produktcodeaenderung.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: CC-Fixplan beantwortet

- Agent: Codex
- Aktion:
  - Aktuelle `CC_RESPONSE.md` gelesen.
  - In `CODEX_FINDINGS.md` direkte Antwort an CC ergaenzt.
  - Gruenes Licht fuer eindeutige Sofort-Fixes gegeben.
  - Critical/High Findings nach Codex-Sicht priorisiert und Env-Fragen ohne Secret-Ausgabe formuliert.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/CODEX_FINDINGS.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung.
- Keine Secrets ausgegeben.
- Kein Deployment.

## 2026-05-04 - Codex: F-Droid-/Lizenz-Kohaerenz nachgeprueft

- Agent: Codex
- Aktion:
  - Nach F-Droid-Entfernung lokal read-only auf Restreferenzen, Kollisionen und Inkohärenzen geprueft.
  - Keine Produktcodeaenderung vorgenommen.
- Gelesene Bereiche:
  - Android Build/Source-Struktur unter `client_android/app/`
  - Website-/Marketing-/Docs-/Fastlane-Texte
  - Payment-E-Mail-Template
  - Bridge-Dateien
- Findings:
  - F-Droid Product Flavor ist aus `client_android/app/build.gradle` entfernt; nur `free`, `pro`, `premium` sind sichtbar.
  - Dennoch existieren noch F-Droid-Resttexte in Marketing/Docs/Website/Fastlane/Payment-E-Mail.
  - Android-Code enthaelt noch F-Droid-Kommentare bzw. Legacy-Branch fuer `FDROID`-Tier.
  - Lokale/verwaiste Ordner `client_android/app/src/fdroid/` und `fdroid/` existieren noch.
  - Lizenz-Kollision: `LICENSE` ist BUSL-1.1, aber mehrere Texte nennen noch GPL-3.0 client source.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/PROJECT_STATE.md`
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Secrets gelesen oder ausgegeben.
- Kein Commit/Push/Deployment.

## 2026-05-04 - Codex: CC-Gegenpruefung fuer F-Droid-/Lizenz-Drift angefordert

- Agent: Codex
- Aktion:
  - Nutzerauftrag umgesetzt: Claude Code soll die Codex-Fundliste zur F-Droid-/Lizenz-Restdrift gegenpruefen.
  - `TODO.md` um expliziten CC-Gegenpruefungsauftrag ergaenzt.
- CC soll pruefen:
  - Ob alle genannten F-Droid-Reste real und noch relevant sind.
  - Ob verwaiste Ordner gefahrlos entfernbar sind.
  - Welche GPL-3.0-Texte auf BUSL-1.1/source-available angepasst werden muessen.
  - Welche historischen Eintraege bewusst unveraendert bleiben sollen.
  - Welche Aenderungen rein textlich sind und welche Build/Funktion beeinflussen koennten.
- Geaenderte Bridge-Dateien:
  - `docs/agent-bridge/TODO.md`
  - `docs/agent-bridge/ACTION_LOG.md`
- Keine Produktcodeaenderung.
- Keine Secrets gelesen oder ausgegeben.
- Kein Deployment.
