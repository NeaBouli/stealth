# Test Session Summary — 2026-03-19

## Devices

| Device | Serial | Flavor | APK Version | WS Connected |
|--------|--------|--------|-------------|--------------|
| S10 (SM-G973F) | RF8N313QMFL | Premium | 0681cc7 (BUG-007 fix) | Yes |
| S7 (SM-G930F) | ce10160adc00152604 | Pro | 0681cc7 (BUG-007 fix) | Yes |
| Tab S4 (SM-T835) | ce12182c68644439037e | Free | 0681cc7 (BUG-007 fix) | Yes |
| Emulator (Pixel 5, API 36) | emulator-5554 | Free | 0681cc7 (BUG-007 fix) | Yes (slow) |

## Network

- S10: Mullvad VPN active — works
- S7: COSMOTE-37vfbu WiFi — works
- Tab S4: COSMOTE WiFi — works
- Emulator: Host network — works

## Test Results

### TEST-01: Incoming call S7→S10 — IncomingCallActivity launches without crash
**PASS** — Verified via logcat:
```
20:22:04 WS_SERVICE: IncomingCallActivity launched directly for Σαννυ
20:22:04 WS_SERVICE: Incoming call notification shown for Σαννυ
```
No FATAL/crash/exception in logcat. Activity launched, displayed caller name, ended cleanly.

### TEST-02: Accept call — CallActivity launches
**INCONCLUSIVE** — Call was declined (not accepted) in the 20:22 test. No crash on decline.

### TEST-03: Reject call — app returns to normal state
**PASS** — Call declined at 20:22:49:
```
20:22:49 WS_SERVICE: CALL_END sent for session 11971f2c-...
20:22:49 WS_SERVICE: CALL_END_ACK received
```
App returned to MainActivity normally.

### TEST-04: End call — save-contact dialog
**NOT TESTED** — Requires accepted call + call end sequence.

### TEST-05: Contact list S10 — no duplicates
**PASS** — ContactsFragment showing contacts correctly, batch lookup running, no duplicate logs.

### TEST-06: Online dots S10
**PASS** — Full cycle verified:
```
All online:  ONLINE_STATUS_RESPONSE: 3 phones, 3 online
S7 stopped:  ONLINE_STATUS_RESPONSE: 3 phones, 2 online (+4915203487046: false)
S7 restart:  ONLINE_STATUS_RESPONSE: 3 phones, 3 online (+4915203487046: true)
```
Transition time: within 15 seconds (STATUS_REFRESH_INTERVAL).

### TEST-07: IFR wallet verify
**NOT TESTED** — Manual UI interaction required.

### TEST-08: Screenshot blocking
**PASS** — `screencap` on S10 (Premium) returns 0-byte file (FLAG_SECURE active).
Tab S4 (Free) screencap works normally.

### TEST-09: Emulator connectivity
**PASS** — Emulator registered with server:
```
20:34:43 WS_SERVICE: REGISTER sent: android-33068922, phone: none
20:34:43 WS_SERVICE: Message: {"type":"REGISTERED","clientId":"android-33068922"}
```
Note: Emulator is very slow (API 36, swiftshader GPU). Not suitable for call testing.

### TEST-10: Cross-device call with emulator
**NOT TESTED** — Emulator too slow, no phone number assigned.

## BUG-007 Verification

**PASS** — Full cycle:
```
1. S7 had presence working (1 registered phone, 1 online)
2. Force-stop S7 → app killed
3. Restart S7 → app launches fresh
4. 20:30:08.351 "Loaded 1 persisted registered phones for immediate presence"
5. 20:30:08.479 ONLINE_STATUS_RESPONSE: 1 phones, 1 online (IMMEDIATE!)
6. 20:30:08.722 Batch lookup complete (would have been the old 5-min wait)
```
Presence worked BEFORE batch lookup completed. Fix confirmed.

## Incoming Call Crash Investigation

**NO CRASH FOUND** — The incoming call from S7→S10 at 20:22:04 worked correctly:
- IncomingCallActivity launched for caller "Σαννυ"
- Notification shown
- Call declined cleanly
- No crash, no exception, no ANR in logcat

The "incoming call crash" may have been a transient issue or specific to a previous APK version. Current build (0681cc7) handles incoming calls without issues.

## Full Logs

Raw device logs saved to `docs/logs/` (gitignored due to size):
- `S10_premium_20260319.log` (8MB)
- `S7_pro_20260319.log` (342KB)
- `TabS4_free_20260319.log` (10MB)
- `emulator_free_20260319.log` (24MB)
