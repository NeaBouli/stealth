# SecureCall Bug Tracker

| ID | Description | Status | Severity | Fixed In |
|----|-------------|--------|----------|----------|
| BUG-001 | Online status dots inconsistent | FIXED | Medium | 9ef003c |
| BUG-002 | Save Contact disappears in <1s | FIXED | High | 5c4f9cd |
| BUG-003 | Contact deduplication broken | FIXED | High | c9c2bbd |
| BUG-004 | IFR wallet verify shows no token count | FIXED | High | c615a5b |
| BUG-005 | Block screenshots not working on all Activities/tiers | FIXED | Medium | 3597cc9 |
| BUG-006 | WireGuard VPN service never started after permission grant | FIXED | High | dedb2ab + 6990c78 |
| BUG-007 | Contacts cache empty after app restart — presence skipped | FIXED | Medium | 0681cc7 |
| BUG-008 | CallActivity crash: SecurityException on PhoneStateListener | FIXED | Critical | a90c7fc |
| BUG-009 | App disconnects on network switch (WiFi→Mobile→eSIM) — no auto-reconnect | FIXED | High | 4174031 |
| BUG-010 | Incoming calls fail when app closed/background — FCM not waking app reliably | OPEN | Critical | — |
| BUG-011 | Call drops immediately after connecting — WebRTC P2P unstable | OPEN | Critical | — |
| BUG-012 | AdMob banner appears during active call | FIXED | High | df52218 |
| BUG-013 | Saved contact after call shows phone number not phonebook name | OPEN | Medium | — |
| BUG-014 | All Settings sections expanded by default — should all be collapsed | FIXED | Low | df52218 |
| BUG-015 | Disconnect button next to connection status | FIXED | Medium | 073895c |
| BUG-016 | Label "Anonymous Network" → "Network" | FIXED | Low | df52218 |
| BUG-017 | "New Call" FAB hidden in non-dialer tabs | FIXED | Medium | df52218 |
| BUG-018 | "Report a Bug" opens bug-report.html | FIXED | Medium | df52218 |
| BUG-019 | "Check for Updates" → GitHub for sideload/F-Droid | FIXED | Medium | 285d89f |
| BUG-020 | IFR Token section last in Settings | FIXED | Low | df52218 |
| BUG-021 | Emergency Delete first in Settings | FIXED | Low | df52218 |
| BUG-022 | eSIM status refreshes on Settings visit | FIXED | Medium | c149200 |
| BUG-023 | No diagnostic log export — SecLog CSV export (Pro/Premium) | OPEN | Low | — |
| BUG-024 | Random disconnects on network change — auto-reconnect via NetworkCallback | FIXED | High | 4174031 |
| BUG-025 | Phone normalization — full E.164 normalization | FIXED | High | df52218 + 991e5af |
| BUG-026 | eSIM Call Routing + Preferred Network — OkHttp pool bypasses bindProcessToNetwork() | OPEN | High | — |
| BUG-029 | No audio after call connected — VPN+VPN blocks TURN UDP relay | OPEN | Critical | — |
| BUG-030 | Audio too quiet — MODE_IN_COMMUNICATION not set, volume not maxed | FIXED | High | — |
| BUG-031 | Contact not verified after call — no verification prompt | OPEN | Medium | — |

## Fix Details

### BUG-026: eSIM Call Routing + Preferred Network bypass (OPEN)
- **Symptom:** `bindProcessToNetwork()` and `socketFactory` do not reliably route OkHttp WebSocket traffic when both WiFi and Cellular are active simultaneously.
- **Root Cause:** OkHttp connection pooling reuses sockets created before the network binding. DNS resolution may also route through the default network.
- **Tested:** S10 Premium — binding applied in logs but `/proc/PID/net/tcp` shows WiFi IP on all connections.
- **Fix Required:** VpnService-based traffic steering (intercept at OS level, not app level).
- **Workaround:** Feature works when only one network is active (e.g. WiFi off → Mobile).
- **Status:** eSIM routing disabled in UI, Preferred Network shows honest disclaimer.

### BUG-001: Online status dots inconsistent (FIXED)
- Reduced STATUS_REFRESH_INTERVAL from 30s to 15s for faster dot transitions
- Previous fix (2c3c697) was reverted due to regression — it cleared cache on
  resume and WS disconnect, preventing dots from ever showing green
- New fix (9ef003c): single-line change, no cache clearing, no WS state clearing
- Verified: green→red within 15s on disconnect, red→green within 15s on reconnect

### BUG-002: Save Contact dialog disappears in <1s (FIXED)
- Proximity wake lock released BEFORE dialog caused Samsung screen state transitions
- Moved releaseProximitySensor() to dialog button handlers
- Added FLAG_KEEP_SCREEN_ON while dialog is visible
- Made isEnding and callback fields volatile for thread safety

### BUG-003: Contact deduplication broken (FIXED)
- ContactRepository.save() blindly appended without duplicate check
- Now checks by phoneOrId, secureId, and normalized phone before adding
- Post-call save dialog stores phone as primary key with SecureID as metadata
- Enables proper dedup with phone book contacts

### BUG-004: IFR wallet verify shows no token count (FIXED)
- Token balance was not persisted when verification returned "insufficient"
- Now stores balance even on failure so UI can display it
- Status shows "Wallet: 0x77e9...0A6e — 500 IFR held"
- Added specific error message for Ethereum RPC failures

### BUG-005: Block screenshots not working on all Activities/tiers (FIXED)
- SettingsActivity was missing FLAG_SECURE entirely
- Free tier had screenshot toggle disabled — now enabled as opt-in (default OFF)
- All 4 Activities now consistently apply FLAG_SECURE

### BUG-007: Contacts cache empty after app restart — presence skipped (OPEN)
- **Symptom:** After app force-stop or restart, `refreshOnlineStatus()` logs "no registered phones cached, skipping" repeatedly. Online dots never appear until a full BATCH_PHONE_LOOKUP completes (up to 5 minutes).
- **Root Cause:** `cachedRegisteredPhones` is a `companion object` volatile Set in `ContactsFragment.kt:47`. It is populated only by `finalizeResults()` after `BATCH_PHONE_LOOKUP`. On app restart, the companion object is re-initialized to `emptySet()`. Since `refreshOnlineStatus()` checks `cachedRegisteredPhones` and skips if empty, presence never works until the next batch lookup.
- **Evidence:** S7 Pro logcat 2026-03-19 11:22–11:23 — repeated "no registered phones cached, skipping" after app restart.
- **Fix (0681cc7):** Persisted `cachedRegisteredPhones` in SharedPreferences (`securecall_prefs`, key `cached_registered_phones`). On fragment creation (`onViewCreated`), loads from SharedPreferences via `loadPersistedRegisteredPhones()`. On `finalizeResults()`, saves to SharedPreferences via `persistRegisteredPhones()`. Presence now works immediately after app restart without waiting for batch lookup.

### BUG-008: CallActivity crash — SecurityException on PhoneStateListener (FIXED)
- **Symptom:** App crashes with FATAL `java.lang.SecurityException: listen` when accepting or making a call. CallActivity force-finishes immediately.
- **Stack Trace:**
  ```
  FATAL EXCEPTION: main
  java.lang.SecurityException: listen
    at CallActivity.startPhoneStateMonitor(CallActivity.java:682)
    at CallActivity.lambda$startTransportAndTimer$16(CallActivity.java:519)
  Caused by: android.os.RemoteException
    at TelephonyPermissions.checkReadPhoneState
  ```
- **Root Cause:** `CallActivity.java:682` — `telephonyManager.listen(phoneStateListener, LISTEN_CALL_STATE)` requires `READ_PHONE_STATE` permission on Android 12+. The app doesn't request this permission at runtime. The phone state monitor is a non-critical feature (pauses SecureCall audio during incoming cell calls).
- **Evidence:** S10 Premium (Android 12, SM-G973F) crash at 2026-03-19 21:10:21, PID 27780.
- **Fix:** Wrapped `telephonyManager.listen()` in try-catch for `SecurityException`. If permission is missing, phone state monitoring is gracefully skipped — calls work normally without it.
- **Verified:** Fully automated call test 2026-03-19 21:49–21:50. S7→S10 call initiated via ADB tap, accepted via uiautomator-detected Accept button (742,1807), 8s active call, ended via EndCall button (539,1807). Complete CALL_INVITE→ACCEPT→END cycle with no crash. SecurityException caught gracefully ("Phone state monitor skipped").

### BUG-009: App disconnects on network switch (WiFi→Mobile→eSIM) — no auto-reconnect (OPEN)
- **Severity:** High
- **Symptom:** When switching between WiFi, Mobile, or eSIM networks, the app loses connection and does not automatically reconnect.
- **Expected:** ConnectivityManager NetworkCallback should detect network change and trigger WebSocket reconnect.

### BUG-010: Incoming calls fail when app closed/background — FCM not waking app reliably (OPEN)
- **Severity:** Critical
- **Symptom:** Incoming calls fail to ring when the app is closed or in the background. FCM does not reliably wake the app.

### BUG-011: Call drops immediately after connecting — WebRTC P2P unstable (OPEN)
- **Severity:** Critical
- **Symptom:** After call connects, it drops within seconds. WebRTC peer-to-peer connection is unstable.

### BUG-012: AdMob banner appears during active call (OPEN)
- **Severity:** High
- **Symptom:** Ad banner is visible during an active call in CallActivity. Ads should be paused/hidden during calls.

### BUG-013: Saved contact after call shows phone number not phonebook name — no phonebook sync (OPEN)
- **Severity:** Medium
- **Symptom:** After a call ends and contact is saved, the contact list shows the raw phone number instead of the name from the Android phone book.

### BUG-014: All Settings sections expanded by default — should all be collapsed (OPEN)
- **Severity:** Low
- **Symptom:** When opening Settings, all PreferenceCategory sections are expanded. They should default to collapsed state with tap-to-toggle.

### BUG-015: No disconnect button next to connection status (OPEN)
- **Severity:** Medium
- **Symptom:** There is no button to manually disconnect/reconnect next to the connection status indicator in the toolbar.

### BUG-016: Label "Anonymous Network" should be "Network" (OPEN)
- **Severity:** Low
- **Symptom:** The settings section for eSIM/network routing is labeled "Anonymous Network" which sounds suspicious. Should be simply "Network".

### BUG-017: "New Call" FAB appears in all tabs — should only show in Dialer tab (OPEN)
- **Severity:** Medium
- **Symptom:** The floating action button "New Call" is visible in the Calls and Contacts tabs. It should only appear in the Dialer tab, or be hidden in Settings.

### BUG-018: "Report a Bug" in Settings opens GitHub Issues — should open stealthx.tech/wiki/bug-report.html (OPEN)
- **Severity:** Medium
- **Symptom:** "Report a Bug" preference opens GitHub Issues directly instead of the user-friendly bug report form at stealthx.tech/wiki/bug-report.html.

### BUG-019: "Check for Updates" opens Play Store for F-Droid/APK — should go to GitHub Releases (OPEN)
- **Severity:** Medium
- **Symptom:** For non-Play Store installs (F-Droid, APK sideload), "Check for Updates" incorrectly opens Play Store instead of GitHub Releases page.

### BUG-020: IFR Token section should be last in Settings (OPEN)
- **Severity:** Low
- **Symptom:** IFR Token unlock section appears near the top of Settings. It should be the last section before Advanced/Reset.

### BUG-021: Emergency Delete should be first in Settings (OPEN)
- **Severity:** Low
- **Symptom:** Emergency Delete (5-tap reset) is at the bottom of Settings in the Advanced section. For quick access during emergencies, it should be the first item.

### BUG-022: eSIM status stays "connected" after switching to default network (OPEN)
- **Severity:** Medium
- **Symptom:** After enabling eSIM routing and then switching back to the default network, the eSIM status indicator still shows "connected".

### BUG-023: No diagnostic log export — SecLog CSV export (Pro/Premium) (OPEN)
- **Severity:** Low
- **Symptom:** No way to export diagnostic logs for troubleshooting. Pro/Premium users should have a CSV export of SecLog data.

### BUG-024: Random disconnects on network change — reconnects only after app restart (OPEN)
- **Severity:** High
- **Symptom:** Similar to BUG-009, random disconnects occur on network changes. The app only reconnects after a full restart, not automatically.

### BUG-025: Phone normalization — +49 and 0049 and +49 151 234 567 treated as different numbers (OPEN)
- **Severity:** High
- **Symptom:** Different representations of the same phone number (+49xxx, 0049xxx, +49 151 234 567) are treated as different contacts. PhoneUtils.normalize() needs to strip all formatting chars including brackets and slashes.

### BUG-006: WireGuard VPN service never started after permission grant (FIXED)
- **Symptom:** VPN toggle could be enabled, Android VPN permission dialog appeared and was accepted, but GhostVpnService never started. VPN status remained "Enabled — waiting for connection" forever.
- **Root Cause:** `SettingsFragment.configureVpn()` called `VpnController.requestPermission(requireActivity())` which uses `activity.startActivityForResult()`. When the permission dialog was dismissed, there was no `onActivityResult` handler — neither in the Fragment nor in the Activity. So `VpnController.start()` was never called after permission grant.
- **Fix:** (1) Changed permission request to use `fragment.startActivityForResult()` instead of `activity.startActivityForResult()` so the result routes to the fragment. (2) Added `onActivityResult()` override in `SettingsFragment` that calls `VpnController.start()` on `RESULT_OK` or reverts the toggle on denial.
- **Additional fix:** Config dialog was missing client address field, and `vpn_client_address` was not saved by `VpnController.saveConfig()`. Without correct client address (e.g. `10.99.0.2/31`), the tunnel used default `10.66.66.2/32` which didn't match server's AllowedIPs, preventing handshake completion.
- **Verified:** 2026-03-22 S10 Premium. Full WireGuard E2E test:
  - VPN toggle → permission grant → GhostVpnService started ✅
  - WireGuard GoBackend tunnel UP, Noise_IKpsk2 handshake complete ✅
  - Keepalive packets flowing bidirectionally ✅
  - S7→S10 call during active VPN: 21s E2E-encrypted call ✅
  - VPN stop: clean tunnel DOWN ✅
