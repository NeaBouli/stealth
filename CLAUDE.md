# Project Handover -- SecureCall (StealthX Platform)

## Current Status

SecureCall is an end-to-end encrypted voice calling app for Android. The monorepo contains the Android client, a Node.js signaling backend (deployed on Railway), a Rust crypto engine, and supporting infrastructure.

**Sprint status:** All changes are **committed and pushed** to `origin/main`. End-to-end call signaling is fully implemented and tested bidirectionally between S10 and Pixel 5 emulator. Heartbeat timeout cycling is fixed — connections are now stable.

**The 13 completed changes (all committed):**

1. **Contact auto-call fix** -- Removed `itemView.setOnClickListener` from `ContactAdapter.kt`. Only the phone icon (`btnCallContact`) now triggers calls; tapping the contact row does nothing.
2. **Messenger invite dialog** -- `DialerFragment.kt` now shows a 3-option dialog (Via Messenger, Share Link, Send SMS) when dialing an unknown number.
3. **Invite dialog bug fix** -- Removed `.setMessage()` from the invite AlertDialog (suppresses `setItems()`). Moved text into `.setTitle()`. *(Commit `a0b9872`)*
4. **Foreground background service** -- `WebSocketService` is now a foreground service with persistent notification.
5. **Red dial pad digits** -- `Widget.SecureCall.DialButton` style uses `@color/stealthx_red`.
6. **README transparency section** -- Third-Party Services table added to `README.md`.
7. **WebSocket reconnect-loop fix** -- Shared `OkHttpClient`, `isConnecting` guard, 30s max backoff.
8. **End-to-end call signaling** *(Commit `ac6f982`)* -- Full call flow wired: CALL_INVITE → IncomingCallActivity → CALL_ACCEPT → CallActivity with transport. See details below.
9. **End call button fix** *(Commit `bc1cc52`)* -- Removed `isCallActive` guard so end call works during ringing/connecting. Shows red end-call icon immediately for outgoing calls.
10. **Heartbeat timeout fix** *(Commit `c17bc14`)* -- Server sends `HEARTBEAT_ACK` response to client HEARTBEAT messages. Client updates `lastSeen` on successful heartbeat send. Filters `HEARTBEAT_ACK` from verbose logs. Connections are now stable (no more ~30s cycling).
11. **Session timeout increase** *(Commit `fe2b9fd`)* -- Server session timeout changed from 30s to 60s in `heartbeat.js`. Prevents ringing sessions from expiring before the callee can accept.
12. **IncomingCallActivity race condition fix** *(Commit `fe2b9fd`)* -- `onDestroy()` no longer clears `onCallEnded` callback when transitioning to CallActivity. Uses `accepted` flag to only clear on decline/caller-hangup.
13. **Heartbeat timeout increase** -- Client-side timeout changed from 15s to 30s.

**End-to-end call signaling details (change #8):**
- `WebSocketService.kt`: Added call signaling callbacks (`_onCallAccepted`, `_onCallEnded`, `_onCallError`), session tracking, incoming call activity launch, message parsing for CALL_INVITE/CALL_INVITE_ACK/CALL_ACCEPT/CALL_END/ERROR. Uses private backing fields with explicit setter methods for Java interop.
- `IncomingCallActivity.kt` (NEW): Ringing screen with accept/decline FABs. Accept sends CALL_ACCEPT + launches CallActivity. Decline sends CALL_END. Auto-closes if caller hangs up during ringing.
- `activity_incoming_call.xml` (NEW): Dark-themed layout with caller avatar, name, and accept/decline buttons.
- `CallActivity.java`: Three-way branching (fromNotification/isIncoming/outgoing). Outgoing sends CALL_INVITE, waits for CALL_ACCEPT to start transport. `endCall()` sends CALL_END signaling and clears all callbacks. Extracted `startTransportAndTimer()` helper.
- `AndroidManifest.xml`: Registered IncomingCallActivity with `showOnLockScreen` and `turnScreenOn`.
- `strings.xml`: Added `incoming_call_title`, `call_accept`, `call_decline`, `settings_client_id`.
- `preferences.xml` + `SettingsFragment.kt`: Tap-to-copy SecureCall ID in Account settings.

**Testing:** APK tested on S10 (serial `RF8N313QMFL`, clientId `android-ded42f50`) and Pixel 5 emulator (clientId `android-be16bcbf`). Full bidirectional call signaling verified:
- **S10 → Emulator:** CALL_INVITE → IncomingCallActivity → Accept → CALL_ACCEPT → CallActivity with timer → End call from S10 → Emulator receives CALL_END and closes
- **Emulator → S10:** CALL_INVITE → IncomingCallActivity on S10 → Accept → CALL_ACCEPT → CallActivity with timer → End call from emulator → S10 receives CALL_END and closes
- **End call works in all states:** ringing, connecting, and active. Both caller and callee can end.
- **Remote hangup:** When one side ends the call, the other side receives CALL_END and automatically closes CallActivity.
- **Heartbeat stability:** Connections remain stable for 65+ seconds with zero timeouts or reconnects.
- **Error handling:** `peer_not_found` (target offline), `peer_disconnected` (caller dropped), `session_not_found` (stale session) all handled correctly

## Architecture Decisions

- **Monorepo structure:** Android client, backend, Rust crypto, website, and docs all live in one repo. The Android project root is `client_android/`.
- **Three-tier product flavors:** `free` (billing, limited features), `pro` (unlimited, cert pinning, root detection), `premium` (max security, hardware keystore, all detections). Each has its own `applicationIdSuffix` and `buildConfigField` flags. The `free` debug variant is used for development and testing.
- **Rust crypto via JNI:** All cryptographic operations (XChaCha20-Poly1305, X25519, HKDF-SHA256) run in a native Rust library (`core_crypto/`) accessed via JNI through a C++ CMake bridge (`src/main/cpp/CMakeLists.txt`). No Java crypto APIs are used.
- **GhostNet transport protocol:** Custom encrypted voice transport layer in `com.securecall.app.ghostnet` with its own handshake, session management, frame serialization, and UDP transport. Separate from the WebSocket signaling layer.
- **WebSocket signaling:** `HeartbeatClient` manages the OkHttp WebSocket connection with keepalive pings (8s app-level HEARTBEAT + 5s OkHttp native ping) and exponential backoff reconnect (1s -> 30s max). `WebSocketService` wraps it as a foreground service and handles message routing (call signaling, key exchange, subscription verification, GHOST protocol). Heartbeat timeout is 30s. Server responds with `HEARTBEAT_ACK` to keep client `lastSeen` fresh. Client also updates `lastSeen` on successful send (writable socket = alive).
- **Single reconnect owner:** Only `HeartbeatClient.onFailure()` triggers reconnect. `WebSocketService.onError()` notifies callbacks but does NOT reconnect. This prevents the double-reconnect exponential explosion bug.
- **Server session/client timeout:** 60s (in `heartbeat.js`). Server sends native `ws.ping()` every 5s, tracks `lastSeen` via pong and message handlers. Sessions and clients that don't communicate for 60s are terminated.
- **Call signaling flow:** Outgoing: CallActivity sends CALL_INVITE via WebSocketService → server routes to target → target's WebSocketService launches IncomingCallActivity → user accepts → CALL_ACCEPT sent back → caller's CallActivity starts GhostNetTransport. End call: either side sends CALL_END → server forwards and cleans up session → remote side receives CALL_END and auto-closes. All callbacks use private backing fields (`_onCallAccepted`, etc.) with explicit setter methods to avoid Kotlin/Java JVM signature clashes. IncomingCallActivity uses an `accepted` flag to prevent clearing `onCallEnded` when transitioning to CallActivity.
- **Kotlin/Java interop:** CallActivity is Java, WebSocketService is Kotlin. Kotlin `var` properties auto-generate getters/setters that clash with explicit methods of the same name. Solution: private backing fields (`_fieldName`) with explicit public setter methods. Java lambdas for Kotlin `(String) -> Unit` must return `kotlin.Unit.INSTANCE`.
- **Feature flags via BuildConfig:** Tier-specific behavior is controlled by `BuildConfig` fields set in `build.gradle` per flavor, accessed at runtime through `FeatureProvider` interface and `FeatureProviderRegistry` singleton.
- **Firebase disabled:** Firebase initialization is disabled via manifest (`FirebaseInitProvider` set to `enabled="false"`). Crashlytics and Analytics collection are both disabled. FCM push notifications won't work until Firebase is properly configured with real credentials.

## Project Structure

```
stealth/                              # Monorepo root
├── CLAUDE.md                         # THIS FILE
├── README.md                         # Project README (modified: transparency section added)
├── LICENSE                           # Source-available license
├── SECURITY.md                       # Security disclosure policy
├── CHANGELOG.md
├── logo.png
├── securecall-release-key.jks        # Release signing keystore
│
├── client_android/                   # Android app (Kotlin)
│   ├── app/
│   │   ├── build.gradle              # Flavors: free/pro/premium, deps, server URLs
│   │   └── src/main/
│   │       ├── AndroidManifest.xml   # MODIFIED: foreground service + IncomingCallActivity
│   │       ├── cpp/CMakeLists.txt    # JNI bridge to Rust crypto
│   │       ├── java/com/securecall/app/
│   │       │   ├── MainActivity.kt
│   │       │   ├── CallActivity.java          # MODIFIED: outgoing signaling, end call fix
│   │       │   ├── IncomingCallActivity.kt    # NEW: incoming call ringing screen (with accepted flag fix)
│   │       │   ├── SecureCallApplication.kt
│   │       │   ├── net/
│   │       │   │   ├── HeartbeatClient.kt      # MODIFIED: reconnect fix, lastSeen on send
│   │       │   │   ├── WebSocketService.kt     # MODIFIED: foreground service, call signaling, HEARTBEAT_ACK filter
│   │       │   │   └── signal/                 # Call & key exchange message builders
│   │       │   ├── ui/
│   │       │   │   ├── CallsFragment.kt
│   │       │   │   ├── ContactsFragment.kt
│   │       │   │   ├── DialerFragment.kt       # MODIFIED: messenger invite, T9 search, invite dialog fix
│   │       │   │   ├── SettingsFragment.kt     # MODIFIED: background service toggle, clientId display
│   │       │   │   └── adapter/
│   │       │   │       └── ContactAdapter.kt   # MODIFIED: removed row click
│   │       │   ├── config/                     # FeatureProvider, FeatureProviderRegistry
│   │       │   ├── audio/                      # Audio capture/decode/output/jitter
│   │       │   ├── billing/                    # Subscription tiers, licensing
│   │       │   ├── call/                       # CallController
│   │       │   ├── crypto/                     # EphemeralKeyProvider
│   │       │   ├── data/                       # ContactRepository, CallHistoryRepository
│   │       │   ├── ghostnet/                   # Encrypted transport protocol
│   │       │   ├── security/                   # Anti-recording, root detection
│   │       │   ├── fcm/                        # FCM push handler
│   │       │   └── vpn/                        # GhostVpnService
│   │       └── res/
│   │           ├── layout/activity_incoming_call.xml  # NEW: incoming call layout
│   │           ├── values/strings.xml          # MODIFIED: call signaling strings
│   │           ├── values/styles.xml           # MODIFIED: red dial pad
│   │           └── xml/preferences.xml         # MODIFIED: background service, clientId pref
│   └── gradlew                                 # Build: ./gradlew assembleFreeDebug
│
├── backend/                          # Node.js signaling server (Railway)
│   └── signaling/src/
│       ├── server.js                 # MODIFIED: HEARTBEAT_ACK response
│       └── heartbeat.js              # MODIFIED: session timeout 30s→60s
├── core_crypto/                      # Rust crypto library (XChaCha20, X25519, HKDF)
├── docs/                             # Security audit, architecture, wiki pages
├── website/                          # GitHub Pages (neabouli.github.io/stealth)
├── deploy/                           # Deployment scripts
├── deployment/                       # Kubernetes/infrastructure configs
├── marketing/                        # Marketing assets
├── native/                           # Native code modules
├── rom_ghostos/                      # Custom ROM project
└── tools/                            # Test scripts, screenshots
    └── test_screenshots/             # Testing screenshots from S10 + emulator
```

### Key Server URLs (hardcoded in build.gradle for both debug and release)

| Service | URL |
|---------|-----|
| WebSocket Signaling | `wss://protective-healing-production.up.railway.app/signal` |
| STUN | `stun:stun.l.google.com:19302` |
| TURN | `turn:a.relay.metered.ca:443?transport=tcp` |

## Open TODOs

1. ~~**Redeploy Railway signaling server**~~ -- DONE.
2. ~~**Verify connection**~~ -- DONE.
3. ~~**Feature testing on S10**~~ -- DONE.
4. ~~**Commit all changes**~~ -- DONE.
5. ~~**End-to-end call signaling**~~ -- DONE. Full bidirectional call flow tested.
6. ~~**End call button fix**~~ -- DONE. Button works in all states.
7. ~~**Heartbeat/ping-pong fix**~~ -- DONE. Server sends `HEARTBEAT_ACK`, client updates `lastSeen` on send. Connections stable 65+ seconds.
8. ~~**Session timeout fix**~~ -- DONE. Increased to 60s. IncomingCallActivity race condition fixed.
9. **Railway redeploy needed.** Server-side changes (`HEARTBEAT_ACK` in `server.js`, 60s timeout in `heartbeat.js`) need a Railway deployment. If GitHub auto-deploy is enabled, the pushes should trigger it. Otherwise manual redeploy via Railway dashboard.
10. **Real audio transport.** GhostNetTransport is currently stubbed (no real UDP). Wire it to actually send/receive encrypted audio between peers via ICE/TURN.
11. **Contact name resolution.** IncomingCallActivity shows raw clientId (e.g., `android-ded42f50`) instead of the contact name. Look up clientId in ContactRepository to show the saved name.
12. **Firebase setup.** Configure real Firebase credentials to enable FCM push for incoming calls when app is not running.
13. **TURN credential rotation.** Move hardcoded Metered.ca TURN credentials out of `build.gradle` and fetch from server at runtime.

## Known Issues

1. ~~**Railway server 429 block**~~ -- RESOLVED.
2. ~~**Heartbeat timeout cycling**~~ -- RESOLVED. Server sends `HEARTBEAT_ACK`, client updates `lastSeen` on send. Connections stable 65+ seconds. Server-side fix needs Railway redeploy but client-side fix works independently.
3. **Firebase disabled.** Firebase initialization is disabled in the manifest (placeholder credentials). FCM push notifications for incoming calls will not work. Crashlytics and Analytics are also disabled.
4. **Emulator instability.** The Pixel 5 AVD shows frequent "System UI isn't responding" dialogs during testing. App-related testing should prioritize the physical S10 device.
5. **TURN credentials in source.** The Metered.ca TURN username and password are hardcoded in `build.gradle`. These should be rotated and fetched from the server at runtime.
6. **Release keystore in repo.** `securecall-release-key.jks` is in the repo root. Passwords are read from environment variables, but the keystore file itself is committed.
7. **No automated tests.** Unit test dependencies are configured but no tests were added for the new changes.
8. **Android AlertDialog gotcha.** `setMessage()` and `setItems()` are mutually exclusive -- `setMessage` suppresses the item list. Fixed in commit `a0b9872`.
9. **Kotlin/Java interop gotcha.** Kotlin `var` properties auto-generate getters/setters that clash with explicit methods of the same name. Use private backing fields + explicit methods. Java lambdas for Kotlin function types must return `kotlin.Unit.INSTANCE`.
10. **Activity lifecycle race condition.** `IncomingCallActivity.onDestroy()` runs after `CallActivity.onCreate()` when accepting a call. Any callbacks set in `onDestroy()` to `null` will overwrite what `CallActivity.onCreate()` just set. Fixed with `accepted` flag guard.

## Explicit Non-Goals

- Backend changes are minimal: `HEARTBEAT_ACK` response in `server.js` and session timeout increase in `heartbeat.js`. No structural or architectural backend changes.
- No Rust crypto changes. `core_crypto/` is stable and untouched (the `.rustc_info.json` change is a build cache artifact).
- No Pro or Premium flavor builds. Only `free` debug is being built and tested.
- No CI/CD pipeline changes. GitHub Actions workflows exist but are not being modified.
- No new automated tests for this sprint. Testing is manual on physical device.
- No Firebase configuration. Push notifications remain non-functional.
- No localization work. The S10 displays German via system locale; no translation files are being added.

## Code Conventions

- **Language:** Kotlin for all app code. XML for Android resources. Rust for crypto engine.
- **Build:** `./gradlew assembleFreeDebug` from `client_android/` directory. APK output: `app/build/outputs/apk/free/debug/app-free-debug.apk`.
- **Package naming:** `com.securecall.app.<feature>` (e.g., `net`, `ui`, `audio`, `config`, `ghostnet`).
- **Variable/function naming:** camelCase. Classes: PascalCase. Resource IDs: `snake_case` (e.g., `pref_background_service`, `btnCallContact`).
- **Preference keys:** Prefixed with `pref_` (e.g., `pref_dark_mode`, `pref_block_screenshots`, `pref_background_service`).
- **Log tags:** Short descriptive tags -- `WS_SERVICE` for WebSocketService, `HB` for HeartbeatClient, `INCOMING_CALL` for IncomingCallActivity, `CallActivity` for CallActivity.
- **Comments:** Older code has German comments (e.g., `// BACKEND-22: Heartbeat Ueberwachung`). New code uses English. Ticket references like `BACKEND-22`, `PATCH 201` appear throughout.
- **Error handling:** Non-critical failures use `catch (_: Exception) {}`. Critical errors use `Log.e(TAG, message, throwable)`.
- **adb on this machine:** Not in PATH. Full path required: `/Users/gio/Library/Android/sdk/platform-tools/adb`. Emulator: `~/Library/Android/sdk/emulator/emulator`. AVD name: `Pixel_5`.
- **S10 serial:** `RF8N313QMFL`. Package name on device: `com.securecall.app.free`. ClientId: `android-ded42f50`.
- **Emulator:** `emulator-5554`. ClientId: `android-be16bcbf`.
- **App launch command:** `adb -s RF8N313QMFL shell am start -n com.securecall.app.free/com.securecall.app.MainActivity`
- **Contacts storage:** SharedPreferences file `securecall_contacts` with key `contacts_json` (JSON array). Fields: `id`, `name`, `phoneOrId`, `createdAt`, `isPhoneContact`.

## Next Immediate Step

Call signaling and heartbeat are complete and tested. Next priorities:

1. **Verify Railway redeploy** -- Check if the server-side changes (HEARTBEAT_ACK, 60s timeout) are live. If Railway has GitHub auto-deploy on `main`, the two recent pushes should trigger it. Otherwise, manually redeploy via Railway dashboard. After redeploy, check server uptime via `curl https://protective-healing-production.up.railway.app/health` — uptime should be low.
2. **Wire real audio transport** -- GhostNetTransport is stubbed. Connect it to WebRTC or UDP transport with ICE/TURN negotiation through the signaling server. The GHOST_PREPARE → GHOST_ACK flow already exists in the server.
3. **Contact name resolution for incoming calls** -- Look up caller's clientId in ContactRepository to show saved name instead of raw `android-xxxxxxxx`.
4. **Firebase setup** -- Configure real Firebase credentials to enable FCM push notifications for incoming calls when the app is not running.
5. **TURN credential rotation** -- Move hardcoded Metered.ca TURN credentials out of `build.gradle` and fetch them from the server at runtime.
