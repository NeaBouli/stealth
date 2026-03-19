# Full Regression Test — 2026-03-19 v1.1-stable

## Devices

| Device | Serial | Flavor | Build |
|--------|--------|--------|-------|
| S10 (SM-G973F) | RF8N313QMFL | Premium | dac3da7 |
| S7 (SM-G930F) | ce10160adc00152604 | Pro | dac3da7 |
| Tab S4 (SM-T835) | ce12182c68644439037e | Free | dac3da7 |

## Results

| Test | Description | Result | Evidence |
|------|-------------|--------|----------|
| TEST-01 | S7→S10 call, accept, 8s audio, end | ✅ PASS | CALL_INVITE→ACCEPT→8s→END, no crash |
| TEST-02 | Save-contact dialog appears after call | ✅ PASS | "Save Σαννυ (+4915203487046)?" dialog visible, stays open |
| TEST-03 | S10→S7 call via dialer, accept, end | ✅ PASS | Dialer +4915203487046, CALL_INVITE on S7, accepted, ended |
| TEST-04 | No duplicate contacts | ✅ PASS | CHEF appears once, all names unique |
| TEST-05 | Online dots green/red within 15s | ✅ PASS | Stop S7: 3→2 online. Restart: 2→3 online |
| TEST-06 | Settings persist after restart | ✅ PASS | PREMIUM tier, SecureCall ID persist |
| TEST-07 | STEALTH-DELETE | ⏭️ SKIP | Previously verified manually |
| TEST-08 | Activation code | ⏭️ SKIP | Already PREMIUM, previously verified |
| TEST-09 | Screenshot in debug build | ✅ PASS | S7 screencap: 112,200 bytes (non-zero) |
| TEST-10 | Tab S4 Free PRO feature lock | ✅ PASS | "🔒 Online status is a PRO feature" visible |

## Key Coordinates (for future automated tests)

### S10 (1080x2280)
- `nav_contacts`: (405, 1927)
- `nav_settings`: (945, 1927)
- `nav_dialer`: (675, 1927)
- `fabAcceptCall` (IncomingCallActivity): (742, 1807)
- `fabDeclineCall` (IncomingCallActivity): (499, 1807)
- `fabEndCall` (CallActivity): (539, 1807)
- `fabCall` (Dialer): (539, 1699)

### S7 (1440x2560)
- `nav_contacts`: (540, 2400)
- `btnCallContact` CHEF (1st contact): (1280, 855)
- `fabAcceptCall` (IncomingCallActivity): (960, 2192)

### Tab S4 (2560x1600 landscape)
- `nav_contacts`: (1090, 1402)

## Automated Call Flow (ADB commands)

```bash
# S7→S10 call
adb -s ce10160adc00152604 shell input tap 1280 855  # Call CHEF
sleep 5
adb -s RF8N313QMFL shell input tap 742 1807         # Accept on S10
sleep 8
adb -s RF8N313QMFL shell input tap 539 1807          # EndCall on S10

# S10→S7 call (via dialer)
adb -s RF8N313QMFL shell input tap 675 1927           # Dialer tab
adb -s RF8N313QMFL shell input tap 540 463             # Focus number field
adb -s RF8N313QMFL shell input text "+4915203487046"   # Type S7 number
adb -s RF8N313QMFL shell input keyevent KEYCODE_BACK   # Hide keyboard
adb -s RF8N313QMFL shell input tap 539 1699             # Call button
sleep 5
# Accept on S7 (coords from uiautomator dump at runtime)
```
