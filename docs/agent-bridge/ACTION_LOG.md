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
