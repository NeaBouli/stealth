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

## 2026-05-04 — Lizenz + Fork-Schutz + F-Droid entfernt + uuid migriert

- Rollback-Tag: `rollback-stable-vC50` auf Commit `d24fbc7`.
- HEAD jetzt: `b9202cb` (main).
- **Lizenz:** BUSL-1.1 (Change Date 2030-05-04, dann GPL-3.0).
- **Fork-Schutz:** Code auf enforce-Default. Railway braucht `ALLOWED_SIGNATURES` env var.
- **SHA-256:** `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
- F-Droid komplett entfernt: GitLab Fork, MR, GitHub Assets, Flavor, Source Set, Docs, Website.
- Nur noch 3 Flavors: `free`, `pro`, `premium`.
- uuid-Paket entfernt, durch `crypto.randomUUID()` ersetzt.
- **Naechster Schritt:** Railway Redeploy + ALLOWED_SIGNATURES setzen (manuell durch Gio).
- CC + Codex arbeiten koordiniert ueber diese Bridge.

## Grenzen

- Keine `.env`, `.env.*`, `.gitignore`, Secret-, Key-, Keystore-, Wallet- oder Dump-Dateien gelesen.
- Lokaler `AUDIT_MUST_READ/`-Ordner bleibt untracked/lokal.
