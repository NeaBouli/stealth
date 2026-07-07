# S10 Disconnect Investigation - 2026-07-07

Status: Initial report captured before machine restart.
Repo: `/Users/gio/Desktop/repos/stealth`
Primary app: SecureCall
Device under investigation: S10, previously referenced as `RF8N313QMFL`

## User Report

- SecureCall disconnected on the S10 without an obvious reason.
- When the phone is on the home network, the app suddenly shows `connected`.
- Need to read Android logs and determine what is happening.
- Suspected behavior to check:
  - network-dependent WebSocket reconnect behavior
  - background service / keep-alive behavior
  - mobile-data or non-home-Wi-Fi disconnect path
  - S10-specific battery/network restrictions
  - server-side fork/signature rejection or reconnect throttling

Original phrasing preserved:

> Securecall dosconected beim s 10 ohne ersichtlichen grund lese logs aus und pruefe den bug. aber wenn ich im heim netzwerk bin sehe ich ploetzlich cionectetd was assier hier.

## Email / GitHub Notification To Review

Source as pasted by user:

> zig-VS-python left a comment (NeaBouli/stealth#28)
> hello Merge into a single app while retaining the phone "Dialer".
> All popular chat apps include messaging functionality,
> Stealth alternatives
>
> such as WhatsApp or Signal, but without requiring a phone number linked to your real identity.
>
> Screenshot_20260705-094902.png (view on web)

Initial interpretation:

- This appears to be a GitHub issue comment on `NeaBouli/stealth#28`.
- The commenter is requesting/arguing for one combined app that keeps the phone dialer while adding chat functionality.
- They compare the product direction to WhatsApp/Signal, but emphasize avoiding a phone number tied to real identity.
- Need to inspect issue #28 directly after restart if GitHub/CLI is available, including the attached screenshot.

## Immediate Debug Plan After Restart

1. Confirm S10 ADB visibility:
   - `adb devices -l`
   - expected S10 serial from prior logs: `RF8N313QMFL`
2. Capture current app/package state:
   - `adb -s RF8N313QMFL shell dumpsys package com.securecall.app.free | rg 'version|targetSdk|installer|enabled'`
   - also check `com.securecall.app.pro` and `com.securecall.app.premium` if installed.
3. Capture connectivity and power state:
   - `adb -s RF8N313QMFL shell dumpsys connectivity`
   - `adb -s RF8N313QMFL shell dumpsys wifi`
   - `adb -s RF8N313QMFL shell dumpsys deviceidle`
   - `adb -s RF8N313QMFL shell dumpsys battery`
4. Capture SecureCall logs:
   - `adb -s RF8N313QMFL logcat -d -v time | rg -i 'securecall|websocket|wss|signal|register|connected|disconnect|reconnect|foreground|keepalive|wakelock|doze|battery|network|connectivity|fcm|ghostvpn|vpn|unauthorized|signature'`
5. Compare on home network vs non-home network:
   - record Wi-Fi SSID/BSSID where possible
   - record whether mobile data is active
   - record whether VPN is active
   - observe whether WebSocketService transitions to connected only after home network association
6. Check backend logs if available:
   - look for S10 clientId / package / appSignature rejection
   - confirm no `unauthorized_client`, `REJECTED`, or reconnect-spam for S10.

## Code Areas To Inspect

- `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
- `client_android/app/src/main/java/com/securecall/app/net/GhostNetWebSocketClient.kt`
- `client_android/app/src/main/java/com/securecall/app/BootReceiver.kt`
- `client_android/app/src/main/java/com/securecall/app/KeepAliveReceiver.kt`
- `client_android/app/src/main/java/com/securecall/app/SecureCallApplication.kt`
- Settings background-service toggle handling.

## Notes

- Do not guess a fix before reproducing or isolating whether this is client, device power management, network, or backend rejection.
- Current repo head before investigation note: `2683d80 docs: record dependency alert closure`.

## Follow-up After Restart - 2026-07-07

Commands were run after the machine restart. ADB is responsive again, but the
previously referenced S10 serial `RF8N313QMFL` is not currently attached.

Visible ADB devices:

- `ce10160adc00152604` — `SM-G930F`
- `ce12182c68644439037e` — `SM-T835`

Installed SecureCall packages:

- `SM-G930F`: `com.securecall.app.premium`
- `SM-T835`: `com.securecall.app.free`, `com.securecall.app.pro`,
  `com.securecall.app.premium`

Because the S10 is not visible, the following are comparison findings only and
do not prove the S10 root cause.

### Comparison Device Findings

`SM-G930F`:

- `com.securecall.app.premium` is installed as `1.0.41-premium`
  (`versionCode=77009`, `targetSdk=35`).
- `WebSocketService` is running as a foreground service in process
  `com.securecall.app.premium`, pid `28007`.
- Service age from `dumpsys activity services`: about `13d15h`.
- `dumpsys power` shows a long-held partial wake lock:
  `securecall:ws_heartbeat`, uid `10315`, pid `28007`.
- `dumpsys deviceidle` shows `com.securecall.app.premium` in the user
  whitelist.
- `dumpsys alarm` shows active `WebSocketService` repeating alarm and
  `KeepAliveReceiver` alarm for `com.securecall.app.premium`.
- Current network is validated Wi-Fi on SSID `GL-MT300N-V2-5df`
  (`192.168.8.187/24`).
- Logcat for the SecureCall pid contains only notification noise; release
  builds strip `Log.d`/`Log.i` via ProGuard, so normal connect/register events
  are not visible in logcat.

`SM-T835`:

- SecureCall packages are installed, but no SecureCall process is currently
  running; only `com.stealthx.chameleon` appears in `ps`.
- Current network stack has validated Wi-Fi on `COSMOTE-536679` plus an active
  VPN default network (`tun0`, establishing uid `10265`).
- SecureCall is not in the current `deviceidle` user whitelist.
- Old SecureCall alarms are present but overdue by about seven days, consistent
  with packages/services not actively running.

### Code Findings Relevant To S10

The reconnect path is split across:

- `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
- `client_android/app/src/main/java/com/securecall/app/net/HeartbeatClient.kt`
- `client_android/app/src/main/java/com/securecall/app/net/NetworkManager.kt`
- `client_android/app/src/main/java/com/securecall/app/net/KeepAliveReceiver.kt`

Important behavior:

- `WebSocketService.startSignaling()` always calls
  `NetworkManager.bindToPreferredNetwork(this)` before connecting.
- `NetworkManager` reads `preferred_network_transport` from
  `securecall_prefs`.
- If the preference is `default`, the process is unbound and Android chooses
  the active network.
- If the preference is `wifi`, `cellular`, or `esim`, the process is explicitly
  bound to that transport and `WebSocketService.instance?.forceReconnect()` is
  called when that preferred network appears or disappears.
- `WebSocketService` also registers a default network callback. After a network
  loss and subsequent availability it waits two seconds, then calls
  `client?.forceReconnect()`.
- `HeartbeatClient` treats 4000-4099 close codes as hard server rejections and
  stops reconnecting. It also backs off 429 responses for five minutes and uses
  a 30-second reconnect delay for DNS failures.
- Release builds strip debug/info logging, so the most useful production
  observability is currently `SecLogManager`, but only when `seclog_enabled` is
  enabled inside app prefs.

Current strongest hypothesis:

- The user-visible "disconnected except at home, then suddenly connected"
  pattern is most consistent with network selection or routing, especially a
  non-default `preferred_network_transport`, VPN interaction, DNS on mobile
  data, or backend reachability/certificate behavior that differs by network.
- Battery/Doze is less supported by the comparison data because `SM-G930F`
  keeps a foreground service, wake lock, whitelist entry, and active alarms.
  This still needs to be checked on the actual S10.
- Backend rejection remains possible, especially if the S10 receives a 4003 or
  other 4000-4099 close. Release logcat may only show this as `Log.e`/`Log.w`;
  server logs or enabled SecLog are needed for proof.

### Next Commands When S10 Is Attached

```bash
adb devices -l
adb -s RF8N313QMFL shell getprop ro.product.model
adb -s RF8N313QMFL shell pm list packages | rg -i 'securecall|stealth|ghost|nea'
adb -s RF8N313QMFL shell ps -A | rg 'securecall|neabouli|stealth'
adb -s RF8N313QMFL shell dumpsys package com.securecall.app.premium | rg 'versionName|versionCode|targetSdk|firstInstallTime|lastUpdateTime|enabled='
adb -s RF8N313QMFL shell dumpsys activity services com.securecall.app.premium
adb -s RF8N313QMFL shell dumpsys power | rg -i 'securecall|ws_heartbeat|Wake Locks|mWakefulness'
adb -s RF8N313QMFL shell dumpsys deviceidle | rg -i 'mState|mLightState|mNetworkConnected|Whitelist|com.securecall'
adb -s RF8N313QMFL shell dumpsys alarm | rg -i 'securecall|KeepAliveReceiver|WebSocketService' -C 2
adb -s RF8N313QMFL shell dumpsys connectivity | rg -i 'NetworkAgentInfo|WIFI|CELLULAR|VPN|VALIDATED|SSID|CONNECTED'
adb -s RF8N313QMFL logcat -d -v time | rg -i 'WS_SERVICE|\\bHB\\b|NetworkManager|SecLog|REGISTER timeout|4003|429|UnknownHost|Unable to resolve|network lost|forceReconnect|WebSocket error'
```

If `run-as` is available for the installed build, inspect network prefs:

```bash
adb -s RF8N313QMFL shell run-as com.securecall.app.premium sh -c 'cat shared_prefs/securecall_prefs.xml' | rg 'preferred_network_transport|esim_routing_enabled|seclog_enabled|pref_background_service'
```

If `run-as` is not available, enable/export SecLog from the app UI on the S10
and reproduce:

1. Start on mobile data or the non-working network until SecureCall shows
   disconnected.
2. Switch to the home Wi-Fi where it becomes connected.
3. Export SecLog immediately.
4. Capture logcat immediately after the transition.

### GitHub Issue #28 Follow-up

Issue: `NeaBouli/stealth#28`, `[Feature]: Send a text message`, opened by
`zig-VS-python`, state `OPEN`.

Relevant comments:

- `NeaBouli` replied on 2026-05-31 that text messaging is handled by
  SecureChat as a separate app in the StealthX platform.
- `zig-VS-python` commented on 2026-07-05 asking to merge into one app while
  retaining the phone dialer, and compared the desired direction to WhatsApp or
  Signal without requiring a phone number linked to real identity. The comment
  includes a screenshot attachment.

Interpretation:

- This is a product-scope request, not evidence for the S10 disconnect bug.
- It conflicts with the previous "SecureCall voice/video, SecureChat messaging"
  separation and should be handled as a product decision before any
  implementation work.
