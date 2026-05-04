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
- **Railway:** ALLOWED_SIGNATURES live, enforce aktiv, Server HTTP 200 (deployed 2026-05-04).
- CC + Codex arbeiten koordiniert ueber diese Bridge.

## 2026-05-04 — Security Audit Phase 1 abgeschlossen

- CC + Codex haben vollstaendigen Code-Audit durchgefuehrt (Backend + Android Client).
- 15 Findings identifiziert (3 Critical, 9 High, 3 Medium/Low).
- 13/15 Findings gefixt und gepusht.
- 2 verbleibend: H-01 (/ice-servers Auth — Client-Aenderung) + H-09 (Cert Pinning — Feature-Entscheidung).
- Phase 3 MIGRATION_PLAN.md erstellt (Hybrid self-hosted Architektur).
- Codex Re-Verify steht aus fuer letzte 5 Commits.

## 2026-05-04 — Codex Nachpruefung F-Droid/Lizenz-Kohaerenz

- Codex hat nach der F-Droid-Entfernung lokal read-only auf Restreferenzen geprueft.
- Ergebnis: F-Droid ist im Build-System als Product Flavor entfernt, aber es gibt noch Restreferenzen in Marketing-/Store-Texten, Website/LLM-Text, Fastlane-Metadaten, Payment-E-Mail und Android-Kommentaren.
- Lokale leere/verwaiste Ordner existieren noch:
  - `client_android/app/src/fdroid/`
  - `fdroid/`
- Lizenz-Kollision erkannt: `LICENSE` ist BUSL-1.1, aber README/CONTRIBUTING/Website nennen teils noch GPL-3.0.
- Diese Punkte sind in `TODO.md` als naechste Bereinigung dokumentiert.

## Grenzen

- Keine `.env`, `.env.*`, `.gitignore`, Secret-, Key-, Keystore-, Wallet- oder Dump-Dateien gelesen.
- Lokaler `AUDIT_MUST_READ/`-Ordner bleibt untracked/lokal.
