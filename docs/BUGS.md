# SecureCall Bug Tracker

| ID | Description | Status | Severity | Fixed In |
|----|-------------|--------|----------|----------|
| BUG-001 | Online status dots inconsistent | FIXED | Medium | 9ef003c |
| BUG-002 | Save Contact disappears in <1s | FIXED | High | 5c4f9cd |
| BUG-003 | Contact deduplication broken | FIXED | High | c9c2bbd |
| BUG-004 | IFR wallet verify shows no token count | FIXED | High | c615a5b |
| BUG-005 | Block screenshots not working on all Activities/tiers | FIXED | Medium | 3597cc9 |
| BUG-006 | WireGuard VPN non-functional (no test config) | FIXED | Low | AGP 8.7.3 + GoBackend |
| BUG-007 | Contacts cache empty after app restart — presence skipped | FIXED | Medium | 0681cc7 |
| BUG-008 | CallActivity crash: SecurityException on PhoneStateListener | FIXED | Critical | a90c7fc |

## Fix Details

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
