# Claude Code Dev Handover

Datum: 2026-05-04

## Wichtig zuerst

Dieses Projekt nutzt ab sofort die Agent Bridge unter:

`docs/agent-bridge/`

Claude Code, Codex und der Nutzer kommunizieren hier ueber Markdown-Dateien. Jeder Agent soll vor Beginn einer Aufgabe zuerst die Bridge lesen und nach Abschluss die Bridge aktualisieren.

## Pflicht-Workflow fuer Claude Code

Vor jeder Arbeit:

1. `docs/agent-bridge/README.md` lesen.
2. `docs/agent-bridge/PROJECT_STATE.md` lesen.
3. `docs/agent-bridge/TODO.md` lesen.
4. `docs/agent-bridge/ACTION_LOG.md` lesen.
5. Falls vorhanden: weitere Handover-Dateien in `docs/agent-bridge/` lesen.
6. Lokale Audit-Dateien unter `AUDIT_MUST_READ/` lesen, sofern sie lokal vorhanden sind.

Nach jeder Arbeit:

1. `docs/agent-bridge/ACTION_LOG.md` aktualisieren.
2. `docs/agent-bridge/PROJECT_STATE.md` aktualisieren, falls sich der Projektstand geaendert hat.
3. `docs/agent-bridge/TODO.md` aktualisieren, falls Aufgaben erledigt, verschoben oder neu erkannt wurden.
4. Risiken, offene Fragen und bewusst nicht erledigte Punkte klar dokumentieren.

## Sicherheitsregeln

- Keine `.env`-Dateien lesen oder ausgeben.
- Keine `.env.*`-Dateien lesen oder ausgeben.
- Keine `.gitignore` lesen oder aendern.
- Keine Key-, Keystore-, Wallet-, Dump- oder Secret-Dateien lesen oder ausgeben.
- Keine Secrets in Logs, Reports, Commits oder Chat ausgeben.
- Produktcode, Deployment, Commit oder Push nur mit Nutzerfreigabe.
- Play Store, APK und F-Droid sind rolloutkritisch; Aenderungen sollen klein, kompatibel und bewusst bleiben.

## Was Codex bereits gemacht hat

Codex hat im Stealth-Projekt folgende Punkte bearbeitet und in der Bridge dokumentiert:

- Lokales Audit unter `AUDIT_MUST_READ/` gelesen.
- Lizenz-/Messaging-Konflikt analysiert:
  - Client-Code bleibt GPL-3.0/open/auditierbar.
  - Offizielle SecureCall/StealthX-Marke, Backend-Services, Store-Releases und paid Pro/Premium-Lizenzierung bleiben bei Vendetta Labs.
- Repo-Dokumentation leicht korrigiert und gepusht:
  - Commit `5d56f1e` `docs: clarify GPL client and official service terms`
- Website-Lizenztexte korrigiert, gepusht und GitHub Pages Deploy verifiziert:
  - Commit `6ecf7f9` `docs: align website license messaging`
  - Live geprueft auf `https://stealthx.tech/` und `https://stealthx.tech/privacy.html`
- Stealth Agent Bridge angelegt:
  - `docs/agent-bridge/README.md`
  - `docs/agent-bridge/ACTION_LOG.md`
  - `docs/agent-bridge/PROJECT_STATE.md`
  - `docs/agent-bridge/TODO.md`
- Bridge-Status zur Website veroeffentlicht:
  - Commit `10a5f9f` `docs(bridge): record stealth website deploy`
- Dependabot/Security Alerts analysiert und risikoarme Lockfile-Fixes umgesetzt:
  - Commit `da9b1bb` `fix(deps): clear high priority dependabot alerts`
  - Commit `14655b6` `docs(bridge): record dependabot follow-up status`
- Verbleibende Dependabot-Themen als Abstimmungspunkt dokumentiert:
  - Commit `1a66d8d` `docs(bridge): flag remaining dependabot fixes for coordination`

## Aktueller Dependabot-Stand

GitHub meldet nach Codex-Fixes noch 2 offene Alerts:

- `medium`: `uuid < 14.0.0` in `backend/signaling/package-lock.json`
- `low`: `@tootallnate/once < 3.0.1` in `backend/signaling/package-lock.json`

Bereits behoben:

- `critical`: `protobufjs < 7.5.5`
- `high`: `path-to-regexp < 0.1.13`
- `medium`: `fast-xml-parser < 5.7.0`
- `low`: `rand >= 0.7.0, < 0.8.6`

## Warum die restlichen Alerts Abstimmung brauchen

`uuid`:

- Ist direkt in `backend/signaling/package.json` als `uuid@^9.0.1` eingetragen.
- Wird in `backend/signaling/src/sessions.js` und `backend/signaling/src/server.js` per CommonJS `require("uuid")` und `v4` genutzt.
- GitHub verlangt `uuid@14`, also ein Major-Upgrade mit moeglicher API-/Module-System-Auswirkung.
- Nicht blind aktualisieren.

`@tootallnate/once`:

- Kommt transitiv ueber Firebase/Google-Cloud-Storage-Pfade:
  `firebase-admin -> @google-cloud/storage/google-gax -> retry-request -> teeny-request -> http-proxy-agent`
- Kein direkter App-Code-Verbrauch erkannt.
- Fix vermutlich nur ueber Upstream-Paketupdates oder Dependency-Override moeglich.
- Override nur nach Testplan und Abstimmung.

## Empfohlener naechster Schritt fuer Claude Code

Bitte zuerst die Bridge und diesen Handover lesen. Danach:

1. Einen konkreten Testplan fuer `backend/signaling` erstellen.
2. Pruefen, ob `uuid` ohne Risiko durch Node `crypto.randomUUID()` ersetzt werden kann.
3. Alternativ kontrolliertes `uuid`-Major-Upgrade pruefen.
4. `@tootallnate/once` nur ueber Upstream-Update oder gut begruendeten Override anfassen.
5. Keine `npm audit fix --force` Aktion ohne ausdrueckliche Nutzerfreigabe.
6. Ergebnis in `ACTION_LOG.md` und `TODO.md` dokumentieren.

## Prompt fuer Claude Code

Du arbeitest im lokalen Repository:

`/Users/gio/Desktop/repo/stealth`

Lies zuerst die Agent Bridge unter:

`docs/agent-bridge/`

Pflichtdateien:

1. `docs/agent-bridge/README.md`
2. `docs/agent-bridge/PROJECT_STATE.md`
3. `docs/agent-bridge/TODO.md`
4. `docs/agent-bridge/ACTION_LOG.md`
5. `docs/agent-bridge/CC_DEV_HANDOVER.md`

Falls lokal vorhanden, lies auch:

`AUDIT_MUST_READ/`

Aufgabe:

Analysiere die zwei verbleibenden Dependabot Alerts im Stealth Signaling Backend:

- `uuid < 14.0.0`
- `@tootallnate/once < 3.0.1`

Arbeite vorsichtig. Keine `.env`, `.env.*`, `.gitignore`, Secret-, Key-, Keystore-, Wallet- oder Dump-Dateien lesen. Keine Secrets ausgeben.

Wichtig:

- Produktcode nur aendern, wenn der Nutzer die konkrete Umsetzung freigibt.
- Keine `npm audit fix --force`.
- Keine grossen Rollout-risikoreichen Updates ohne Testplan.
- Play Store/APK/F-Droid Rollout nicht gefaehrden.
- Dokumentiere jede Analyse und jede Aktion in `docs/agent-bridge/ACTION_LOG.md`.
- Aktualisiere `docs/agent-bridge/TODO.md`, falls sich Prioritaeten oder offene Punkte aendern.

Erwartetes Ergebnis:

- Kurze technische Bewertung, ob `uuid` besser auf `crypto.randomUUID()` migriert oder per kontrolliertem Major-Upgrade geloest werden sollte.
- Bewertung, ob `@tootallnate/once` ueber Upstream-Updates oder Override geloest werden sollte.
- Konkreter Testplan fuer Sessions, Connections, GhostNet IDs, Firebase/FCM/Storage-Pfade und Mail/Resend-Flows.
- Keine nicht abgestimmten funktionsabhaengigen Aenderungen.
