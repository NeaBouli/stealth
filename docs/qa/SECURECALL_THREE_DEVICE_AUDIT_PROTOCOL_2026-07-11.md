# SecureCall Three-Device Audit And Emulation Protocol

Date: 2026-07-11
Owner: CODEX TERMINAL
Scope: SecureCall Android app, backend-dependent call behavior, device lifecycle, audio routing, settings, tier behavior, and release readiness.

## Purpose

This protocol exists to stop ad hoc debugging. Every SecureCall feature that is present in code must be exercised in a repeatable order across the three physical devices and a small emulator matrix before another release candidate is treated as healthy.

The test result is only green when every required case has one of these outcomes:

- `PASS`: behavior matches expected result.
- `FAIL`: reproducible bug with device, build, logs, exact steps, and suspected code owner.
- `BLOCKED`: external blocker is named, for example locked device, missing SIM/network, server down, or missing Play credentials.
- `N/A`: feature is genuinely unavailable on that device/tier and the reason is documented.

No silent skips.

## Physical Device Matrix

| Role | Device | Serial | Primary Variant | Purpose |
|---|---|---:|---|---|
| D1 | Samsung S10 / SM-G973F | `RF8N313QMFL` | Premium | Primary regression device; audio routing, VPN/network, S10 disconnect history |
| D2 | Samsung S7 / SM-G930F | `ce10160adc00152604` | Pro | Older Android/low-resource behavior, long-running service comparison |
| D3 | Samsung Tab S4 / SM-T835 | `ce12182c68644439037e` | Free | Tablet layout, free-tier restrictions, lockscreen/background behavior |

## Emulator Matrix

Run only after physical smoke is understood. Emulators are not replacements for device audio/call QA.

| Emulator | API | Profile | Purpose |
|---|---:|---|---|
| E1 | 24 | phone | minSdk compatibility and legacy permission behavior |
| E2 | 30 | phone | Android 11 audio/security behavior |
| E3 | 35 | phone | targetSdk 35, edge-to-edge, notification/runtime restrictions |
| E4 | 35 | tablet | tablet layout and responsive settings/navigation |

## Required Artifacts Per Test Session

Create a timestamped folder before testing:

```bash
export QA_TS="$(date +%Y%m%d-%H%M%S)"
export QA_DIR="$HOME/Desktop/securecall-qa-$QA_TS"
mkdir -p "$QA_DIR"/{logs,screens,apks,reports}
```

For each device capture:

```bash
adb -s <SERIAL> shell getprop ro.product.model > "$QA_DIR/logs/<SERIAL>-model.txt"
adb -s <SERIAL> shell getprop ro.build.version.release > "$QA_DIR/logs/<SERIAL>-android.txt"
adb -s <SERIAL> shell dumpsys package com.securecall.app.premium > "$QA_DIR/logs/<SERIAL>-pkg-premium.txt" 2>/dev/null || true
adb -s <SERIAL> shell dumpsys package com.securecall.app.pro > "$QA_DIR/logs/<SERIAL>-pkg-pro.txt" 2>/dev/null || true
adb -s <SERIAL> shell dumpsys package com.securecall.app.free > "$QA_DIR/logs/<SERIAL>-pkg-free.txt" 2>/dev/null || true
```

Capture logs around every call test:

```bash
adb -s <SERIAL> logcat -c
# perform test
adb -s <SERIAL> logcat -d -v time > "$QA_DIR/logs/<TEST_ID>-<SERIAL>.log"
```

Minimum grep after call/audio tests:

```bash
rg -i "CallActivity|AUDIO|AUDIO_PLAYER|WS_SERVICE|WEBRTC|ICE|CALL|Speaker|AudioManager|Ringtone|ToneGenerator|AudioTrack|SecLog|REGISTER|CALL_INVITE|CALL_ACCEPT|CALL_END|4003|429|UnknownHost|timeout|Exception|E/" "$QA_DIR/logs"
```

## Preflight Gate

Do not start user-flow tests until this is green.

### P0.1 Repo And Build State

- [ ] `git status --short` reviewed; unrelated dirty files listed and left untouched.
- [ ] Current `HEAD` recorded.
- [ ] `client_android/app/build.gradle` versionCode/versionName recorded.
- [ ] Release keystore availability confirmed for release install tests.

Commands:

```bash
cd /Users/gio/Desktop/repos/stealth
git log -1 --oneline
git status --short
cd client_android
./gradlew --stop 2>/dev/null || true
./gradlew --no-daemon --max-workers=1 assembleFreeRelease assembleProRelease assemblePremiumRelease
```

### P0.2 Backend And Website Dependencies

- [ ] `https://api.stealthx.tech/health` returns OK.
- [ ] Activation/license status endpoint returns expected product keys.
- [ ] Stripe purchase links are not required for call QA, but activation-code unlock must be available if tier state is reset.
- [ ] PM2/signaling status checked if call signaling fails.

### P0.3 Device Preparation

For all three physical devices:

- [ ] ADB visible with correct serial.
- [ ] Screen unlocked.
- [ ] Battery above 30% or charging.
- [ ] App notification permission state recorded.
- [ ] Battery optimization/whitelist state recorded.
- [ ] VPN state recorded.
- [ ] Wi-Fi/mobile state recorded.
- [ ] Other dev activity on the same devices confirmed stopped before SecureCall QA.

Commands:

```bash
adb devices -l
for D in RF8N313QMFL ce10160adc00152604 ce12182c68644439037e; do
  adb -s "$D" shell dumpsys connectivity | rg -i "NetworkAgentInfo|WIFI|CELLULAR|VPN|VALIDATED|CONNECTED" || true
  adb -s "$D" shell dumpsys deviceidle | rg -i "whitelist|idle|mState|mNetworkConnected" || true
done
```

## Install Matrix

Release APKs must be used when an installed release build exists. Debug APKs can be used only after uninstall or on dedicated debug devices.

| Test ID | D1 S10 | D2 S7 | D3 Tab S4 | Expected |
|---|---|---|---|---|
| I-001 | Premium release | Pro release | Free release | Installs without signature mismatch |
| I-002 | Premium launch | Pro launch | Free launch | No crash, correct package/name/icon |
| I-003 | Upgrade over previous release | Upgrade over previous release | Upgrade over previous release | Data preserved unless test explicitly uninstalls |
| I-004 | Fresh install after uninstall | Fresh install after uninstall | Fresh install after uninstall | Onboarding/permissions behave consistently |

Commands:

```bash
adb -s RF8N313QMFL install -r app/build/outputs/apk/premium/release/app-premium-release.apk
adb -s ce10160adc00152604 install -r app/build/outputs/apk/pro/release/app-pro-release.apk
adb -s ce12182c68644439037e install -r app/build/outputs/apk/free/release/app-free-release.apk
```

## Functional Test Passes

Each test must be run with screenshots/logs when behavior is not obvious.

### A. Launch, Onboarding, Permissions

| ID | Case | Devices | Expected |
|---|---|---|---|
| A-001 | Cold launch after install | D1/D2/D3 | App opens without crash; correct first screen |
| A-002 | Runtime permissions | D1/D2/D3 | Microphone/contacts/notification prompts make sense and do not loop |
| A-003 | Onboarding first run | D1/D2/D3 fresh install | Shows once, progresses, does not return after completion |
| A-004 | Confirm phone number | D1/D2/D3 | Confirmed number persists; no repeat loop after confirm |
| A-005 | Skip phone confirm | D1/D2/D3 | Skip suppresses prompt as designed; no crash |
| A-006 | Reboot after setup | D1/D2/D3 | Background service/boot behavior matches Settings |

### B. Navigation And Core UI

| ID | Case | Devices | Expected |
|---|---|---|---|
| B-001 | Bottom navigation | D1/D2/D3 | Calls, Contacts, Settings reachable; no banner overlap |
| B-002 | Edge-to-edge layout | D1/D2/D3 + E3/E4 | No controls hidden by nav/status bars on Android 15 behavior |
| B-003 | Landscape/rotation policy | D1/D2/D3 | Call screen respects configured orientation; no state loss |
| B-004 | Tablet layout | D3/E4 | Settings/call controls visible and tappable |

### C. Contacts And Dialing

| ID | Case | Devices | Expected |
|---|---|---|---|
| C-001 | Add SecureCall contact manually | D1/D2/D3 | Contact persists and appears in Contacts |
| C-002 | Phone book lookup | D1/D2 | Phone contact resolves to display name |
| C-003 | QR/share contact flow | D1/D2/D3 | QR opens, scans/imports if supported |
| C-004 | Blocked contact | D1/D2 | Incoming/outgoing behavior follows block policy |
| C-005 | Unknown Secure ID | D1 -> D2 | Friendly offline/not found error, no stuck call UI |

### D. Signaling And Call State

Run all pair directions. Do not test only one direction.

| ID | Caller | Callee | Network | Expected |
|---|---|---|---|---|
| D-001 | D1 Premium | D2 Pro | same Wi-Fi | INVITE -> ringing -> ACCEPT -> active -> END |
| D-002 | D2 Pro | D1 Premium | same Wi-Fi | Same as D-001 |
| D-003 | D1 Premium | D3 Free | same Wi-Fi | Same as D-001, Free restrictions visible where applicable |
| D-004 | D3 Free | D1 Premium | same Wi-Fi | Same as D-001 |
| D-005 | D2 Pro | D3 Free | same Wi-Fi | Same as D-001 |
| D-006 | D1 Premium | D2 Pro | D1 VPN on | Either connects or fails with clear logs; no infinite ringing |
| D-007 | D1 Premium | D2 Pro | Wi-Fi -> mobile handoff | Reconnect behavior logged; app does not hang silently |
| D-008 | D1 Premium | D2 Pro | airplane mode during call | Peer receives disconnect/end; UI recovers |
| D-009 | D1 calls busy D2 | D3 attempts D2 | busy state shown; no double session |
| D-010 | Caller cancels while ringing | each pair | Incoming UI dismisses; missed call state correct |
| D-011 | Callee declines | each pair | Caller sees ended/declined; no stuck ringback |
| D-012 | Backend unavailable simulation | any pair | Clear error path; no endless spinner |

### E. Audio Routing And Ringing

This is the current high-risk area. Capture logs for every case.

| ID | Case | Devices | Expected |
|---|---|---|---|
| E-001 | Outgoing ringback volume | D1/D2/D3 | Ringback is not painfully loud; stops immediately on accept/error/end |
| E-002 | Incoming ringtone/vibration | D1/D2/D3 | Starts once; stops on accept/decline/timeout/caller cancel |
| E-003 | Active call earpiece | D1/D2 | Audio routes to earpiece by default on phone devices |
| E-004 | Active call speaker ON | D1/D2/D3 | Button state and actual route both ON; audible on loudspeaker |
| E-005 | Active call speaker OFF | D1/D2/D3 | Button state and actual route both OFF; routes back to earpiece/headset policy |
| E-006 | Rapid speaker toggles | D1/D2/D3 | No stuck state; route follows final tap |
| E-007 | Speaker toggle before active | D1/D2/D3 | Either disabled until active or remembered cleanly; no inconsistent UI |
| E-008 | Mute ON/OFF | all pairs | Local mic stops/resumes; remote hears expected behavior |
| E-009 | Mute + speaker interaction | all pairs | Toggles independent; no audio route corruption |
| E-010 | Hardware volume buttons during ringback | D1/D2/D3 | Adjusts expected stream; no forced max after user lowers |
| E-011 | Hardware volume buttons during active call | D1/D2/D3 | Adjusts call volume; no reset on state update |
| E-012 | Bluetooth headset connected | at least one phone | Routes to BT; speaker toggle policy clear |
| E-013 | Wired headset connected | if available | Routes to headset; speaker toggle policy clear |
| E-014 | Incoming GSM call during SecureCall | D1/D2 | SecureCall pauses/resumes as designed |

Audio debug commands:

```bash
adb -s <SERIAL> shell dumpsys audio > "$QA_DIR/logs/<TEST_ID>-<SERIAL>-dumpsys-audio.txt"
adb -s <SERIAL> logcat -d -v time | rg -i "AUDIO|AudioManager|AudioTrack|Speaker|CallActivity|ToneGenerator|Ringtone|AUDIO_PLAYER|WS_SERVICE" > "$QA_DIR/logs/<TEST_ID>-<SERIAL>-audio.log"
```

Known code areas to inspect on failure:

- `CallActivity.java`: speaker toggle, ringback, `prepareCallAudio`, `configureCallAudio`, `restoreCallAudio`.
- `GhostAudioPlayer.kt`: `AudioTrack` attributes/lifecycle.
- `WebSocketService.kt`: jitter playback start/stop and global audio cleanup.
- `IncomingCallActivity.kt`: accept/decline ringtone cleanup.

### F. Lifecycle And Background

| ID | Case | Devices | Expected |
|---|---|---|---|
| F-001 | Background during active call | D1/D2/D3 | Call survives; notification/service state correct |
| F-002 | Lock screen during incoming call | D1/D2/D3 | Incoming UI appears over lock screen and is actionable |
| F-003 | Lock screen during active call | D1/D2/D3 | Proximity/controls behave; no accidental stuck screen |
| F-004 | App swipe-away while idle | D1/D2/D3 | Background-service setting respected |
| F-005 | App force-stop | D1/D2/D3 | Recovers on manual launch; no phantom call |
| F-006 | Device reboot | D1/D2/D3 | Boot receiver/service state matches Settings |
| F-007 | Doze / battery optimization | D1/D2/D3 | Whitelisted service keeps expected connectivity |
| F-008 | 12-hour idle service sample | D1 preferred | No unbounded wakelock/alarm churn; WebSocket state understood |

### G. Settings Exhaustive Pass

Use `docs/SETTINGS_AUDIT_2026-06-21.md` as baseline. Each visible Settings entry must be tapped or toggled at least once on the correct tier.

| ID | Area | Expected |
|---|---|---|
| G-001 | Account/upgrade | Opens current pricing, no IFR/wallet app unlock copy |
| G-002 | Activation code | Valid code unlocks; invalid code has clear error |
| G-003 | Background service | Toggle starts/stops service and notification correctly |
| G-004 | Privacy/call history | Call records persist or do not persist according to pref |
| G-005 | Appearance | Theme changes without layout break |
| G-006 | Security/anti-recording | Tier gates match Free/Pro/Premium |
| G-007 | Anonymous network/eSIM | Unsupported devices show clear disabled state |
| G-008 | VPN | No persisted enabled state without configuration; status refreshes |
| G-009 | Battery optimization | Opens Android settings and refreshes on return |
| G-010 | Legal/about links | All links open or fail gracefully |
| G-011 | Support development | Addresses copy correctly |
| G-012 | Diagnostics | SecLog enable/export/clear gated and functional |
| G-013 | Reset/stealth delete | Requires intended multi-step action; no accidental wipe |

### H. Tier And Variant Behavior

| ID | Case | Free | Pro | Premium |
|---|---|---|---|---|
| H-001 | Package id/name/icon | correct | correct | correct |
| H-002 | Call duration/contact limits | enforced | unlimited | unlimited |
| H-003 | Ads/banner behavior | allowed, non-overlapping | absent | absent |
| H-004 | Certificate pinning | enabled | enabled | enabled |
| H-005 | Security enforcement | warn/limited | stronger | strict terminate where designed |
| H-006 | SecLog diagnostics | gated | available | available |
| H-007 | Upgrade/pricing links | visible | appropriate | appropriate |

### I. Security And Privacy

| ID | Case | Devices | Expected |
|---|---|---|---|
| I-001 | Screenshot in call | D1/D2/D3 | Tier policy enforced |
| I-002 | Screen recording during call | D1/D2/D3 | Warning/block/terminate policy follows tier |
| I-003 | Mic conflict | D1/D2 | Warning/pause behavior logged |
| I-004 | Accessibility detector | if safe to test | Suspicious service warning where applicable |
| I-005 | Call recording app installed | if safe to test | Detected/warned according to tier |
| I-006 | No sensitive data in logs | all logs | No secrets, activation codes, full tokens, private keys |

### J. Data Persistence And History

| ID | Case | Expected |
|---|---|
| J-001 | Incoming answered call history | Correct contact, type, duration |
| J-002 | Outgoing answered call history | Correct contact, type, duration |
| J-003 | Missed call history | Saved when enabled, absent when disabled |
| J-004 | Save-contact prompt | Shows only when applicable; does not loop |
| J-005 | Verify-contact prompt | Shows only for unverified saved contact; result persists |
| J-006 | Clear/reset data | Removes local state expected by UI |

### K. Release Build And Store Artifacts

| ID | Case | Expected |
|---|---|
| K-001 | `bundleFreeRelease` | Builds AAB, versionCode unused for Play |
| K-002 | Release APK install | No signature mismatch on devices |
| K-003 | R8/ProGuard | No runtime crash from stripped classes |
| K-004 | Play prelaunch warnings | Edge-to-edge/insets assessed |
| K-005 | Release notes | Match actual changes |

## Pass/Fail Report Template

Create one report per session:

```markdown
# SecureCall Three-Device QA Report

Date:
Build commit:
Version code/name:
APK/AAB paths:
Devices:

## Summary
- PASS:
- FAIL:
- BLOCKED:

## Failures
| ID | Device | Variant | Steps | Expected | Actual | Logs | Suspected code |
|---|---|---|---|---|---|---|---|

## Required Retest
| Fix commit | Test IDs |
|---|---|

## Release Decision
- [ ] Safe to build AAB
- [ ] Not safe: blockers listed above
```

## Hard Stop Rules

Do not produce a release AAB as "green" if any of these fail:

- Any device cannot install its intended release variant.
- Any two-device call cannot reach active state in at least one direction.
- Speaker route gets stuck or UI lies about actual route on S10.
- Ringback/ringtone cannot be stopped immediately.
- Confirm-phone prompt loops after successful confirmation.
- Settings bottom controls are covered by ads or system bars.
- Background service state cannot be reasoned about from logs.
- Release build strips all diagnostics needed for field investigation and SecLog cannot compensate.
- Any Play-targeted AAB has already-used versionCode.

## Tonight's Recommended Execution Order

1. Preflight and install release APKs.
2. S10 <-> S7 basic call both directions.
3. S10 audio routing/ringback speaker suite (`E-001` through `E-011`).
4. S10 <-> Tab S4 Free/Premium behavior.
5. Settings exhaustive pass on all three devices.
6. Lifecycle/network tests on S10.
7. Only then emulator/API 35 edge-to-edge checks.
8. Write report and decide whether to fix or build.
