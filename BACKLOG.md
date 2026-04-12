# stealth — Backlog

## 🔴 Aktiv (diese Session)
<!-- Was gerade in Arbeit ist -->

## 🟡 Nächste Session
<!-- Nächste konkrete Tasks -->
- **TODO-088** F-002 Backup-Mechanismus (Datenverlust nach Uninstall)
  - SecureID + Kontakte + Settings gehen bei Uninstall verloren (SharedPreferences)
  - Option A: Cloud-Backup (encrypted unter SecureID)
  - Option B: Lokale Datei `/sdcard/SecureCall/backup.json`
  - Option C: Deterministische SecureID-Ableitung per HMAC(phone_number)
- **TODO-089** F-006 PRO-Feature Toggles: Feedback bei Tap auf FREE
  - Aktuell stumm — kein Toast, kein Dialog bei Tap auf gesperrte Features
  - Dialog "Diese Funktion ist nur in PRO verfügbar. Jetzt upgraden?" einbauen
- **TODO-090** F-007 Settings RecyclerView paddingBottom für BottomNav
  - "Save Call History" Zeile wird teilweise vom BottomNav verdeckt auf S7 (1440x2560)
  - Fix: `recycler_view.setPadding(0, 0, 0, bottomNavHeight)` + `clipToPadding=false`
- **TODO-091** F-008 Kontakt-Duplikat nach Verify
  - Phonebook-Name vs SecureCall-interner Name nicht synchronisiert nach Verify
- **TODO-092** F-012 LOGGING_LEVEL Inkonsistenz FREE-Tier
  - BuildConfig.LOGGING_LEVEL="DEBUG" aber Log.d wird von ProGuard gestrippt
  - FREE-Release auf "INFO" oder "WARN" setzen
- **TODO-093** Samsung Diktiergerät in Security-Warning Whitelist
  - Falsches Positiv: Standard-App löst "Call recording app installed" Warning aus

## 🟢 Irgendwann / Ideas
<!-- Ideen ohne Deadline -->
- In-App Updater: "Skip this version" Button im Update-Dialog
- In-App Updater: WiFi-only Option in Settings
- In-App Updater: Delta-Download (nur diff laden)
- Play Core In-App Update API für Play-Store-User (native UX)
- F-Droid: firebase_crashlytics/analytics meta-data Einträge aus Manifest entfernen (harmlos aber F-Droid Scanner meckert evtl.)

## ✅ Erledigt
<!-- Session 2026-04-10 / 2026-04-11 / 2026-04-12 -->

### Test-Session vC34 (2026-04-10)
- [x] **TODO-075** Phase 1: App-Lifecycle + Core Calling Tests (T001-T033)
  - Cold-Start, Restart, Kontakte-View, Wake, Bidirektionaler Call S7↔Tab S4
  - Audio OK, Mute/Speaker/Hangup funktionieren
  - 5 Findings dokumentiert (F-001 bis F-005)
- [x] **TODO-076** Phase 2: Permissions Check (T041-T047)
  - RECORD_AUDIO/READ_CONTACTS/READ_PHONE_STATE granted auf S7+Tab S4
  - READ_PHONE_NUMBERS nicht runtime-requested (unkritisch)
  - POST_NOTIFICATIONS nur API 33+ (beide Geräte darunter)
- [x] **TODO-077** Phase 3: UI Navigation (T036-T039)
  - BottomNav: Anrufe/Kontakte/Dialer/Einstellungen alle erreichbar
  - Back-Stack funktioniert
- [x] **TODO-078** Phase 4: Settings — alle 11 Kategorien getestet (T061-T072)
  - Konto, Sicherheit, Anti-Recording, Calls, Darstellung, Network, VPN,
    Über, Support Development, Diagnostics, IFR Token Unlock
  - Background Service Toggle AN→AUS→AN ✓
  - PRO-only Features korrekt als gesperrt angezeigt
  - F-006 gefunden: kein Feedback bei Tap auf PRO-locked Toggles
  - F-007 gefunden: Save Call History Row hinter BottomNav verdeckt auf S7
- [x] **TODO-079** Phase 5: Dialer + manueller SecureID-Call (T100-T107)
  - ABC-Toggle funktioniert, SecureID-Eingabe über Keyboard
  - Call via SecureID `android-d620db02` → Klingelt → Accept → Audio OK
  - Warning Dialog: Kaspersky (com.kms.free) + Samsung Diktiergerät erkannt
  - Post-Call Verify Dialog funktioniert
- [x] **TODO-080** Phase 6: Background / Lock / Notification (T400-T412)
  - FullScreenIntent triggert IncomingCallActivity auf Lock-Screen ✓
  - **F-009 gefunden**: CallActivity nach Accept NICHT sichtbar über Lock-Screen
  - Audio fließt trotzdem (Call aktiv), aber Mute/Speaker/Hangup nicht bedienbar
- [x] **TODO-081** Phase 7: Long-press Contact Menu (T300-T303)
  - Verified Contact: "Verified ✓", Block, Delete, Cancel
  - Unverified Contact: "✅ Verify Contact", Block, Delete, Cancel
  - Verify via Post-Call Dialog auf S7 ✓
- [x] **TODO-082** Phase 8: Call History Persistence (T500-T502)
  - 5 Einträge auf S7, korrekte Dauer-Formatierung (M:SS)
  - History überlebt App-Restart

### Bug-Fixes vC35 + vC36 (2026-04-10)
- [x] **TODO-083** F-010 FIX: WalletConnect Crashlytics Production-Crash
  - **Commit `968ecf0` (vC35)**: `catch(NoClassDefFoundError)` → `catch(Throwable)` in init()
  - **Commit `69dd7c6` (vC36)**: ALLE 5 public methods gewrapped (init, connect, getConnectedWallet, disconnect, verifyAndUnlock)
  - Alle 6 catch-Blöcke jetzt `Throwable` — fängt ClassNotFoundException, NoClassDefFoundError, NoSuchMethodError, LinkageError
  - SecureCallApplication.kt outer catch ebenfalls → `catch(Throwable)`
  - Root Cause: Version-Mismatch android-core:1.26.0 vs sign:2.26.0
  - Crashlytics: 4 Abstürze / 2 Nutzer auf vC34
- [x] **TODO-084** F-009 FIX: Lock-Screen Call UI
  - **Commit `968ecf0` (vC35)**: CallActivity.onCreate() → `setShowWhenLocked(true)` + `setTurnScreenOn(true)` (API 27+) + FLAG fallback
  - IncomingCallActivity hatte die Flags bereits — Gap war die Transition zu CallActivity
  - Verifiziert auf Tab S4 (Android 10) + S10 (Android 12): CallActivity über Lock-Screen sichtbar, Mute/Speaker/Hangup bedienbar

### Test-Session vC36 auf 3 Geräten (2026-04-10 Nacht)
- [x] **TODO-085** vC36 Volltest auf S7 + Tab S4 + S10
  - vC36 auf alle 3 Geräte installiert (S10 clean install wegen Signatur-Mismatch)
  - **F-010 verifiziert**: Alle 3 Geräte loggen `E WalletConnect: init failed (non-fatal)` — 0 FATAL
  - **F-009 verifiziert**: Tab S4 (locked + Doze) + S10 (locked + Doze) → Accept → CallActivity über Lock-Screen ✓
  - S10 OnboardingActivity durchlaufen, Phone-Confirm, SecureID generiert
  - Bidirektional: S7→S10 ✓, S10→S7 ✓, S7→Tab S4 (locked) ✓, S7→S10 (locked) ✓
  - Audio MODE_IN_COMMUNICATION auf allen Endpunkten bestätigt
  - Mute/Speaker/Hangup über Lock-Screen bedienbar ✓
  - Connect Wallet Tap (S10, 4×rapid) → kein Crash, PID stabil ✓
  - Call History: 3 Einträge auf S10, 8 auf S7 (inkl. vC34 persistent über Update)
  - WalletConnect init graceful auf Android 8/10/12 ✓

### Website Stripe Integration (2026-04-10/11)
- [x] **TODO-086** Stripe Payment Links in Landing Page
  - **Commit `5f1262b`**: Pro Monthly, Premium Monthly → "Buy Direct" Button neben Google Play
  - Stripe Test-Links integriert, "Pay with Card or PayPal" Hinweis
  - Google Play Buttons beibehalten + umbenannt zu "Buy on Google Play"
  - `success.html` erstellt (Danke-Seite mit Aktivierungs-Instruktionen)
  - Premium Lifetime "Buy Direct" wieder entfernt (c6685f6) — Dynamic Checkout bleibt
  - Weitere Stripe-Commits (bd0955a, a1fa710, 731fc45): Full activation flow, Live Mode, Custom ID prices

### In-App Updater (2026-04-11)
- [x] **TODO-087** In-App Updater für Sideload-User
  - **Commit `fa7a845` (vC37)**: Vollständiger In-App Updater
    - `UpdateChecker.kt`: OkHttp → GitHub Releases API, vC-Regex Parser, Version-Vergleich
    - `UpdateDownloader.kt`: DownloadManager Wrapper mit BroadcastReceiver
    - `UpdateInstaller.kt`: SHA-256 Signing-Cert Verification + FileProvider Install Intent
    - `UpdateManager.kt`: Routing (Play Store / F-Droid / Sideload), Dialog, Auto-Check
    - Auto-Check auf MainActivity.onCreate (24h Throttle, silent wenn up-to-date)
    - Install-Source Erkennung: `getInstallerPackageName()` → PLAY_STORE / FDROID / SIDELOAD
  - **Commit `e58162e`**: Log.d → Log.w für Release-Observability
    - Verifiziert auf S10 + S7: `W UpdateManager: Manual check — install source: SIDELOAD`
    - `W UpdateChecker: Already up-to-date (installed=37, latest=37)` — 700ms round-trip
  - **Commit `19322f6` (vC38)**: REQUEST_INSTALL_PACKAGES entfernt (Play Console Compliance)
    - UpdateInstaller.kt + UpdateDownloader.kt gelöscht (-440 LOC)
    - Update-Dialog öffnet jetzt Browser mit APK-URL statt Direkt-Install
    - aapt dump permissions: REQUEST_INSTALL_PACKAGES count = 0 ✓
    - Verifiziert auf S7 (Android 8): PID stabil, Permission weg

### Build-Sicherheit (2026-04-12)
- [x] **TODO-094** Guard für Pro/Premium Gradle Flavors
  - **Commit `1faa742`**: `variantFilter` blockt pro/premium ohne `-Pinternal`
  - `assembleProRelease` → "Task not found" ✗
  - `assemblePremiumRelease` → "Task not found" ✗
  - `assembleFreeRelease` → BUILD SUCCESSFUL ✓
  - `assembleFdroidRelease` → BUILD SUCCESSFUL ✓
  - `-Pinternal assembleProDebug` → baut (expliziter Opt-in)
  - Verifiziert: Kein Pro/Premium APK/AAB auf GitHub Releases (alle 8 Releases geprüft)

### F-Droid Distribution (2026-04-10/11/12)
- [x] **TODO-095** F-Droid Metadata + GitLab Pipeline Updates
  - vC34→vC36: Commit `07a1a2f`, Tag `v1.0.15-fdroid`, Pipeline 2445207923
  - vC36→vC37: Commit + Tag `v1.0.16-fdroid`, Pipeline 2445833241
  - vC37→vC38: Commit `a97c4d8`, Tag `v1.0.17-fdroid`, Pipeline 2446424966
  - Alle Pipelines via GitLab API getriggert (TrueRepublic/securecall-fdroid, Project 81115682)
  - fdroid APK gebaut + auf GitHub Release hochgeladen (securecall-fdroid-v1.0.17-vC38.apk)

### Release-Builds (2026-04-10/11/12)
- [x] **TODO-096** Release-Artefakte für alle Versionen
  - vC35 (v1.0.14): `968ecf0` → AAB+APK → GitHub Release v1.0.14-stable
  - vC36 (v1.0.15): `69dd7c6` → AAB+APK → GitHub Release v1.0.15-stable
  - vC37 (v1.0.16): `fa7a845` → AAB+APK → GitHub Release v1.0.16-stable
  - vC38 (v1.0.17): `19322f6` → AAB+APK+fdroidAPK → GitHub Release v1.0.17-stable
  - Alle signiert mit `securecall-release-key.jks` (SHA-256 1E:0A:8E:B4:...)
  - Alle kopiert nach `~/Documents/SecureCall-Release/final/`
  - Aktuelle Artefakte auf `~/Desktop/` für Play Console Upload

---

### Ältere erledigte Tasks
- [x] **TODO-065** FLAG_SECURE konsistent — DONE (2026-04-07)
- [x] **TODO-066** FCM Push Notifications geprüft — DONE (2026-04-07)
- [x] **TODO-074** F-Droid APK Build — DONE (2026-04-07)

---
*Zuletzt aktualisiert: 2026-04-12*
