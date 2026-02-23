# Project Handover -- SecureCall (StealthX Platform)

## Current Status

SecureCall is an end-to-end encrypted voice calling app for Android. The monorepo contains the Android client, a Node.js signaling backend (deployed on Railway), a Rust crypto engine, and supporting infrastructure.

**Sprint status:** All changes are **committed and pushed** to `origin/main`. End-to-end call signaling has been implemented and tested between S10 and Pixel 5 emulator.

**The 10 completed changes (all committed):**

1. **Contact auto-call fix** -- Removed `itemView.setOnClickListener` from `ContactAdapter.kt`. Only the phone icon (`btnCallContact`) now triggers calls; tapping the contact row does nothing. *Tested on S10.*
2. **Messenger invite dialog** -- `DialerFragment.kt` now shows a 3-option dialog (Via Messenger, Share Link, Send SMS) when dialing an unknown number. *Tested on S10.*
3. **Invite dialog bug fix** -- Removed `.setMessage()` from the invite AlertDialog (suppresses `setItems()`). Moved text into `.setTitle()`. *(Commit `a0b9872`)*
4. **Foreground background service** -- `WebSocketService` is now a foreground service with persistent notification. *Tested on S10.*
5. **Red dial pad digits** -- `Widget.SecureCall.DialButton` style uses `@color/stealthx_red`. *Tested on S10.*
6. **README transparency section** -- Third-Party Services table added to `README.md`.
7. **WebSocket reconnect-loop fix** -- Shared `OkHttpClient`, `isConnecting` guard, 30s max backoff.
8. **End-to-end call signaling** *(Commit `ac6f982`)* -- Full call flow wired: CALL_INVITE → IncomingCallActivity → CALL_ACCEPT → CallActivity with transport. See details below.
9. **End call button fix** *(Commit `bc1cc52`)* -- Removed `isCallActive` guard so end call works during ringing/connecting. Shows red end-call icon immediately for outgoing calls.
10. **Heartbeat timeout increase** -- Changed from 15s to 30s to reduce connection cycling (server doesn't respond to WebSocket pings).

**End-to-end call signaling details (change #8):**
- `WebSocketService.kt`: Added call signaling callbacks (`_onCallAccepted`, `_onCallEnded`, `_onCallError`), session tracking, incoming call activity launch, message parsing for CALL_INVITE/CALL_INVITE_ACK/CALL_ACCEPT/CALL_END/ERROR. Uses private backing fields with explicit setter methods for Java interop.
- `IncomingCallActivity.kt` (NEW): Ringing screen with accept/decline FABs. Accept sends CALL_ACCEPT + launches CallActivity. Decline sends CALL_END. Auto-closes if caller hangs up during ringing.
- `activity_incoming_call.xml` (NEW): Dark-themed layout with caller avatar, name, and accept/decline buttons.
- `CallActivity.java`: Three-way branching (fromNotification/isIncoming/outgoing). Outgoing sends CALL_INVITE, waits for CALL_ACCEPT to start transport. `endCall()` sends CALL_END signaling and clears all callbacks. Extracted `startTransportAndTimer()` helper.
- `AndroidManifest.xml`: Registered IncomingCallActivity with `showOnLockScreen` and `turnScreenOn`.
- `strings.xml`: Added `incoming_call_title`, `call_accept`, `call_decline`, `settings_client_id`.
- `preferences.xml` + `SettingsFragment.kt`: Tap-to-copy SecureCall ID in Account settings.

**Testing:** APK tested on S10 (serial `RF8N313QMFL`, clientId `android-ded42f50`) and Pixel 5 emulator (clientId `android-be16bcbf`). End-to-end call signaling verified:
- S10 calls emulator → CALL_INVITE delivered → IncomingCallActivity shows "Incoming Secure Call" with caller ID
- Accept → CALL_ACCEPT sent → CallActivity launched with timer running
- End call button works during ringing → CALL_END sent → CALL_END_ACK received → activity closes
- Error handling: `peer_not_found` (target offline), `peer_disconnected` (caller dropped), `session_not_found` (stale session) all handled correctly

## Architecture Decisions

- **Monorepo structure:** Android client, backend, Rust crypto, website, and docs all live in one repo. The Android project root is `client_android/`.
- **Three-tier product flavors:** `free` (billing, limited features), `pro` (unlimited, cert pinning, root detection), `premium` (max security, hardware keystore, all detections). Each has its own `applicationIdSuffix` and `buildConfigField` flags. The `free` debug variant is used for development and testing.
- **Rust crypto via JNI:** All cryptographic operations (XChaCha20-Poly1305, X25519, HKDF-SHA256) run in a native Rust library (`core_crypto/`) accessed via JNI through a C++ CMake bridge (`src/main/cpp/CMakeLists.txt`). No Java crypto APIs are used.
- **GhostNet transport protocol:** Custom encrypted voice transport layer in `com.securecall.app.ghostnet` with its own handshake, session management, frame serialization, and UDP transport. Separate from the WebSocket signaling layer.
- **WebSocket signaling:** `HeartbeatClient` manages the OkHttp WebSocket connection with keepalive pings (8s) and exponential backoff reconnect (1s -> 30s max). `WebSocketService` wraps it as a foreground service and handles message routing (call signaling, key exchange, subscription verification, GHOST protocol). Heartbeat timeout is 30s.
- **Single reconnect owner:** Only `HeartbeatClient.onFailure()` triggers reconnect. `WebSocketService.onError()` notifies callbacks but does NOT reconnect. This prevents the double-reconnect exponential explosion bug.
- **Call signaling flow:** Outgoing: CallActivity sends CALL_INVITE via WebSocketService → server routes to target → target's WebSocketService launches IncomingCallActivity → user accepts → CALL_ACCEPT sent back → caller's CallActivity starts GhostNetTransport. End call: either side sends CALL_END → server forwards and cleans up session. All callbacks use private backing fields (`_onCallAccepted`, etc.) with explicit setter methods to avoid Kotlin/Java JVM signature clashes.
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
│   │       │   ├── IncomingCallActivity.kt    # NEW: incoming call ringing screen
│   │       │   ├── SecureCallApplication.kt
│   │       │   ├── net/
│   │       │   │   ├── HeartbeatClient.kt      # MODIFIED: reconnect fix
│   │       │   │   ├── WebSocketService.kt     # MODIFIED: foreground service, call signaling callbacks
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
5. ~~**End-to-end call signaling**~~ -- DONE. Full CALL_INVITE → IncomingCallActivity → CALL_ACCEPT → CallActivity flow implemented and tested between S10 and emulator.
6. ~~**End call button fix**~~ -- DONE. Button works in all states (ringing, connecting, active).
7. **Server-side ping/pong.** The Railway signaling server does not respond to WebSocket pings, causing heartbeat timeouts every ~30s. Adding server-side pong handling would eliminate the reconnect cycling.
8. **Optional backoff improvement.** After 5+ consecutive failures, increase delay beyond 30s (e.g., 5 minutes) to be more server-friendly during extended outages.
9. **Real audio transport.** GhostNetTransport is currently stubbed (no real UDP). Wire it to actually send/receive encrypted audio between peers via ICE/TURN.
10. **Contact name resolution.** IncomingCallActivity shows raw clientId (e.g., `android-ded42f50`) instead of the contact name. Look up clientId in ContactRepository to show the saved name.

## Known Issues

1. ~~**Railway server 429 block**~~ -- RESOLVED.
2. **Heartbeat timeout cycling.** The Railway signaling server does not respond to WebSocket pings, so the client's heartbeat monitor triggers a timeout every ~30s and reconnects. Both devices cycle connections but re-register each time. Call signaling must happen while both peers are connected (within the ~30s window). This is the most impactful issue for call reliability.
3. **Firebase disabled.** Firebase initialization is disabled in the manifest (placeholder credentials). FCM push notifications for incoming calls will not work. Crashlytics and Analytics are also disabled.
4. **Emulator instability.** The Pixel 5 AVD shows frequent "System UI isn't responding" dialogs during testing. App-related testing should prioritize the physical S10 device.
5. **TURN credentials in source.** The Metered.ca TURN username and password are hardcoded in `build.gradle`. These should be rotated and fetched from the server at runtime.
6. **Release keystore in repo.** `securecall-release-key.jks` is in the repo root. Passwords are read from environment variables, but the keystore file itself is committed.
7. **No automated tests.** Unit test dependencies are configured but no tests were added for the new changes.
8. **Android AlertDialog gotcha.** `setMessage()` and `setItems()` are mutually exclusive -- `setMessage` suppresses the item list. Fixed in commit `a0b9872`.
9. **Kotlin/Java interop gotcha.** Kotlin `var` properties auto-generate getters/setters that clash with explicit methods of the same name. Use private backing fields + explicit methods. Java lambdas for Kotlin function types must return `kotlin.Unit.INSTANCE`.

## Explicit Non-Goals

- No backend code changes. The `backend/` directory is not modified.
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

Call signaling is complete and tested. Next priorities:

1. **Fix server-side ping/pong** -- The Railway signaling server (`backend/signaling/src/server.js`) does not respond to WebSocket pings. Adding pong handling would eliminate the ~30s reconnect cycling and make calls far more reliable. This is the highest-impact fix.
2. **Wire real audio transport** -- GhostNetTransport is stubbed. Connect it to WebRTC or UDP transport with ICE/TURN negotiation through the signaling server.
3. **Contact name resolution for incoming calls** -- Look up caller's clientId in ContactRepository to show saved name instead of raw `android-xxxxxxxx`.
4. **Firebase setup** -- Configure real Firebase credentials to enable FCM push notifications for incoming calls when the app is not running.
5. **TURN credential rotation** -- Move hardcoded Metered.ca TURN credentials out of `build.gradle` and fetch them from the server at runtime.
