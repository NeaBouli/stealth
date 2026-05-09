# Stealth Project State

## 2026-05-09 — Neustart-Resume nach Play-Console-Free-Geraeteangleichung

- Resume-Dateien:
  - Global: `/Users/gio/.codex/memories/stealth_resume_2026-05-09.md`
  - Projekt: `docs/agent-bridge/RESUME_NEXT_SESSION.md`
- Aktuelle Upload-AAB:
  - `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
  - package: `com.securecall.app.free`
  - versionCode: `53002`
  - versionName: `1.0.31-free`
  - SHA-256: `6d0ee1b70efea5f7470544d0d5ba184b027025175c407597e3635ea0e3749433`
  - Manifest enthaelt `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  - `bundletool validate`: PASS
- Geraetestand:
  - S10, S7 und Tab S4 haben nur noch `com.securecall.app.free` installiert.
  - Alle drei Geraete laufen auf `versionCode=53002`, `versionName=1.0.31-free`.
  - Alte Test-Flavors entfernt:
    - S10: `com.securecall.app.premium` deinstalliert.
    - S7: `com.securecall.app.pro` deinstalliert.
  - `com.securecall.app.free` ist auf allen drei Geraeten in der DeviceIdle-User-Whitelist.
  - `WebSocketService` war auf allen drei Geraeten als Foreground-Service verifiziert.
- Naechster Schritt:
  - Nach Rechnerneustart zuerst ADB-/Git-Status pruefen.
  - Dann 20-30-Minuten-Lockscreen-Langzeittest mit eingehendem Call starten.

## 2026-05-09 — UEBERHOLT: Resume-Stand nach Battery-Optimization-Recheck

Hinweis: Dieser Abschnitt ist durch den neueren Abschnitt
`2026-05-09 — Neustart-Resume nach Play-Console-Free-Geraeteangleichung`
ersetzt. Fuer Neustart/Fortsetzung gilt der neuere Stand mit
`com.securecall.app.free`, versionCode `53002`, versionName `1.0.31-free`
auf allen drei Geraeten.

- Codex-Resume-Dateien:
  - Global: `/Users/gio/.codex/memories/stealth_resume_2026-05-09.md`
  - Projekt: `docs/agent-bridge/RESUME_NEXT_SESSION.md`
- Aktuelle Upload-AAB:
  - `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
  - package: `com.securecall.app.free`
  - versionCode: `52002`
  - versionName: `1.0.30-free`
  - SHA-256: `39f09af7475209e3b2ead6ca9bce48c74a51e5f3a8161f0c1abde37aa9699f38`
  - Manifest enthaelt `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  - `bundletool validate`: PASS
- Q2 Battery Optimization:
  - Manifest-Permission geloest.
  - Dialog-Stacking im Allow-Pfad geloest.
  - WakeLock-Refresh geloest: non-reference-counted, 30-Min-Timeout, Refresh in `WebSocketService.onStartCommand()`.
  - Settings-Warnung bleibt UX-Verbesserung, kein harter Upload-Blocker.
- Letzte Checks:
  - `git diff --check`: PASS
  - `./gradlew :app:testFreeDebugUnitTest`: PASS
  - `./gradlew :app:bundleFreeRelease`: PASS
  - `bundletool validate`: PASS
- Naechster sinnvoller Schritt:
  - Play Console Upload der aktuellen AAB.
  - Danach realer 20-30-Minuten-Geraetetest mit gesperrtem Bildschirm und eingehendem Call.

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

## 2026-05-05 — v1.0.29 (vC51) Released

- HEAD: `13fa8f0` (main).
- **Release:** v1.0.29 auf GitHub: https://github.com/NeaBouli/stealth/releases/tag/v1.0.29
- **Artefakte:** 3 APKs (arm64/armv7/x86_64) + 1 AAB
- **Security Audit:** 17/17 Findings VERIFIED_FIXED (CC self-audit, Codex-Tokens aufgebraucht)
- **Master Audit:** STX-CRIT-01 (nicht kritisch), STX-HIGH-01/02 (done), STX-MED-01/02/03/04 (done)
- **Offen:** STX-HIGH-03 (Backend Modularisierung) — Plan liegt vor, Post-Release
- **Railway:** Redeployed 2026-05-05 07:09 UTC. Health OK. Fork-Schutz blockt Bot (157.245.103.245).
- **Play Console:** AAB bereit: `app-free-release.aab` (38 MB) — lokal + GitHub Release
- **Geraete:** S7 (ce101...) + Tab S4 (ce121...) auf v1.0.29 updated via ADB

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
