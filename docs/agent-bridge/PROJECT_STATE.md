# Stealth Project State

## 2026-07-11 23:59 EEST — CODEX — CUSTOM-ID PAYMENT P0 / CRYPTO SUPPORT

- Custom-ID checkout is now fail-closed behind `CUSTOM_ID_STRIPE_CHECKOUT_ENABLED=true`. Direct activation cannot mint an unpaid ID, and a pending token alone cannot activate one.
- Google Play one-time verification now fails closed without service-account verification, accepts only exact package/product allowlists and reuses an existing code for duplicate purchase tokens. The old substring-tier and development accept-without-verification paths are removed.
- Google Publisher verification no longer imports the undeclared `googleapis` package; it uses directly declared `google-auth-library` credentials and an encoded Android Publisher REST request. Fresh `npm ci` reports 0 vulnerabilities.
- Stripe paid webhook must bind the pending token, normalized Custom ID and exact Checkout Session before activation; unpaid, mismatched and leaked pending tokens fail.
- Direct ETH/BTC/SOL support is explicitly described as voluntary, without purchase/feature access or implied tax-exempt donation status. Recipient/accounting treatment remains a Gio/accountant gate.
- Codex owns this payment path. No Stripe request, crypto transfer, invoice, AADE request or deploy was executed.
- Verification: full signaling suite PASS; Android `:app:processFreeDebugResources` PASS with the repository's required AndroidX flag; `git diff --check` PASS. Changes belong to PR #33.



## 2026-07-11 — Ed25519-Entitlement-Vertrag lokal fertig

- StealthX Signaling kann geräte- und produktgebundene 30-Tage-Entitlements fuer SecureChat, Chameleon, Suite und SecureCall ausstellen.
- Token signiert nur mit Runtime-Private-Key; Order-ID erscheint nur gehasht. Ohne Key kein Token.
- SecureChat-Verifier ist separat implementiert und fail-closed. Chameleon-Gegenstelle folgt ueber denselben Vertrag.
- Externes Gate: Runtime-Keypair/Public-Key-Buildkonfiguration und Testmode-E2E vor jeder Freischaltung.

## 2026-07-11 — Payment-P0 auf `origin/main` implementiert

- Der aktuelle Remote-Stand besitzt nun die zuvor nur lokal vorbereitete signierte VLABS-Fulfillment-/Revocation-Grenze.
- Checkout-Bypass geschlossen: Legacy-/Dynamic-Checkout und IFR-Discount-Challenge sind default-off; Webhook ist fail-closed und paid-only.
- Vollrefund und Stripe-Dispute widerrufen die Aktivierung idempotent; Payment-Auditlogs sind redigiert.
- Vollstaendiger Signaling-Testlauf PASS und npm Audit 0. Kein Production-Deploy oder Live-Checkout.

## 2026-07-11 — Payment-/Etimologio-Ownership und VLABS-Verkaufsvorbereitung

- Repository Owner: Codex ist durch Gio fuer das gesamte oeffentliche SecureCall/StealthX-Repository verantwortlich, einschliesslich Produktcode, Security-Integration, Tests, Website, Payment und Etimologio/myDATA.
- Andere Devs arbeiten nur nach Codex-Handover oder als Reviewer; keine parallele Implementierung.
- VLABS fuehrt die private steuerliche Source of Truth; dieses Public Repo erhaelt keine Steuer-ID, Provider-ID, Stripe-Secrets, Kundendaten oder AADE-Credentials.
- VLABS-Shopwaren sind lokal vorbereitet: SecureCall Pro/Premium sowie StealthX Suite mit serverkontrollierten Produkt-IDs/Preisen, Privat-/Firmenauswahl und AFM/VAT-Erfassung vor Stripe.
- SecureCall besitzt lokal einen signierten, session-idempotenten Aktivierungs-Consumer und einen separaten Vollrefund-Revoke-Pfad. Legacy-Direktcheckouts sind standardmaessig deaktiviert, damit VLABS der kanonische Verkaufseinstieg wird.
- Rechtshinweise auf den Produktseiten wurden auf VLABS-Preise, digitale Leistung, sofortige Lieferung, Widerruf und gesetzliche Rechte angepasst.
- Release-Gate bleibt geschlossen: kein Live-Checkout/Deploy, bis Runtime-Secrets, End-to-End-Test mit Stripe-Testmodus, Accountant-Produktmapping und Gio-Launch-Freigabe vorliegen. Etimologio-Provider ist noch nicht produktiv aktiv.
- Kein Secret gelesen/geschrieben, keine Zahlung, keine Rechnung, kein Provider-/AADE-Request und kein Deploy bei dieser Bridge-Aktualisierung.

## 2026-05-09 (Abend) — AKTUELLER STAND — CC Autonomous Session

### Git HEAD
- Branch: `main`
- HEAD: `fe8bd63` — "docs: update BRIDGE.md and TODO.md for v1.0.33 release"
- Remote: `origin/main` in sync

### Aktuelle Version
- **versionCode:** 55
- **versionName:** 1.0.33
- **Flavor:** free
- **AAB fuer Play Console:** `~/Desktop/SecureCall-v1.0.33-vC55-FINAL.aab` (36 MB)
- **GitHub Release:** https://github.com/NeaBouli/stealth/releases/tag/v1.0.33

### Geraetestand (Stand 16:12 Uhr)
- S7 / ce10160adc00152604 (SM-G930F): v1.0.33-free, vC55001 ✓
- Tab S4 / ce12182c68644439037e (SM-T835): v1.0.33-free, vC55001 ✓
- (S9 laut Gio am Rechner — war zum Zeitpunkt der ADB-Checks nicht per ADB verbunden)

### Backend (Railway)
- **Service:** `protective-healing` im Projekt `disciplined-flexibility`
- **URL:** `protective-healing-production.up.railway.app`
- **HEAD live:** `c6965e8` (nodemailer 8.0.7 + BRIDGE update)
- **FORK_PROTECTION_MODE:** `warn` (Railway env var)
- **DATA_DIR:** auto-fallback /tmp/stealthx-data wenn /app/data nicht schreibbar
- **npm audit:** 0 critical, 0 high, 0 moderate, 8 low (alle firebase-admin transitiv — kein Fix ohne Major-Downgrade)

### Sicherheits-Status
| ID | Finding | Status | Commit |
|----|---------|--------|--------|
| H-01 | /ice-servers Auth | DONE | HTTP hinter requireAdmin, WS REGISTERED-Delivery |
| H-09 | Certificate Pinning | DONE | `5949617` — network_security_config.xml |
| Privacy Claims | "Zero metadata" | DONE | `5949617` — "Minimal metadata" |

**Cert-Pin-Details:**
- Domain: `protective-healing-production.up.railway.app`
- Primary: LE E7 intermediate SPKI `y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU=` (gültig bis 2027-03-12)
- Backup: ISRG Root X1 SPKI `C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=`
- ⚠️ PIN ROTATION erforderlich vor 2027-03-12

### Offene Tasks (nach Priorität)
1. **Play Console:** AAB `~/Desktop/SecureCall-v1.0.33-vC55-FINAL.aab` hochladen (Gio)
2. **BUG-029 Retest:** eingehender Call bei aktivem StealthX-VPN auf S7/Tab S4 — Audio prüfen (Gio)
3. **Langzeittest:** 20-30 Min gesperrt → eingehender Call (Gio)
4. **BUG-026:** eSIM-Routing via VpnService-Architektur (Codex-Task — verfügbar in ~14h)
5. **Hybrid-Migration Hetzner:** MIGRATION_PLAN.md — erwartet Gio-Entscheidung zu den 5 Fragen am Ende des Plans
6. **Backend-Modularisierung:** BACKEND_MODULARIZATION.md — Plan liegt vor, Post-Release, Priority 3
7. **Play-Tester Retest:** FORK_PROTECTION_MODE=warn seit ef28d46 live

### Codex
- **Verfügbar ab:** ca. 06:00-07:00 Uhr morgen früh (14h vom jetzigen Zeitpunkt)
- **Nächster Codex-Task (vorbereitet in BRIDGE.md):** BUG-026 VpnService-Architektur-Analyse

### Rust core_crypto
- `cargo test --locked`: 6/6 PASS
- Deps aktuell: zerocopy 0.8.48, libc 0.2.186, typenum 1.20.0

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

## 2026-05-09 — Rechnerwechsel-Handover / v1.0.32 Play-Tester-Disconnect

- HEAD vor Rechnerwechsel:
  - `bb9c719` auf `main` / `origin/main`
- Aktuelle Play-Console-AAB:
  - `/Users/gio/Desktop/SecureCall-FINAL-UPLOAD.aab`
  - package: `com.securecall.app.free`
  - versionCode: `54002`
  - versionName: `1.0.32-free`
  - Manifest enthaelt `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  - Gio/CC meldeten: v1.0.32/vC54 in Play Console hochgeladen.
- Produktionsproblem:
  - Externe Play-Tester disconnected nach Play-Update.
  - Lokal per Gradle/ADB installierte Geraete konnten connecten.
  - Hauptursache laut Codex: Backend-Forkschutz gegen `ALLOWED_SIGNATURES` blockt Google-Play-signierte APKs bei `FORK_PROTECTION_MODE=enforce`.
- Umgesetzte Commits:
  - `9a7e1f9`: Fork Protection Default `enforce` -> `warn`.
  - `ed1d176`: Dockerfile kopiert `data/` ins Railway-Image.
  - `4b3f783`: Release-Bump `v1.0.32` / `vC54`.
  - `bb9c719`: Bridge/TODO Session State.
- Noch noetig (OFFEN bei Rechnerwechsel):
  - Railway CLI auf neuem Rechner einloggen/verknuepfen.
  - Railway env pruefen: `FORK_PROTECTION_MODE` entfernen oder auf `warn` setzen.
  - Railway redeploy/restart, damit Fork-Protection-Fix und Dockerfile-Fix live sind.
  - Logs pruefen und Play-Tester Retest ausloesen.
- Wichtig:
  - `ALLOWED_SIGNATURES` mit lokalem Upload-Key kann bleiben, solange `FORK_PROTECTION_MODE=warn` aktiv ist.
  - Fuer spaeteren sicheren Enforce-Modus muss Google Play App Signing SHA-256 in `ALLOWED_SIGNATURES` aufgenommen werden.

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
