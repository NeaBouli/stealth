# SecureCall Ecosystem – Changelog

Dieses Dokument listet **alle Änderungen**, Features, Fixes und sicherheitsrelevanten Updates
an der SecureCall/Stealth Plattform auf.

Format orientiert sich an "Keep a Changelog" + sicherheitskritischen Erweiterungen.

---

## [1.0.22] — 2026-04-17 — vC43

### Fixed
- **REGISTERED-gated registration** — client only proceeds after server ACK
- **Close code 4003 stop** — unauthorized client signature rejects cleanly
- **Subscription resync** — inconsistent tier state auto-corrected
- **HIGH-002/005** — signaling server hardening
- **Custom-ID token validation** — proper JWT verification
- **Subscription verify endpoint** — server-side state check
- **SECUREID_CHANGED hardened** — atomic JSON writes prevent race conditions

### Security
- **Origin-less WS clients allowed** — native apps without browser Origin header
- **CORS allowlist hardened** — signaling server restricts origins
- **Stripe idempotency** — duplicate webhook calls safe
- **Admin key unified** — single source of truth
- **PII redacted from release logs** — LOGGING_LEVEL tightened

### Verified
- Samsung S10 (Premium), S7 (Free), Tab S4 (Free)

### Release Artifacts
- `securecall-v1.0.22-free.apk` — Free tier (Firebase/FCM)
- `securecall-v1.0.22-premium.apk` — Premium tier (internal)
- `securecall-v1.0.22-fdroid.apk` — F-Droid edition (no proprietary libs)
- APK SHA-256 signing cert: `1e0a8eb4...c88c3fdb21d`

---

## [1.0.17] — 2026-04-12 — vC38

### Fixed
- **Play Console Compliance**: `REQUEST_INSTALL_PACKAGES` Permission entfernt — keine sensitive-permission Review/Video-Erklärung mehr nötig
- In-App Updater öffnet jetzt den Browser mit dem APK-Download-Link statt direkt zu installieren

### Removed
- `UpdateInstaller.kt` (Signature-Check + FileProvider Install Intent — nicht mehr nötig)
- `UpdateDownloader.kt` (DownloadManager Wrapper — Browser/System übernimmt)

### Security
- Pro/Premium Gradle Flavors hinter `-Pinternal` Flag geschützt — können nicht mehr versehentlich gebaut werden

---

## [1.0.16] — 2026-04-11 — vC37

### Added
- **In-App Updater** für Sideload/APK-User:
  - Automatischer GitHub Releases API Check (1× pro 24h, silent wenn up-to-date)
  - Update-Dialog mit Version, Changelog, Größe
  - Download + SHA-256 Signing-Cert Verification + FileProvider Install
  - Install-Source Routing: Play Store → market://, F-Droid → fdroid.app://, Sideload → in-app flow
- `UpdateChecker.kt` — OkHttp GitHub API Client mit vC-Regex Parser
- `UpdateDownloader.kt` — DownloadManager Wrapper
- `UpdateInstaller.kt` — APK Signature Verification gegen installierte App
- `UpdateInfo.kt` — Data Class für Release-Metadaten
- Auto-Check in `MainActivity.onCreate()` (throttled, nur Sideload)
- Key Log-Events auf `Log.w` promoted für Release-Observability

---

## [1.0.15] — 2026-04-10 — vC36

### Fixed
- **F-010 CRITICAL**: WalletConnect Production-Crash komplett behoben
  - ALLE 5 public methods in `WalletConnectManager.kt` mit `catch(Throwable)` gewrapped
  - `connect()` und `verifyAndUnlock()` waren in vC35 noch ungewrapped
  - 6 von 6 catch-Blöcke jetzt Throwable (vorher: 4 Exception + 2 NoClassDefFoundError)
  - Root Cause: android-core:1.26.0 vs sign:2.26.0 Version-Mismatch → ClassNotFoundException + NoSuchMethodError

---

## [1.0.14] — 2026-04-10 — vC35

### Fixed
- **F-010**: `WalletConnectManager.init()` catch-Block von `NoClassDefFoundError` auf `Throwable` erweitert
  - Crashlytics: 4 Abstürze / 2 Nutzer auf vC34 (ClassNotFoundException: PushClient)
  - `SecureCallApplication.kt` outer catch ebenfalls → `catch(Throwable)`
- **F-009**: `CallActivity.onCreate()` → `setShowWhenLocked(true)` + `setTurnScreenOn(true)`
  - Gap: IncomingCallActivity hatte die Flags, CallActivity nicht → Call-UI verschwand hinter Lock-Screen nach Accept
  - Fallback für Android < 8.1: `FLAG_SHOW_WHEN_LOCKED | FLAG_TURN_SCREEN_ON | FLAG_DISMISS_KEYGUARD`

---

## [1.0.13] — 2026-04-09 — vC34

### Fixed
- F-Droid Build-Konfiguration: Summary in `en-US/summary.txt` (make-summary-translatable)
- F-Droid `rewritemeta`: curl line wrap + printf trailing space
- F-Droid `rewritemeta`: exakte Feld-Reihenfolge (ndk nach scandelete)

### Known Issues (gefunden in Test-Session 2026-04-10)
- F-001: Phonebook leer trotz 35 Einträgen (UX — nur SecureCall-registrierte gezeigt)
- F-002: Komplett-Reset nach Uninstall (SharedPreferences-Datenverlust)
- F-003: SECUREID_CHANGED updated 0 contacts (replaceSecureId matched nicht per phone)
- F-004: S7 hatte 2 Apps parallel (pro + free) — Test-Setup Issue
- F-005: Log Stripping in Release verhindert Diagnose
- F-006: PRO-Feature Toggles geben kein Feedback auf FREE
- F-007: Save Call History Row hinter BottomNav verdeckt auf S7
- F-008: Kontakt-Duplikat nach Verify (Phonebook vs internal Name)
- F-009: Lock-Screen Call-UI nicht sichtbar nach Accept → **gefixt in vC35**
- F-010: WalletConnectManager.init Crashlytics Production-Crash → **gefixt in vC35/vC36**
- F-011: Security Warnings: Samsung Diktiergerät (false positive)
- F-012: DEBUG LoggingLevel in FREE BuildConfig (Inkonsistenz mit ProGuard)

---

## [1.0.12] — 2026-04-02 — vC29

### Fixed
- 5 Bug-Fixes aus Test-Session 2026-04-01/02
- Call verified S10→S7 audio OK

---

## [Unreleased]

### Added
- Projektstruktur angelegt
- Architektur-Dokumentation
- Security-Design Dokument
- Developer Roadmap
- Entwicklerhandbuch
- CI-Linting Workflow

### Changed
- README Konflikt bereinigt und zusammengeführt

### Security
- Initiale Sicherheitsrichtlinien definiert

---
