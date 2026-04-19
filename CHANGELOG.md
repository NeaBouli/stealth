# SecureCall Ecosystem – Changelog

This document lists **all changes**, features, fixes and security-relevant updates
to the SecureCall/Stealth platform.

Format follows "Keep a Changelog" with security-critical extensions.

---

## [1.0.23] — 2026-04-19 — vC44

### Fixed
- **Speaker toggle**: UI state sync with AudioManager — button reflects actual state
- **AudioTrack.Builder**: deprecated stream-type constructor replaced with AudioAttributes (USAGE_VOICE_COMMUNICATION) in AudioPlayer, AudioPlaybackThread, GhostAudioPlayer
- **Zombie-Session**: client disconnect now cleans up sessions — peer gets CALL_END instead of hanging call screen
- **WS reconnect call-drop (Bug #1)**: session-cleanup guard prevents active calls from being killed on supersede

### Security
- **BETA-PRO0-2026 + BETA-PREM-2026 disabled**: blocklist before validation (TODO-047)
- **FCM Volume Permission (#16)**: chown before USER switch in Dockerfile — non-root user can write /app/data

### Verified
- Call-Tests 5/5 PASS (S10 Premium, S7 Free, Tab S4 Pro)
- Speaker ON/OFF verified on S10↔S7
- Tab S4: no earpiece (hardware limitation, speaker only output)
- Railway health: stable after all deploys

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
- **Play Console Compliance**: `REQUEST_INSTALL_PACKAGES` permission removed — no sensitive-permission review/video explanation needed anymore
- In-App Updater now opens the browser with the APK download link instead of installing directly

### Removed
- `UpdateInstaller.kt` (signature check + FileProvider install intent — no longer needed)
- `UpdateDownloader.kt` (DownloadManager wrapper — browser/system handles it)

### Security
- Pro/Premium Gradle Flavors protected behind `-Pinternal` flag — can no longer be built accidentally

---

## [1.0.16] — 2026-04-11 — vC37

### Added
- **In-App Updater** for sideload/APK users:
  - Automatic GitHub Releases API check (once per 24h, silent when up-to-date)
  - Update dialog with version, changelog, size
  - Download + SHA-256 signing cert verification + FileProvider install
  - Install source routing: Play Store → market://, F-Droid → fdroid.app://, Sideload → in-app flow
- `UpdateChecker.kt` — OkHttp GitHub API client with vC regex parser
- `UpdateDownloader.kt` — DownloadManager wrapper
- `UpdateInstaller.kt` — APK signature verification against installed app
- `UpdateInfo.kt` — Data class for release metadata
- Auto-check in `MainActivity.onCreate()` (throttled, sideload only)
- Key log events promoted to `Log.w` for release observability

---

## [1.0.15] — 2026-04-10 — vC36

### Fixed
- **F-010 CRITICAL**: WalletConnect production crash fully resolved
  - ALL 5 public methods in `WalletConnectManager.kt` wrapped with `catch(Throwable)`
  - `connect()` and `verifyAndUnlock()` were still unwrapped in vC35
  - 6 of 6 catch blocks now Throwable (previously: 4 Exception + 2 NoClassDefFoundError)
  - Root cause: android-core:1.26.0 vs sign:2.26.0 version mismatch → ClassNotFoundException + NoSuchMethodError

---

## [1.0.14] — 2026-04-10 — vC35

### Fixed
- **F-010**: `WalletConnectManager.init()` catch block extended from `NoClassDefFoundError` to `Throwable`
  - Crashlytics: 4 crashes / 2 users on vC34 (ClassNotFoundException: PushClient)
  - `SecureCallApplication.kt` outer catch also → `catch(Throwable)`
- **F-009**: `CallActivity.onCreate()` → `setShowWhenLocked(true)` + `setTurnScreenOn(true)`
  - Gap: IncomingCallActivity had the flags, CallActivity did not → call UI disappeared behind lock screen after accept
  - Fallback for Android < 8.1: `FLAG_SHOW_WHEN_LOCKED | FLAG_TURN_SCREEN_ON | FLAG_DISMISS_KEYGUARD`

---

## [1.0.13] — 2026-04-09 — vC34

### Fixed
- F-Droid build configuration: Summary in `en-US/summary.txt` (make-summary-translatable)
- F-Droid `rewritemeta`: curl line wrap + printf trailing space
- F-Droid `rewritemeta`: exact field order (ndk after scandelete)

### Known Issues (found in test session 2026-04-10)
- F-001: Phonebook empty despite 35 entries (UX — only SecureCall-registered contacts shown)
- F-002: Complete reset after uninstall (SharedPreferences data loss)
- F-003: SECUREID_CHANGED updated 0 contacts (replaceSecureId did not match by phone)
- F-004: S7 had 2 apps installed in parallel (pro + free) — test setup issue
- F-005: Log stripping in release prevents diagnosis
- F-006: PRO feature toggles give no feedback on FREE
- F-007: Save Call History row hidden behind BottomNav on S7
- F-008: Contact duplicate after verify (phonebook vs internal name)
- F-009: Lock screen call UI not visible after accept → **fixed in vC35**
- F-010: WalletConnectManager.init Crashlytics production crash → **fixed in vC35/vC36**
- F-011: Security warnings: Samsung voice recorder (false positive)
- F-012: DEBUG LoggingLevel in FREE BuildConfig (inconsistency with ProGuard)

---

## [1.0.12] — 2026-04-02 — vC29

### Fixed
- 5 bug fixes from test session 2026-04-01/02
- Call verified S10→S7 audio OK

---

## [Unreleased]

### Added
- Project structure created
- Architecture documentation
- Security design document
- Developer roadmap
- Developer handbook
- CI linting workflow

### Changed
- README conflict resolved and merged

### Security
- Initial security guidelines defined

---
