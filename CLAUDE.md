# Project Handover -- SecureCall (StealthX Platform)

## Current Status

SecureCall is an end-to-end encrypted voice calling app for Android. The monorepo contains the Android client, a Node.js signaling backend (deployed on Railway), a Rust crypto engine, and supporting infrastructure.

**Sprint status:** All changes are **committed and pushed** to `origin/main`. The Railway server has been redeployed and the 429 rate-limit is cleared. Feature testing on the S10 is complete.

**The 7 completed changes (all committed):**

1. **Contact auto-call fix** -- Removed `itemView.setOnClickListener` from `ContactAdapter.kt`. Only the phone icon (`btnCallContact`) now triggers calls; tapping the contact row does nothing. *Tested: confirmed on S10.*
2. **Messenger invite dialog** -- `DialerFragment.kt` now shows a 3-option dialog (Via Messenger, Share Link, Send SMS) when dialing an unknown number. Messenger option checks WhatsApp > Telegram > Signal in order. *Tested: confirmed on S10.*
3. **Invite dialog bug fix** -- Removed `.setMessage()` from the invite AlertDialog because Android's `setMessage()` suppresses `setItems()` list items. Moved the invite text into `.setTitle()` and shortened it to "Invite %s to SecureCall" to avoid truncation. *(Commit `a0b9872`)*
4. **Foreground background service** -- `WebSocketService` is now a foreground service (`foregroundServiceType="phoneCall"`) with a persistent silent notification. Controlled by `pref_background_service` toggle in Settings (default ON). *Tested: toggle visible and enabled on S10.*
5. **Red dial pad digits** -- Added `android:textColor="@color/stealthx_red"` (#D32F2F) to `Widget.SecureCall.DialButton` style. *Tested: confirmed red digits on S10.*
6. **README transparency section** -- Added Third-Party Services table to `README.md` listing Railway, Metered.ca, Google STUN, Firebase, and GitHub Pages with data access descriptions.
7. **WebSocket reconnect-loop fix** -- `HeartbeatClient.kt` now uses a shared `OkHttpClient`, an `isConnecting` guard to prevent duplicate connections, and 30s max backoff. `WebSocketService.onError()` no longer calls `scheduleReconnect()` (only `HeartbeatClient` owns reconnect). *Tested: WebSocket connects successfully, registers client ID.*

**Testing:** The APK (`assembleFreeDebug`) was installed and tested on a Samsung Galaxy S10 (serial `RF8N313QMFL`). All features verified working. WebSocket connects and registers successfully. A Pixel 5 emulator AVD exists but was unstable (System UI crashes).

## Architecture Decisions

- **Monorepo structure:** Android client, backend, Rust crypto, website, and docs all live in one repo. The Android project root is `client_android/`.
- **Three-tier product flavors:** `free` (billing, limited features), `pro` (unlimited, cert pinning, root detection), `premium` (max security, hardware keystore, all detections). Each has its own `applicationIdSuffix` and `buildConfigField` flags. The `free` debug variant is used for development and testing.
- **Rust crypto via JNI:** All cryptographic operations (XChaCha20-Poly1305, X25519, HKDF-SHA256) run in a native Rust library (`core_crypto/`) accessed via JNI through a C++ CMake bridge (`src/main/cpp/CMakeLists.txt`). No Java crypto APIs are used.
- **GhostNet transport protocol:** Custom encrypted voice transport layer in `com.securecall.app.ghostnet` with its own handshake, session management, frame serialization, and UDP transport. Separate from the WebSocket signaling layer.
- **WebSocket signaling:** `HeartbeatClient` manages the OkHttp WebSocket connection with keepalive pings (8s) and exponential backoff reconnect (1s -> 30s max). `WebSocketService` wraps it as a foreground service and handles message routing (call signaling, key exchange, subscription verification, GHOST protocol).
- **Single reconnect owner:** Only `HeartbeatClient.onFailure()` triggers reconnect. `WebSocketService.onError()` notifies callbacks but does NOT reconnect. This prevents the double-reconnect exponential explosion bug.
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
│   │       ├── AndroidManifest.xml   # Modified: FOREGROUND_SERVICE permissions
│   │       ├── cpp/CMakeLists.txt    # JNI bridge to Rust crypto
│   │       ├── java/com/securecall/app/
│   │       │   ├── MainActivity.kt
│   │       │   ├── CallActivity.kt
│   │       │   ├── SecureCallApplication.kt
│   │       │   ├── net/
│   │       │   │   ├── HeartbeatClient.kt      # MODIFIED: reconnect fix
│   │       │   │   ├── WebSocketService.kt     # MODIFIED: foreground service + reconnect fix
│   │       │   │   └── signal/                 # Call & key exchange message builders
│   │       │   ├── ui/
│   │       │   │   ├── CallsFragment.kt
│   │       │   │   ├── ContactsFragment.kt
│   │       │   │   ├── DialerFragment.kt       # MODIFIED: messenger invite, T9 search, invite dialog fix
│   │       │   │   ├── SettingsFragment.kt     # MODIFIED: background service toggle
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
│   │           ├── values/strings.xml          # MODIFIED: new string resources
│   │           ├── values/styles.xml           # MODIFIED: red dial pad
│   │           └── xml/preferences.xml         # MODIFIED: background service pref
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

1. ~~**Redeploy Railway signaling server**~~ -- DONE. Server redeployed, 429 cleared.
2. ~~**Verify connection**~~ -- DONE. WebSocket connects and registers (`android-ded42f50`).
3. ~~**Feature testing on S10**~~ -- DONE. All features verified: red dial pad, contact tap no-op, invite dialog with 3 options, background service toggle.
4. ~~**Commit all changes**~~ -- DONE. All changes committed and pushed to `origin/main`.
5. **Interaction testing (optional).** Call between S10 and emulator (if emulator is stable). Test background incoming call wakeup.
6. **Optional backoff improvement.** After 5+ consecutive failures, increase delay beyond 30s (e.g., 5 minutes) to be more server-friendly during extended outages.
7. **End-to-end call test.** Requires a second device registered with the signaling server. Verify call setup, key exchange, and encrypted audio work.

## Known Issues

1. ~~**Railway server 429 block**~~ -- RESOLVED. Server redeployed, client connects normally.
2. **Firebase disabled.** Firebase initialization is disabled in the manifest (placeholder credentials). FCM push notifications for incoming calls will not work. Crashlytics and Analytics are also disabled.
3. **Emulator instability.** The Pixel 5 AVD shows frequent "System UI isn't responding" dialogs and has crashed/disconnected during testing sessions. App-related testing should prioritize the physical S10 device.
4. **TURN credentials in source.** The Metered.ca TURN username and password are hardcoded in `build.gradle` (both debug and release). These should be rotated and ideally fetched from the server at runtime.
5. **Release keystore in repo.** `securecall-release-key.jks` is in the repo root. Passwords are read from environment variables or gradle properties, but the keystore file itself is committed.
6. **No automated tests for new features.** Unit tests exist (`testImplementation` dependencies configured) but no tests were added for the new changes.
7. **Android AlertDialog gotcha.** `AlertDialog.Builder.setMessage()` and `.setItems()` are mutually exclusive -- `setMessage` suppresses the item list. This was the cause of the invite dialog bug (fixed in `a0b9872`).

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
- **Log tags:** Short descriptive tags -- `WS_SERVICE` for WebSocketService, `HB` for HeartbeatClient, `WS_SERVICE` for call signaling.
- **Comments:** Older code has German comments (e.g., `// BACKEND-22: Heartbeat Ueberwachung`). New code uses English. Ticket references like `BACKEND-22`, `PATCH 201` appear throughout.
- **Error handling:** Non-critical failures use `catch (_: Exception) {}`. Critical errors use `Log.e(TAG, message, throwable)`.
- **adb on this machine:** Not in PATH. Full path required: `/Users/gio/Library/Android/sdk/platform-tools/adb`. Emulator: `~/Library/Android/sdk/emulator/emulator`. AVD name: `Pixel_5`.
- **S10 serial:** `RF8N313QMFL`. Package name on device: `com.securecall.app.free`.
- **App launch command:** `adb -s RF8N313QMFL shell am start -n com.securecall.app.free/com.securecall.app.MainActivity`

## Next Immediate Step

All sprint items are complete, tested, committed, and pushed. Next priorities:

1. **End-to-end call test** -- Register a second device with the signaling server and attempt a call between two clients to verify the full call flow (signaling, key exchange, GhostNet encrypted audio).
2. **Optional backoff improvement** -- After 5+ consecutive reconnect failures, increase delay beyond 30s (e.g., 5 minutes) to be more server-friendly during extended outages.
3. **Firebase setup** -- Configure real Firebase credentials to enable FCM push notifications for incoming calls when the app is not running.
4. **TURN credential rotation** -- Move hardcoded Metered.ca TURN credentials out of `build.gradle` and fetch them from the server at runtime.
