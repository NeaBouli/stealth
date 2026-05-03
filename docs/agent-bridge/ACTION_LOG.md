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
