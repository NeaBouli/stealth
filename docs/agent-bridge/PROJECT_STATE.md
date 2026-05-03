# Stealth Project State

## 2026-05-03

- Projekt: Stealth / SecureCall / StealthX
- Lokaler Pfad: `/Users/gio/Desktop/repo/stealth`
- GitHub Remote: `https://github.com/NeaBouli/stealth.git`
- Website: `stealthx.tech`
- Rollout-Hinweis: Play Store, APK und F-Droid sind bereits im Verifizierungs-/Rollout-Prozess. Aenderungen sollen deshalb klein und kompatibel bleiben.
- Lizenzstand:
  - `LICENSE` bleibt GPL-3.0 fuer den Client-Code.
  - README/CONTRIBUTING/Repo-Security wurden leicht korrigiert: GPL-Client bleibt offen/auditierbar, offizielle SecureCall/StealthX-Marke, Backend-Services, Store-Releases und paid Pro/Premium-Lizenzierung bleiben bei Vendetta Labs.
- Website-Stand:
  - Website-Texte wurden in Richtung `GPL-3.0 client source + official services controlled` angepasst, gepusht und via GitHub Pages live veroeffentlicht.
  - Live verifiziert auf `https://stealthx.tech/` und `https://stealthx.tech/privacy.html`.
- Bekannte offene Security-Hinweise:
  - GitHub meldete beim Push 6 Dependabot/Security-Warnungen: 1 critical, 1 high, 2 moderate, 2 low.
  - Inhalte der Warnungen wurden noch nicht geprueft.

## Grenzen

- Keine `.env`, `.env.*`, `.gitignore`, Secret-, Key-, Keystore-, Wallet- oder Dump-Dateien gelesen.
- Lokaler `AUDIT_MUST_READ/`-Ordner bleibt untracked/lokal.
