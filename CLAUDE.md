# Project Handover -- SecureCall (StealthX Platform)

## Current Status

SecureCall is an end-to-end encrypted voice calling app for Android. The monorepo contains the Android client, a Node.js signaling backend (deployed on Railway), a Rust crypto engine, and supporting infrastructure.

**Sprint status:** All changes are **committed and pushed** to `origin/main`. Real peer-to-peer audio transport is working — Opus-encoded voice relayed through the signaling WebSocket, tested bidirectionally between S10 and emulator with live microphone audio.

**The 20 completed changes (all committed):**

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
14. **Real audio transport** *(Commit `228c30c`)* -- Wired peer-to-peer voice audio through the signaling WebSocket binary relay. See details below.
15. **Runtime RECORD_AUDIO permission** *(Commit `4fe8c77`)* -- CallActivity now requests microphone permission at runtime before starting audio capture. If permission is denied, shows a Toast and skips capture (call still connects, just no outgoing audio). Permission result handled in `onRequestPermissionsResult()`.
16. **endCall() idempotency guard** *(Commit `10fe6b0`)* -- Added `isEnding` boolean guard to prevent `endCall()` from firing multiple times. Also clears the `onCallError` callback after the first error to prevent repeated error-triggered endCall scheduling. Previously, server `rate_limited` errors would each schedule a separate `postDelayed(this::endCall, 3000)`, causing dozens of duplicate teardowns.
17. **E2E audio encryption** *(Commit `a97faa0`)* -- X25519 key exchange piggybacked on CALL_INVITE/CALL_ACCEPT signaling, session key derived via HKDF-SHA256, every Opus frame encrypted with XChaCha20-Poly1305 (40 bytes overhead: 24B nonce + 16B auth tag). Server forwards `pubKey` field transparently. Graceful fallback to unencrypted if native crypto unavailable. Key material zeroed on call end. See details below.
18. **Earpiece audio routing** *(Commit `0096e74`)* -- `GhostAudioPlayer` changed from `STREAM_MUSIC` to `STREAM_VOICE_CALL` for proper earpiece routing and voice call volume controls.
19. **Contact name resolution for incoming calls** *(Commit `0096e74`)* -- `IncomingCallActivity` looks up caller's `clientId` in `ContactRepository` by matching `phoneOrId`. Shows saved contact name on ringing screen and passes it through to `CallActivity`. Falls back to raw clientId if no contact matches.
20. **Jitter buffer** *(Commit `a6cd8c1`)* -- Wired `JitterBuffer` between OpusDecoder and GhostAudioPlayer. Decoded PCM frames are buffered (max 32, ShortArray) and drained by a dedicated playout thread at a steady 20ms rate. 60ms pre-buffer (3 frames) before playback starts. Writes silence on buffer underrun.

**End-to-end call signaling details (change #8):**
- `WebSocketService.kt`: Added call signaling callbacks (`_onCallAccepted`, `_onCallEnded`, `_onCallError`), session tracking, incoming call activity launch, message parsing for CALL_INVITE/CALL_INVITE_ACK/CALL_ACCEPT/CALL_END/ERROR. Uses private backing fields with explicit setter methods for Java interop.
- `IncomingCallActivity.kt` (NEW): Ringing screen with accept/decline FABs. Accept sends CALL_ACCEPT + launches CallActivity. Decline sends CALL_END. Auto-closes if caller hangs up during ringing.
- `activity_incoming_call.xml` (NEW): Dark-themed layout with caller avatar, name, and accept/decline buttons.
- `CallActivity.java`: Three-way branching (fromNotification/isIncoming/outgoing). Outgoing sends CALL_INVITE, waits for CALL_ACCEPT to start transport. `endCall()` sends CALL_END signaling and clears all callbacks. Extracted `startTransportAndTimer()` helper.
- `AndroidManifest.xml`: Registered IncomingCallActivity with `showOnLockScreen` and `turnScreenOn`.
- `strings.xml`: Added `incoming_call_title`, `call_accept`, `call_decline`, `settings_client_id`.
- `preferences.xml` + `SettingsFragment.kt`: Tap-to-copy SecureCall ID in Account settings.

**Real audio transport details (change #14):**
- `HeartbeatClient.kt`: Added `onBinaryMessage(ByteArray)` to Listener interface, `sendBinary(ByteArray)` method, and `onMessage(WebSocket, ByteString)` override for binary WebSocket frames.
- `WebSocketService.kt`: Added `onBinaryMessage()` handler that decodes Opus → PCM via `OpusDecoder` and plays via `GhostAudioPlayer`. Added `sendBinary()` passthrough and `stopAudioPlayback()` cleanup. Audio pipeline is lazy-initialized on first binary frame received.
- `AudioCapturePlaceholder.java`: Redirected audio output from `GhostNetWebSocketClient` (separate unused WebSocket) to `WebSocketService.sendBinary()` (signaling WebSocket with server binary relay).
- `CallActivity.java`: Replaced stubbed `GhostNetTransport` with real `AudioCapturePlaceholder`. Audio capture starts when call becomes active (2s after connect). Mute button stops/starts capture. Audio cleanup in `endCall()` and `onDestroy()`.
- **Audio flow:** Mic → AudioRecord (48kHz mono) → OpusEncoder (32kbps, native JNI) → WebSocket binary → Server `forwardBinaryToPeer()` → Peer WebSocket → OpusDecoder (native JNI) → JitterBuffer (60ms prefill, 20ms playout) → GhostAudioPlayer (AudioTrack) → Earpiece
- **No server changes needed** -- `server.js` already relays binary frames between active session peers.

**E2E audio encryption details (change #17):**
- `server.js`: Added `pubKey: msg.pubKey` to forwarded CALL_INVITE and CALL_ACCEPT objects (2 lines). Server never reads the key — just passes it through.
- `WebSocketService.kt`: Added crypto state fields (`localPrivKey`, `remotePubKey`, `sessionKey`). `sendCallInvite()` generates X25519 keypair and includes pubKey in JSON. CALL_INVITE handler extracts and stores caller's public key. `sendCallAccept()` generates keypair, derives session key (callee), includes pubKey. CALL_ACCEPT handler extracts callee's pubKey and derives session key (caller). `sendBinary()` encrypts with `CoreCrypto.encrypt()` before sending. `onBinaryMessage()` decrypts with `CoreCrypto.decrypt()` before Opus decode (drops frame on decrypt failure). `clearSession()` zeroes all key material via `ByteArray.fill(0)`.
- **Key exchange flow:** Caller sends CALL_INVITE with X25519 pubKey → Callee stores it, generates own keypair, derives session key from privB+pubA, sends CALL_ACCEPT with pubKey → Caller derives session key from privA+pubB. Both sides have identical session keys before any audio flows.
- **Fallback:** If `CoreCrypto.isNativeAvailable()` returns false, audio is sent/received unencrypted (log warning).

**Testing:** APK tested on S10 (serial `RF8N313QMFL`, clientId `android-ded42f50`) and Pixel 5 emulator (clientId `android-33068922`). Full bidirectional call signaling, audio, and E2E encryption verified:
- **S10 → Emulator:** CALL_INVITE → IncomingCallActivity → Accept → CALL_ACCEPT → CallActivity with timer → End call from S10 → Emulator receives CALL_END and closes
- **Emulator → S10:** CALL_INVITE → IncomingCallActivity on S10 → Accept → CALL_ACCEPT → CallActivity with timer → End call from emulator → S10 receives CALL_END and closes
- **Bidirectional audio:** Both devices show `AUDIO_CAPTURE: Capture thread started` (mic recording) and `AUDIO_PLAYER: write(): wrote=960 samples` (receiving peer audio). Full pipeline: OpusEncoder init → capture thread → binary WebSocket send → remote decode → AudioTrack playback.
- **End call works in all states:** ringing, connecting, and active. Both caller and callee can end.
- **Remote hangup:** When one side ends the call, the other side receives CALL_END and automatically closes CallActivity.
- **Heartbeat stability:** Connections remain stable for 65+ seconds with zero timeouts or reconnects.
- **Error handling:** `peer_not_found` (target offline), `peer_disconnected` (caller dropped), `session_not_found` (stale session) all handled correctly
- **Runtime permission:** RECORD_AUDIO is now requested at runtime when the call goes active. Tested: permission revoked → call started → dialog appeared → granted → audio capture started immediately.
- **endCall() guard:** Verified `endCall()` fires exactly once in all scenarios: error (peer_not_found), rate limiting (multiple server errors), and normal call flow. Proximity sensor does NOT trigger endCall — it only manages the wake lock.
- **E2E encryption:** Full key exchange verified — caller generates X25519 keypair and includes pubKey in CALL_INVITE, callee stores it and derives session key on accept, caller derives matching session key from CALL_ACCEPT pubKey. Both sides log `E2E session key derived`. Bidirectional encrypted audio: all Opus frames encrypted/decrypted successfully (zero `E2E decrypt failed` errors). Tested with native crypto on both arm64-v8a (S10) and x86_64 (emulator).

## Architecture Decisions

- **Monorepo structure:** Android client, backend, Rust crypto, website, and docs all live in one repo. The Android project root is `client_android/`.
- **Three-tier product flavors:** `free` (billing, limited features), `pro` (unlimited, cert pinning, root detection), `premium` (max security, hardware keystore, all detections). Each has its own `applicationIdSuffix` and `buildConfigField` flags. The `free` debug variant is used for development and testing.
- **Rust crypto via JNI:** All cryptographic operations (XChaCha20-Poly1305, X25519, HKDF-SHA256) run in a native Rust library (`core_crypto/`) accessed via JNI through a C++ CMake bridge (`src/main/cpp/CMakeLists.txt`). No Java crypto APIs are used.
- **GhostNet transport protocol:** Custom encrypted voice transport layer in `com.securecall.app.ghostnet` with its own handshake, session management, frame serialization. The old `GhostNetTransport` class was stubbed (no real network). Real audio now bypasses it entirely, using the signaling WebSocket's binary relay instead.
- **WebSocket signaling:** `HeartbeatClient` manages the OkHttp WebSocket connection with keepalive pings (8s app-level HEARTBEAT + 5s OkHttp native ping) and exponential backoff reconnect (1s -> 30s max). `WebSocketService` wraps it as a foreground service and handles message routing (call signaling, key exchange, subscription verification, GHOST protocol). Heartbeat timeout is 30s. Server responds with `HEARTBEAT_ACK` to keep client `lastSeen` fresh. Client also updates `lastSeen` on successful send (writable socket = alive).
- **Single reconnect owner:** Only `HeartbeatClient.onFailure()` triggers reconnect. `WebSocketService.onError()` notifies callbacks but does NOT reconnect. This prevents the double-reconnect exponential explosion bug.
- **Server session/client timeout:** 60s (in `heartbeat.js`). Server sends native `ws.ping()` every 5s, tracks `lastSeen` via pong and message handlers. Sessions and clients that don't communicate for 60s are terminated.
- **Call signaling flow:** Outgoing: CallActivity sends CALL_INVITE via WebSocketService → server routes to target → target's WebSocketService launches IncomingCallActivity → user accepts → CALL_ACCEPT sent back → caller's CallActivity starts audio capture. End call: either side sends CALL_END → server forwards and cleans up session → remote side receives CALL_END and auto-closes. All callbacks use private backing fields (`_onCallAccepted`, etc.) with explicit setter methods to avoid Kotlin/Java JVM signature clashes. IncomingCallActivity uses an `accepted` flag to prevent clearing `onCallEnded` when transitioning to CallActivity.
- **Audio transport via WebSocket binary relay:** Audio uses the same signaling WebSocket (not a separate connection). `AudioCapturePlaceholder` captures mic at 48kHz mono, encodes with native Opus (32kbps via JNI), sends as binary WebSocket frames through `WebSocketService.sendBinary()`. Server's `forwardBinaryToPeer()` routes binary frames to the peer in the active session. Receiving side: `HeartbeatClient.onMessage(ByteString)` → `WebSocketService.onBinaryMessage()` → `OpusDecoder.decode()` → `JitterBuffer.push()` → playout thread pops every 20ms → `GhostAudioPlayer.write()`. All audio frames are E2E encrypted with XChaCha20-Poly1305 (40 bytes overhead per frame). Server relay adds ~50-200ms latency vs direct P2P.
- **Jitter buffer:** `JitterBuffer` (singleton, synchronized) buffers decoded PCM `ShortArray` frames (max 32). A dedicated `jitter-playout` thread drains one frame every 20ms (matching Opus 960-sample frame duration at 48kHz). Pre-buffers 3 frames (60ms) before starting playback. On underrun, writes 960 zero samples (silence) to maintain steady output. Thread and buffer are cleared in `stopAudioPlayback()`.
- **E2E audio encryption:** X25519 public keys are piggybacked on existing CALL_INVITE/CALL_ACCEPT messages (no extra round-trips). Both sides derive a shared session key via X25519 DH + HKDF-SHA256 using `CoreCrypto.deriveSessionKey()`. Every Opus frame is encrypted with `CoreCrypto.encrypt(sessionKey, data)` → [nonce(24B)|ciphertext|tag(16B)] before sending, and decrypted with `CoreCrypto.decrypt()` on receive. Session keys are derived before any audio flows. Key material (`localPrivKey`, `sessionKey`) is zeroed via `ByteArray.fill(0)` in `clearSession()`. Graceful fallback: if `CoreCrypto.isNativeAvailable()` is false, audio passes through unencrypted.
- **endCall() idempotency:** `endCall()` uses an `isEnding` boolean guard to ensure it runs at most once per call session. The `onCallError` callback is cleared after first invocation (`ws.setOnCallError(null)`) to prevent server error floods (e.g., `rate_limited`) from scheduling multiple delayed `endCall()` calls. The proximity sensor does NOT call `endCall()` — it only manages `PROXIMITY_SCREEN_OFF_WAKE_LOCK`.
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
│   │       │   ├── CallActivity.java          # MODIFIED: signaling, audio, runtime permission, endCall guard
│   │       │   ├── IncomingCallActivity.kt    # NEW: incoming call ringing screen (accepted flag fix, contact name resolution)
│   │       │   ├── SecureCallApplication.kt
│   │       │   ├── net/
│   │       │   │   ├── HeartbeatClient.kt      # MODIFIED: reconnect fix, lastSeen on send, binary WS support
│   │       │   │   ├── WebSocketService.kt     # MODIFIED: foreground, call signaling, audio, E2E encryption
│   │       │   │   └── signal/                 # Call & key exchange message builders
│   │       │   ├── ui/
│   │       │   │   ├── CallsFragment.kt
│   │       │   │   ├── ContactsFragment.kt
│   │       │   │   ├── DialerFragment.kt       # MODIFIED: messenger invite, T9 search, invite dialog fix
│   │       │   │   ├── SettingsFragment.kt     # MODIFIED: background service toggle, clientId display
│   │       │   │   └── adapter/
│   │       │   │       └── ContactAdapter.kt   # MODIFIED: removed row click
│   │       │   ├── config/                     # FeatureProvider, FeatureProviderRegistry
│   │       │   ├── audio/                      # AudioCapturePlaceholder (MODIFIED: sends via WS), Opus codec
│   │       │   │   └── jitter/JitterBuffer.kt # MODIFIED: ShortArray PCM buffer with playout support
│   │       │   ├── ghostnet/media/playback/
│   │       │   │   └── GhostAudioPlayer.kt   # MODIFIED: STREAM_VOICE_CALL
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
│       ├── server.js                 # MODIFIED: HEARTBEAT_ACK, pubKey forwarding in call signaling
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
9. ~~**Railway redeploy**~~ -- DONE. Auto-deployed via GitHub push.
10. ~~**Real audio transport**~~ -- DONE. Opus-encoded audio relayed via signaling WebSocket binary frames. Tested bidirectionally with live mic audio.
11. ~~**Runtime RECORD_AUDIO permission.**~~ DONE. CallActivity requests RECORD_AUDIO at runtime before starting audio capture. Commit `4fe8c77`.
12. ~~**Contact name resolution.**~~ DONE. IncomingCallActivity resolves caller clientId via ContactRepository. Commit `0096e74`.
13. ~~**E2E audio encryption.**~~ DONE. X25519 key exchange + XChaCha20-Poly1305 per-frame encryption. Commit `a97faa0`.
14. **Firebase setup.** Configure real Firebase credentials to enable FCM push for incoming calls when app is not running.
15. **TURN credential rotation.** Move hardcoded Metered.ca TURN credentials out of `build.gradle` and fetch from server at runtime.
16. **Direct P2P audio (WebRTC).** Current audio goes through the server relay (~50-200ms added latency). Migrate to WebRTC data channels or direct UDP with ICE/TURN for lower latency. Server already supports WEBRTC_OFFER/ANSWER/ICE_CANDIDATE relay.
17. ~~**Audio stream type.**~~ DONE. GhostAudioPlayer changed to `STREAM_VOICE_CALL`. Commit `0096e74`.
18. ~~**Jitter buffer.**~~ DONE. JitterBuffer wired between OpusDecoder and GhostAudioPlayer with 60ms prefill playout thread. Commit `a6cd8c1`.

## Known Issues

1. ~~**Railway server 429 block**~~ -- RESOLVED.
2. ~~**Heartbeat timeout cycling**~~ -- RESOLVED. Server sends `HEARTBEAT_ACK`, client updates `lastSeen` on send. Connections stable 65+ seconds. Server-side fix needs Railway redeploy but client-side fix works independently.
3. **Firebase disabled.** Firebase initialization is disabled in the manifest (placeholder credentials). FCM push notifications for incoming calls will not work. Crashlytics and Analytics are also disabled.
4. **Emulator instability.** The Pixel 5 AVD (Android 16 API 36) shows frequent "System UI isn't responding" dialogs during testing. `eth0` often stays DOWN after boot — fix with `su 0 ndc network create 100 && ndc network interface add 100 eth0 && ndc network default set 100` or `su 0 ip link set eth0 down && ip link set eth0 up` then add IP/route manually. App-related testing should prioritize the physical S10 device.
5. **TURN credentials in source.** The Metered.ca TURN username and password are hardcoded in `build.gradle`. These should be rotated and fetched from the server at runtime.
6. **Release keystore in repo.** `securecall-release-key.jks` is in the repo root. Passwords are read from environment variables, but the keystore file itself is committed.
7. **No automated tests.** Unit test dependencies are configured but no tests were added for the new changes.
8. ~~**No runtime mic permission request.**~~ RESOLVED. CallActivity now requests RECORD_AUDIO at runtime. Commit `4fe8c77`.
9. **Android AlertDialog gotcha.** `setMessage()` and `setItems()` are mutually exclusive -- `setMessage` suppresses the item list. Fixed in commit `a0b9872`.
10. **Kotlin/Java interop gotcha.** Kotlin `var` properties auto-generate getters/setters that clash with explicit methods of the same name. Use private backing fields + explicit methods. Java lambdas for Kotlin function types must return `kotlin.Unit.INSTANCE`.
11. **Activity lifecycle race condition.** `IncomingCallActivity.onDestroy()` runs after `CallActivity.onCreate()` when accepting a call. Any callbacks set in `onDestroy()` to `null` will overwrite what `CallActivity.onCreate()` just set. Fixed with `accepted` flag guard.

## Explicit Non-Goals

- Backend changes are minimal: `HEARTBEAT_ACK` response and `pubKey` forwarding in `server.js`, session timeout increase in `heartbeat.js`. No structural or architectural backend changes.
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
- **Emulator:** `emulator-5554`. ClientId changes on each wipe (last: `android-33068922`).
- **App launch command:** `adb -s RF8N313QMFL shell am start -n com.securecall.app.free/com.securecall.app.MainActivity`
- **Contacts storage:** SharedPreferences file `securecall_contacts` with key `contacts_json` (JSON array). Fields: `id`, `name`, `phoneOrId`, `createdAt`, `isPhoneContact`.

## Next Immediate Step

Call signaling, heartbeat, real audio transport, E2E encryption, earpiece routing, contact name resolution, jitter buffer, runtime permissions, and call lifecycle are complete and tested. Next priorities:

1. **Direct P2P audio (WebRTC)** -- Migrate from server relay to WebRTC data channels for lower latency. Server already has WEBRTC_OFFER/ANSWER/ICE_CANDIDATE support.
2. **Firebase setup** -- Configure real Firebase credentials to enable FCM push for incoming calls when app is not running.
3. **TURN credential rotation** -- Move hardcoded Metered.ca TURN credentials out of `build.gradle` and fetch from server at runtime.
