# Developer Handover — SecureCall (StealthX Platform)

> Last updated: 2026-03-18
> Author: Development Team

This document contains everything a new developer needs to understand, build, test, and continue development on SecureCall. Read it end-to-end before touching any code.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Repository Structure](#2-repository-structure)
3. [Build & Deploy](#3-build--deploy)
4. [Device Setup](#4-device-setup)
5. [Backend](#5-backend)
6. [Current State](#6-current-state)
7. [Open Bugs (Critical)](#7-open-bugs-critical)
8. [Open TODOs](#8-open-todos)
9. [Monetization](#9-monetization)
10. [Inferno (IFR) Integration](#10-inferno-ifr-integration)
11. [Testing Protocol](#11-testing-protocol)
12. [Key Learnings](#12-key-learnings)
13. [Conventions](#13-conventions)

---

## 1. Project Overview

### What Is SecureCall?

SecureCall is an end-to-end encrypted voice calling application for Android. Think of it as a hardened, privacy-first alternative to standard phone calls — all voice data is encrypted client-side before it ever leaves the device.

### Architecture

```
┌─────────────┐       WebSocket (WSS)       ┌──────────────────┐
│  Android App │ ◄──────────────────────────► │  Signaling Server │
│  (Kotlin/Java)│                              │  (Node.js/Express) │
└──────┬───────┘                              └────────┬─────────┘
       │                                               │
       │  WebRTC DataChannel (P2P)                     │  Railway.app
       │  XChaCha20-Poly1305 encrypted audio           │  Docker container
       │                                               │
       ▼                                               ▼
┌──────────────┐                              ┌──────────────────┐
│  Peer Device  │                              │  Ethereum Mainnet │
│  (Android App)│                              │  (IFR token check) │
└──────────────┘                              └──────────────────┘
```

**How a call works:**
1. App connects to signaling server via WebSocket
2. Caller dials a phone number → `PHONE_LOOKUP` resolves it to a SecureCall ID
3. `CALL_INVITE` sent with an ephemeral X25519 public key
4. Callee accepts → `CALL_ACCEPT` with their public key
5. Both derive a shared session key via X25519 ECDH + HKDF-SHA256
6. WebRTC DataChannel established for P2P audio
7. Opus-encoded audio encrypted with XChaCha20-Poly1305, sent via DataChannel
8. Call ends → session keys zeroized from memory

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Android Client | Kotlin (new code) + Java (CallActivity, VPN), AndroidX, Material Design 3 |
| Audio Codec | Opus 1.4 via JNI (native .so libraries for arm64-v8a, armeabi-v7a, x86_64) |
| P2P Transport | WebRTC (libwebrtc v125) DataChannel |
| Encryption | XChaCha20-Poly1305 (AEAD), X25519 key exchange, HKDF-SHA256 |
| Crypto Engine | Rust (chacha20poly1305, x25519-dalek, hkdf, sha2, zeroize) |
| Signaling Server | Node.js 18+, Express, ws (WebSocket), ethers.js (Ethereum) |
| Deployment | Docker on Railway.app, auto-deploy from GitHub main branch |
| STUN/TURN | Google STUN (free), Metered.ca TURN (TCP relay) |
| Landing Page | Static HTML/CSS/JS on GitHub Pages |

---

## 2. Repository Structure

```
stealth/
├── client_android/                 # Android application
│   ├── app/
│   │   ├── build.gradle            # AGP 7.4.2, compileSdk 33, 3 product flavors
│   │   ├── proguard-rules.pro      # R8/ProGuard obfuscation (release builds)
│   │   └── src/
│   │       ├── main/               # Shared code across all flavors
│   │       │   ├── java/com/securecall/app/
│   │       │   │   ├── MainActivity.java        # Launcher, bottom nav, permission handling
│   │       │   │   ├── CallActivity.java        # Active call UI, audio focus, proximity sensor
│   │       │   │   ├── IncomingCallActivity.kt  # Full-screen incoming call overlay
│   │       │   │   ├── audio/       # JitterBuffer, AudioCapture, OpusDecoder, playback
│   │       │   │   ├── billing/     # Google Play Billing, SubscriptionManager
│   │       │   │   ├── call/        # CallController
│   │       │   │   ├── config/      # FeatureProvider, TierManager, IfrLockManager
│   │       │   │   ├── crypto/      # EphemeralKeyProvider
│   │       │   │   ├── data/        # Contact, ContactRepository, CallHistory, PhoneUtils
│   │       │   │   ├── fcm/         # FcmTokenManager (placeholder — needs Firebase project)
│   │       │   │   ├── ghostnet/    # GhostNet encrypted transport protocol
│   │       │   │   │   ├── crypto/          # Encryption, key derivation, replay detection
│   │       │   │   │   ├── transport/       # WebRTC DataChannel handling
│   │       │   │   │   ├── frames/          # Frame serialization
│   │       │   │   │   ├── handshake/       # Key exchange protocol
│   │       │   │   │   ├── channel/         # Virtual channels
│   │       │   │   │   └── keys/            # Key management
│   │       │   │   ├── net/         # WebSocketService, HeartbeatClient, WebRtcManager
│   │       │   │   ├── security/    # SecurityEnforcer, StealthDeleteManager, monitors
│   │       │   │   ├── ui/          # Fragments (Contacts, Calls, Dialer, Settings), adapters
│   │       │   │   │   └── onboarding/  # Registration, tutorial flows
│   │       │   │   └── vpn/         # GhostVpnService (WireGuard split tunnel, Premium only)
│   │       │   ├── res/             # Layouts, strings, preferences, drawables, menus
│   │       │   │   ├── values-de/   # German localization
│   │       │   │   └── values-night/# Dark mode colors
│   │       │   └── cpp/             # Native CMake skeleton for crypto JNI
│   │       ├── free/                # Free flavor: BillingManager, RuntimeFeatureProvider, UpgradeActivity
│   │       ├── pro/                 # Pro flavor: CompileTimeFeatureProvider, FeatureFlags
│   │       └── premium/             # Premium flavor: Same pattern, maximum security flags
│   ├── gradle/wrapper/
│   └── gradlew
│
├── backend/                        # Node.js signaling server
│   └── signaling/
│       ├── src/
│       │   ├── server.js           # Main server: WebSocket, phone registry, call routing, IFR
│       │   ├── heartbeat.js        # Ping/pong every 5s, 60s timeout
│       │   ├── rate_limit.js       # 40 JSON msgs/10s, 1000 binary frames/10s per connection
│       │   ├── fcm.js              # Firebase Cloud Messaging (disabled — placeholder)
│       │   ├── pkd.js              # Public Key Directory (anonymous key storage)
│       │   ├── subscriptions.js    # Google Play subscription verification
│       │   ├── call_routing.js     # Call state machine
│       │   ├── session_registry.js # Session tracking
│       │   ├── session_cleanup.js  # Auto-cleanup (>60s inactive)
│       │   ├── presence.js         # Online/offline status
│       │   ├── broadcast.js        # Session broadcasting
│       │   ├── validator.js        # Message validation + sanitization
│       │   ├── logger.js           # Structured logging
│       │   └── errors.js           # Custom error types
│       ├── data/
│       │   ├── activation_codes.json   # Beta activation codes
│       │   └── wallets.json            # IFR wallet-to-device mappings
│       ├── package.json            # express, ws, ethers, firebase-admin, uuid
│       ├── Dockerfile              # Node 18-alpine, non-root user, dumb-init
│       ├── railway.json            # Railway deployment config
│       └── Procfile                # web: node src/server.js
│
├── core_crypto/                    # Rust crypto library (JNI bridge to Android)
│   ├── Cargo.toml                  # chacha20poly1305, x25519-dalek, hkdf, sha2, zeroize
│   └── src/lib.rs
│
├── website/                        # GitHub Pages landing page
│   ├── index.html                  # Landing page with pricing, audit results, IFR section
│   ├── faq.html
│   ├── css/style.css
│   ├── js/main.js
│   └── wiki/                       # 13 HTML docs (architecture, API reference, security audit, etc.)
│
├── docs/                           # Internal documentation (this file + 30+ markdown docs)
├── deploy/                         # Docker Compose, .env.example, deployment scripts
├── deployment/                     # Production configs (nginx, coturn, SSL, monitoring)
├── tools/                          # Build and test scripts
├── native/                         # Native code modules (Opus JNI)
│
├── securecall-release-key.jks      # Release signing keystore (DO NOT COMMIT — .gitignore'd)
├── README.md
├── LICENSE                         # Source-available (not open source)
├── SECURITY.md                     # Vulnerability reporting policy
├── CHANGELOG.md
└── TEST_REPORT.md                  # Bug verification results from physical device testing
```

---

## 3. Build & Deploy

### Prerequisites

- Android Studio (AGP 7.4.2 compatible)
- JDK 11+
- NDK (for libopus native build — pre-compiled .so files included in `jniLibs/`)
- Node.js 18+ (for backend)
- ADB: `/Users/gio/Library/Android/sdk/platform-tools/adb`

### Android App — 3 Build Flavors

| Flavor | Package Name | Tier | Key Differences |
|--------|-------------|------|-----------------|
| `free` | `com.securecall.app.free` | FREE | 15-min calls, 10 contacts, billing enabled, recording allowed |
| `pro` | `com.securecall.app.pro` | PRO | Unlimited, anti-recording (block), cert pinning, device attestation |
| `premium` | `com.securecall.app.premium` | PREMIUM | All Pro + hardware keystore, debugger detection, FLAG_SECURE, auto-terminate on threat |

**Debug builds:**

```bash
cd client_android
./gradlew assembleFreeDebug       # → app/build/outputs/apk/free/debug/app-free-debug.apk
./gradlew assembleProDebug        # → app/build/outputs/apk/pro/debug/app-pro-debug.apk
./gradlew assemblePremiumDebug    # → app/build/outputs/apk/premium/debug/app-premium-debug.apk
```

**Release builds** require keystore environment variables:

```bash
export SECURECALL_STORE_FILE=/path/to/securecall-release-key.jks
export SECURECALL_STORE_PASSWORD=<password>
export SECURECALL_KEY_ALIAS=<alias>
export SECURECALL_KEY_PASSWORD=<password>
./gradlew assembleFreeRelease
```

**Install on device:**

```bash
ADB=/Users/gio/Library/Android/sdk/platform-tools/adb

# Galaxy S10 — Premium
$ADB -s RF8N313QMFL install -r app/build/outputs/apk/premium/debug/app-premium-debug.apk

# Galaxy S7 — Free
$ADB -s ce10160adc00152604 install -r app/build/outputs/apk/free/debug/app-free-debug.apk

# Galaxy Tab S4 — Pro
$ADB -s ce12182c68644439037e install -r app/build/outputs/apk/pro/debug/app-pro-debug.apk
```

### Backend — Local Development

```bash
cd backend/signaling
npm install
npm start                 # Starts on http://localhost:8080
npm run dev               # NODE_ENV=development
```

### Backend — Production Deployment (Railway)

The backend is deployed on Railway and auto-deploys from the `main` branch on GitHub push.

- **Service URL:** `https://protective-healing-production.up.railway.app`
- **WebSocket:** `wss://protective-healing-production.up.railway.app/signal`
- **Health check:** `GET /health`
- **Docker image:** Node 18-alpine, non-root user (`securecall:1001`)
- **Health check interval:** every 30s, 10s timeout, restart on failure (max 5 retries)

To deploy manually: push to `main` and Railway auto-builds via Nixpacks (`npm ci --omit=dev`).

### Environment Variables (Backend)

See `deploy/.env.example` for the full list. Critical ones:

| Variable | Purpose | Default |
|----------|---------|---------|
| `PORT` | Server port | `8080` |
| `NODE_ENV` | Environment | `production` |
| `ADMIN_API_KEY` | Admin endpoint auth (X-Admin-Key header) | — |
| `STUN_URL` | STUN server | `stun:stun.l.google.com:19302` |
| `TURN_URL` | TURN relay server | — |
| `TURN_USER` | TURN username | — |
| `TURN_PASS` | TURN password | — |
| `ETH_RPC_URL` | Ethereum RPC for IFR verification | `https://eth.llamarpc.com` |
| `FIREBASE_SERVICE_ACCOUNT_KEY` | Path to Firebase service account JSON | — (disabled) |
| `ALLOWED_ORIGINS` | CORS allowed origins | — |
| `MAX_CONNS_PER_IP` | Max WebSocket connections per IP | `10` |

---

## 4. Device Setup

### Physical Test Devices

| Device | Serial Number | Installed Flavor | Phone Number | SecureCall ID |
|--------|--------------|-----------------|--------------|---------------|
| Samsung Galaxy S10 | `RF8N313QMFL` | Premium (debug) | +4915231794100 | android-8c766ae8 |
| Samsung Galaxy S7 | `ce10160adc00152604` | Free (debug) | +4915203487046 | android-117f1741 |
| Samsung Galaxy Tab S4 | `ce12182c68644439037e` | Pro (debug) | +491752536807 | android-50965c37 |

**Notes:**
- SecureCall IDs are generated at first install and persist in SharedPreferences. They change on app reinstall/data clear.
- The emulator (`emulator-5554`) can be started via `~/Library/Android/sdk/emulator/emulator -avd Pixel_5` but is unstable (API 36, frequent "System UI not responding" crashes). Use physical devices for reliable testing.
- All three devices must be on the same WiFi or have mobile data for WebSocket connection.

### Checking Device Connection

```bash
ADB=/Users/gio/Library/Android/sdk/platform-tools/adb
$ADB devices                                    # List connected devices
$ADB -s RF8N313QMFL logcat -s WS_SERVICE HB    # Watch WebSocket logs on S10
$ADB -s RF8N313QMFL shell am start -n com.securecall.app.premium/com.securecall.app.MainActivity  # Launch app
```

---

## 5. Backend

### Server Architecture

The signaling server (`backend/signaling/src/server.js`, ~1350 lines) is the central coordination point. It does **not** handle audio — that goes P2P via WebRTC. The server only handles:

- Client registration and phone number mapping
- Call signaling (invite, accept, end, busy)
- WebRTC SDP/ICE relay (during P2P setup)
- Phone number lookup (single + privacy-preserving batch)
- Online status tracking
- Activation code validation
- IFR token balance verification (Ethereum)
- Subscription verification (Google Play)

### WebSocket Protocol — All Message Types

**Connection & Registration:**

| Message | Direction | Fields | Purpose |
|---------|-----------|--------|---------|
| `REGISTER` | Client → Server | `clientId`, `phoneNumber?`, `pubKey?` | Register on connect |
| `REGISTERED` | Server → Client | `clientId` | Confirm registration |
| `DEREGISTER` | Client → Server | — | Remove all mappings (stealth-delete) |
| `DEREGISTER_ACK` | Server → Client | `ok` | Confirm deregistration |

**Call Signaling:**

| Message | Direction | Fields | Purpose |
|---------|-----------|--------|---------|
| `CALL_INVITE` | Caller → Server → Callee | `to`, `pubKey`, `callerPhone`, `sessionId` | Initiate call |
| `CALL_INVITE_ACK` | Server → Caller | `ok`, `sessionId`, `from`, `to`, `pushSent?` | Confirm invite sent |
| `CALL_ACCEPT` | Callee → Server → Caller | `sessionId`, `pubKey` | Accept call |
| `CALL_ACCEPT_ACK` | Server → Callee | `ok`, `sessionId` | Confirm accept |
| `CALL_BUSY` | Callee → Caller | `sessionId`, `from` | Callee already in call |
| `CALL_END` | Either → Peer | `sessionId`, `reason?` | End call |
| `CALL_END_ACK` | Server → Sender | `ok`, `sessionId` | Confirm end |

**WebRTC Relay:**

| Message | Direction | Fields | Purpose |
|---------|-----------|--------|---------|
| `WEBRTC_OFFER` | Caller → Callee | `sessionId`, `from`, `sdp` | SDP offer |
| `WEBRTC_ANSWER` | Callee → Caller | `sessionId`, `from`, `sdp` | SDP answer |
| `ICE_CANDIDATE` | Either → Peer | `sessionId`, `from`, `candidate` | ICE candidate |
| `*_ACK` | Server → Sender | `ok`, `sessionId` | Acknowledgments |

**Lookup & Status:**

| Message | Direction | Fields | Purpose |
|---------|-----------|--------|---------|
| `PHONE_LOOKUP` | Client → Server | `phoneNumber` | Resolve phone → clientId (rate: 10/min) |
| `PHONE_LOOKUP_RESULT` | Server → Client | `phoneNumber`, `clientId`, `online`, `error?` | Lookup response |
| `BATCH_PHONE_LOOKUP` | Client → Server | `hashes[]` | SHA-256 hashed contact check |
| `BATCH_PHONE_LOOKUP_RESULT` | Server → Client | `mode`, `results[]` | Matched hashes |
| `ONLINE_STATUS_REQUEST` | Client → Server | `phones[]` | Check online/offline |
| `ONLINE_STATUS_RESPONSE` | Server → Client | `statuses{}` | Phone → online bool |

**Monetization:**

| Message | Direction | Fields | Purpose |
|---------|-----------|--------|---------|
| `ACTIVATE_CODE` | Client → Server | `code` | Redeem activation code |
| `ACTIVATE_CODE_RESULT` | Server → Client | `success`, `tier`, `code`, `error?` | Code result |
| `VERIFY_IFR_LOCK` | Client → Server | `walletAddress` | Check IFR token balance |
| `IFR_LOCK_RESULT` | Server → Client | `success`, `tier`, `lockedAmount`, `walletAddress`, `error?` | IFR result |
| `SUBSCRIPTION_VERIFY` | Client → Server | `purchaseToken`, `productId` | Google Play purchase |
| `SUBSCRIPTION_VERIFY_ACK` | Server → Client | `tier`, `expiresAt` | Subscription confirmed |

**Other:**

| Message | Direction | Fields | Purpose |
|---------|-----------|--------|---------|
| `REGISTER_FCM_TOKEN` | Client → Server | `fcmToken` | Store push token |
| `GHOST_PREPARE` | Client → Server | `sessionId` | Prepare GhostNet transport |
| `GHOST_ACK` | Server → Client | `sessionId`, `ghostNetId`, `iceServers`, `relayHints` | GhostNet response |
| `HEARTBEAT` / `HEARTBEAT_ACK` | Bidirectional | — | Keep-alive (5s interval, 60s timeout) |
| `ERROR` | Server → Client | `error`, `message` | Error response |

### REST Endpoints

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/` | GET | None | Status page |
| `/health` | GET | None | Health check (used by Railway) |
| `/metrics` | GET | None | Memory, uptime, connection count |
| `/ice-servers` | GET | Admin | List configured TURN/STUN servers |
| `/routing/list` | GET | Admin | Debug: active call sessions |
| `/clients/list` | GET | Admin | Debug: connected clients |
| `/key/register` | POST | None | PKD: register public key |
| `/key/:id` | GET/PUT/DELETE | None | PKD: manage public keys |
| `/api/subscription/:clientId` | GET | Admin | Get subscription details |

Admin endpoints require `X-Admin-Key` header matching `ADMIN_API_KEY` env var.

### Call State Machine

```
INVITE ──► ACTIVE ──► ENDED
  │                     ▲
  │                     │
  └──► INVITE_PENDING_PUSH ──► (FCM wakes device) ──► ACTIVE ──► ENDED
```

Sessions auto-clean after 60 seconds of inactivity.

### Server Security

- **Prototype pollution prevention:** Deletes `__proto__`, `constructor`, `prototype` from all incoming JSON
- **Rate limiting:** 40 JSON messages per 10 seconds, 1000 binary frames per 10 seconds (per connection)
- **Per-IP limits:** Max connections configurable (default 10)
- **Input validation:** String fields truncated to 64 chars, special characters removed
- **Session validation:** Only call participants can accept/end sessions
- **Phone privacy:** Batch lookups use SHA-256 hashes — server never sees raw phone numbers in batch mode

---

## 6. Current State

### Version: v1.0-beta (git tag: `v1.0-beta`)

**What works (verified on physical devices):**

- End-to-end encrypted voice calls between two devices
- WebSocket signaling: register, call invite, accept, end, busy
- WebRTC P2P DataChannel audio transport
- Opus codec encoding/decoding via JNI
- Phone number lookup (single and batch)
- Contact sync with privacy-preserving hash matching
- T9 dialer with contact suggestions
- Contact search with keyboard handling (hides bottom nav)
- Call history display
- Dark mode / light mode toggle
- SMS and Share Link invite flow for non-SecureCall users
- Activation code tier unlock (tested: TEST-PRO1-CODE, TEST-PREM-CODE)
- IFR token wallet verification with 30-day expiration
- Online/offline status indicators (Pro/Premium)
- STEALTH-DELETE emergency wipe (5-tap trigger in Settings)
- Proximity sensor screen off during calls
- Call timer with encryption status indicator
- Three-tier feature gating (Free/Pro/Premium)
- German localization
- Backend auto-deploy on Railway from GitHub push

**What was tested and passed (see TEST_REPORT.md):**

- Contacts search keyboard visibility (Bug #1 — fixed)
- Dialer T9 contact suggestions (Bug #2 — fixed)
- Call button visibility and function (Bug #3 — verified)
- SMS invite flow for unknown numbers (Bug #6 — working)
- Share link invite (Bug #7 — working)
- Call screen UI: timer, encryption badge, mute/end/speaker buttons
- Bottom navigation across all 4 tabs
- Backend WebSocket connectivity from multiple devices

---

## 7. Open Bugs (Critical)

### BUG-1: Firebase/FCM Push Notifications Disabled

**Severity:** Critical (calls to offline users silently fail)

**Description:** Firebase is configured with placeholder credentials. FCM push notifications do not work. When a user calls someone whose app is in the background or killed, the callee never receives the call.

**Reproduction:**
1. Kill SecureCall on Device B (swipe away from recents)
2. On Device A, call Device B's phone number
3. Device A shows "Calling..." indefinitely — Device B never rings

**Root Cause:** `google-services.json` is a placeholder. `FcmTokenManager.kt` has a TODO comment: "FCM requires a real Firebase project." `SecureCallMessagingService` exists but never receives pushes.

**Impact:** The app only works when both users have it actively open. This is the single biggest usability blocker.

---

### BUG-2: TURN Credentials Hardcoded in Source Code

**Severity:** High (security + operational risk)

**Description:** Metered.ca TURN server credentials (username and password) are hardcoded in `client_android/app/build.gradle` as `BuildConfig` fields. These are baked into every APK and visible to anyone who decompiles it.

**Reproduction:**
1. Decompile any debug APK with `apktool`
2. Search for `TURN_USER` or `TURN_PASS` in BuildConfig
3. Credentials are plaintext

**Root Cause:** Build config fields `TURN_URL`, `TURN_USER`, `TURN_PASS` are set at compile time instead of fetched at runtime.

**Fix Direction:** The server already has a `/ice-servers` endpoint (admin-only). The client should fetch TURN credentials from this endpoint at startup, allowing credential rotation without rebuilding APKs.

---

### BUG-3: Background Activity Launch Fails on Samsung (Locked Screen)

**Severity:** High (missed incoming calls on Samsung devices)

**Description:** `fullScreenIntent` notifications don't reliably bring `IncomingCallActivity` to the foreground on Samsung devices when the screen is locked or the app is in the background. Samsung's power management and notification restrictions interfere.

**Reproduction:**
1. On Galaxy S10, lock the screen
2. Call from another device
3. Notification appears but `IncomingCallActivity` may not launch as a full-screen overlay

**Workaround:** User must manually exempt SecureCall from Samsung's battery optimization and "Sleeping apps" list.

---

### BUG-4: Emulator Instability

**Severity:** Medium (development workflow impact)

**Description:** Pixel 5 AVD (API 36) frequently crashes with "System UI not responding" dialogs. GPU/memory issues on the development machine.

**Impact:** Cannot reliably use emulator for testing. All functional testing must happen on physical devices.

---

### BUG-5: GhostNet Crypto Uses Mock/Placeholder Encryption

**Severity:** High (security — debug builds only)

**Description:** The GhostNet transport layer (`ghostnet/crypto/`) currently uses mock handshakes and placeholder encryption in debug builds. `SessionCryptoContext.fromMockHandshake()` returns a context where `encryptOutbound()` and `decryptInbound()` are no-ops (data passes through unencrypted).

**Evidence:**
- `SessionCryptoContext.kt:36`: `"encryptOutbound(): size=${plain.size} (NO REAL ENCRYPTION)"`
- `SessionCryptoContext.kt:41`: `"decryptInbound(): size=${cipher.size} (NO REAL DECRYPTION)"`
- `SessionKeyDerivation.kt:45`: `"deriveEphemeral(): PLACEHOLDER – generating random keys"`

**Impact:** Audio data is not actually encrypted in debug builds. The real crypto path (Rust JNI via `CoreCrypto`) must be wired in before any public release.

---

### BUG-6: HKDF Key Derivation Not Implemented (CRYPTO-03)

**Severity:** Medium (cryptographic correctness)

**Description:** `SessionKeyDerivation.kt:79` has a TODO: "CRYPTO-03: HKDF o.a. einsetzen, statt reinem Random." Currently uses random bytes instead of proper HKDF-SHA256 for ephemeral key derivation.

**Impact:** Without proper KDF, key material lacks the security properties expected from the X25519 + HKDF design.

---

### BUG-7: `promptForPhoneNumber()` BadTokenException

**Severity:** Low (crash on edge case)

**Description:** If `promptForPhoneNumber()` is called on a destroyed Activity, it throws `BadTokenException`. Needs an `isFinishing()/isDestroyed()` guard before showing the AlertDialog.

---

### BUG-8: AlertDialog `setMessage()` Suppresses `setItems()`

**Severity:** Low (UI quirk)

**Description:** Android's `AlertDialog.Builder`: calling both `setMessage()` and `setItems()` causes `setItems()` to be silently ignored. Use `setTitle()` for the description text when items are needed.

---

## 8. Open TODOs

### High Priority

1. **Firebase/FCM Push Notifications**
   - Create a real Firebase project
   - Replace placeholder `google-services.json`
   - Wire up `SecureCallMessagingService` to handle `CALL_INVITE` pushes
   - Test: kill app, receive call → push wakes device → incoming call screen appears

2. **Runtime TURN Credential Fetching**
   - Remove hardcoded TURN credentials from `build.gradle`
   - Client fetches from server `/ice-servers` endpoint on app startup
   - Server returns time-limited TURN credentials
   - Enables credential rotation without APK rebuild

3. **Wire Real Encryption (Replace Mock Handshake)**
   - Connect `CoreCrypto` Rust JNI to GhostNet transport
   - Replace `SessionCryptoContext.fromMockHandshake()` with real X25519 + XChaCha20-Poly1305
   - Implement HKDF-SHA256 key derivation (CRYPTO-03)
   - Verify zeroization of key material after call ends

4. **WalletConnect Integration**
   - UI is ready (Settings preference exists, shows "coming soon")
   - Need to integrate WalletConnect SDK for wallet signature verification
   - WalletConnect-verified wallets get lifetime access (no 30-day expiration)
   - `IfrLockManager` already has `METHOD_WALLETCONNECT` constant and logic paths

### Medium Priority

5. **TURN Server Rotation**
   - Set up own TURN infrastructure (coturn) for production
   - Configuration files exist in `deployment/coturn_config/`
   - Nginx reverse proxy config in `deployment/nginx_config/`
   - Reduces dependency on Metered.ca free tier

6. **Release APK Signing & Distribution**
   - Keystore exists (`securecall-release-key.jks`)
   - Build script exists (`tools/build_release_aabs.sh`)
   - Need to set up CI/CD pipeline for automated release builds
   - ProGuard rules configured in `proguard-rules.pro`

7. **Google Play Store Submission**
   - Checklist exists: `docs/PLAY_STORE_CHECKLIST.md`
   - Need: privacy policy, app screenshots, feature graphic, content rating
   - Beta testing plan in `docs/BETA_TESTING_PLAN.md`

### Lower Priority

8. **Real Subscription Verification**
   - `SubscriptionManager` + `BillingManager` are implemented (Free flavor)
   - Backend `subscriptions.js` handles verification
   - Needs real Google Play Console setup with product IDs matching SKUs

9. **iOS / Cross-Platform**
   - Not started. Current architecture (WebSocket signaling + WebRTC) is cross-platform compatible.
   - Rust crypto engine can be compiled for iOS via `cargo-lipo`

10. **GhostOS ROM Integration**
    - `rom_ghostos/` directory exists with hardened OS project files
    - Currently separate from the main app

---

## 9. Monetization

### Three Upgrade Paths

**Path 1: Google Play In-App Purchase (Free flavor only)**

Users on the Free tier can upgrade via Google Play Billing:

| SKU | Type | Tier |
|-----|------|------|
| `securecall_pro_monthly` | Subscription | Pro |
| `securecall_pro_yearly` | Subscription | Pro |
| `securecall_premium_monthly` | Subscription | Premium |
| `securecall_premium_yearly` | Subscription | Premium |
| `securecall_pro_lifetime` | One-time | Pro |
| `securecall_premium_lifetime` | One-time | Premium |

Flow: `UpgradeActivity` (Free flavor) → Google Play billing flow → purchase token sent to backend via `SUBSCRIPTION_VERIFY` → backend confirms → tier stored locally.

**Path 2: Activation Codes**

Beta testers and special users can unlock tiers via codes:

- Dialog accessible in Settings
- User enters code → `ACTIVATE_CODE` message to server
- Server validates against `activation_codes.json`
- On success: tier stored in SharedPreferences key `activated_tier`
- App restarts to apply new feature flags
- Test codes: `TEST-PRO1-CODE` (Pro), `TEST-PREM-CODE` (Premium), `BETA-PRO0-2026`, `BETA-PREM-2026`
- Usage tracked in memory (resets on server restart)

**Path 3: IFR Token (Ethereum)**

See [Section 10: Inferno Integration](#10-inferno-ifr-integration) for full details.

### Tier Priority Logic

`TierManager.getCurrentTier()` returns the highest of:
1. Build flavor tier (from `BuildConfig.FLAVOR`)
2. Activated tier (from SharedPreferences `activated_tier`, set by activation code or IFR)

The `FeatureProviderRegistry` maps tier → 19 boolean feature flags that gate functionality across the app.

---

## 10. Inferno (IFR) Integration

### Overview

Users can unlock Pro or Premium tiers by holding IFR tokens in an Ethereum wallet. This is a balance-based verification system — no tokens are locked or transferred.

### Contract Details

| Parameter | Value |
|-----------|-------|
| Token Contract | `0x77e99917Eca8539c62F509ED1193ac36580A6e7B` |
| Network | Ethereum Mainnet |
| Token Standard | ERC-20 |
| Decimals | 9 |
| ABI Method | `balanceOf(address) view returns (uint256)` |

### Tier Thresholds

| IFR Balance | Unlocked Tier |
|-------------|---------------|
| >= 1,000 IFR | Pro |
| >= 5,000 IFR | Premium |

### Verification Flow

1. User enters Ethereum wallet address in Settings (manual entry)
2. App sends `VERIFY_IFR_LOCK` message with `walletAddress` to server
3. Server calls `balanceOf(walletAddress)` on Ethereum via ethers.js
4. Server uses RPC fallback chain: `ETH_RPC_URL` (default: llamarpc.com) → ankr.com → cloudflare-eth.com
5. Server checks wallet-to-device mapping (one wallet per device, prevents multi-device abuse)
6. Server returns `IFR_LOCK_RESULT` with `success`, `tier`, `lockedAmount`
7. App stores result in SharedPreferences and activates tier via `TierManager`

### 30-Day Wallet Expiration (Manual Entry)

- Manual wallet entry expires after 30 days from first verification
- Tracked via `ifr_wallet_verified_at` timestamp in SharedPreferences
- `IfrLockManager.getDaysRemaining()` calculates remaining days
- `IfrLockManager.isManualExpired()` checks if >30 days have passed
- On expiration: tier auto-reverts, all IFR preferences cleared
- User must re-enter wallet address to re-verify

### 24-Hour Re-verification

- Triggered at app startup in `MainActivity.onCreate()` → `IfrLockManager.reverifyIfNeeded()`
- Checks if 24 hours have passed since `ifr_last_verified`
- Re-verification does NOT reset the 30-day clock (keeps original `verified_at`)
- Only updates `last_verified` and balance amount

### WalletConnect (Not Yet Implemented)

- WalletConnect-verified wallets get lifetime access (no 30-day expiration)
- `IfrLockManager.METHOD_WALLETCONNECT` constant exists
- Settings UI shows "lifetime" suffix for WalletConnect entries
- UI preference is disabled with "coming soon" label

### SharedPreferences Keys (IFR)

All stored in `securecall_prefs`:

| Key | Value |
|-----|-------|
| `ifr_wallet_address` | Ethereum address (0x...) |
| `ifr_tier` | Unlocked tier (pro/premium) |
| `ifr_locked_amount` | Token balance at verification |
| `ifr_last_verified` | Last verification timestamp (ms) |
| `ifr_wallet_verified_at` | First verification timestamp (for 30-day calc) |
| `ifr_verification_method` | "manual" or "walletconnect" |

---

## 11. Testing Protocol

### Golden Rule: Test on Physical Devices

The emulator is unreliable. All testing MUST be done on the three physical Samsung devices. This is non-negotiable.

### Analog Testing (Required for Voice Calls)

Voice call quality cannot be tested via automated scripts. You need:
1. Two physical devices in front of you
2. Both connected to the signaling server (check logcat for `REGISTERED` message)
3. Call from Device A → Device B
4. Verify: ringtone, accept, audio both directions, encryption indicator, call timer, end call
5. Test mute, speaker, proximity sensor (hold to ear → screen off)

### Standard Test Matrix

Run this after every significant change:

| Test | Device A | Device B | Expected |
|------|----------|----------|----------|
| Call (both online) | S10 (Premium) | S7 (Free) | Ring → Accept → Audio → End |
| Call (busy) | S10 | S7 (already in call) | Caller gets CALL_BUSY |
| Call (unknown number) | S10 | — | Invite dialog (SMS/Share) |
| Contact search | S10 | — | Keyboard shows, contacts filter, bottom nav hides |
| T9 dialer | S10 | — | Type digits → contact suggestions appear |
| Activation code | S7 | — | Enter TEST-PRO1-CODE → app restarts → Pro features |
| IFR wallet verify | S7 | — | Enter wallet → server checks → tier updates |
| STEALTH-DELETE | Tab S4 | — | 5 taps on version → confirmation → data wiped |
| Online status | S10 (Premium) | S7 | Green/red dots update when S7 connects/disconnects |
| Dark/light mode | Any | — | Toggle in Settings → theme switches |

### Checking Logs

```bash
ADB=/Users/gio/Library/Android/sdk/platform-tools/adb

# WebSocket connection
$ADB -s RF8N313QMFL logcat -s WS_SERVICE

# Heartbeat
$ADB -s RF8N313QMFL logcat -s HB

# Incoming calls
$ADB -s RF8N313QMFL logcat -s INCOMING_CALL

# IFR verification
$ADB -s RF8N313QMFL logcat -s IFR_LOCK

# Security events
$ADB -s RF8N313QMFL logcat -s SECURITY

# Stealth delete
$ADB -s RF8N313QMFL logcat -s STEALTH_DELETE

# All app logs
$ADB -s RF8N313QMFL logcat --pid=$(adb -s RF8N313QMFL shell pidof -s com.securecall.app.premium)
```

### Backend Monitoring

```bash
# Health check
curl https://protective-healing-production.up.railway.app/health

# Metrics (memory, uptime, connections)
curl https://protective-healing-production.up.railway.app/metrics

# Connected clients (requires admin key)
curl -H "X-Admin-Key: <key>" https://protective-healing-production.up.railway.app/clients/list

# Active sessions
curl -H "X-Admin-Key: <key>" https://protective-healing-production.up.railway.app/routing/list
```

---

## 12. Key Learnings

These are hard-won lessons from the development process. Ignore them at your peril.

### Regressions Happen Constantly

Every change, no matter how small, can break something else. The codebase has tight coupling between WebSocket signaling, audio pipeline, UI state, and security enforcement. A "simple" fix to the call screen can break incoming call handling. A tweak to the jitter buffer can cause audio dropouts.

**Rule: Test the full call flow after every change. Not just the thing you changed.**

### One Fix at a Time

When multiple bugs exist, fix them one at a time. Build, install, test, verify. Then move to the next bug. Batching fixes makes it impossible to isolate which change broke something when (not if) a regression appears.

### Physical Devices Are the Only Truth

The emulator lies. Samsung-specific behaviors (battery optimization, notification restrictions, `fullScreenIntent` behavior) only manifest on real Samsung hardware. Never trust a "works on emulator" result.

### AlertDialog Gotchas

Android's `AlertDialog.Builder` has a quirk: calling `setMessage()` suppresses `setItems()`. If you need both descriptive text and list items, put the description in `setTitle()`.

### Foreground Service Timing

`WebSocketService` MUST call `startForeground()` within 5 seconds of being started on Android 8+. If it doesn't, the system kills the service and the app may ANR. This is enforced by the OS, not a guideline.

### SharedPreferences Key Discipline

All persistent state is in SharedPreferences (`securecall_prefs`). Keys are undocumented except in code. Before adding a new key, search the codebase for existing keys to avoid conflicts. Key naming: `pref_` prefix for user preferences, `ifr_` prefix for IFR state, `activated_tier` for activation code result.

### JVM Signature Clashes (Kotlin/Java Interop)

When Kotlin functions return `Function` types (e.g., `() -> Unit`), they can clash with Java method signatures at the JVM level. This has caused build failures in the past. When adding Kotlin code that Java code calls, verify it compiles before committing.

### TURN Relay Is Essential

Without TURN, calls fail whenever both users are behind symmetric NAT (common on mobile networks). The Google STUN server only handles simple NAT traversal. For production, TURN relay via Metered.ca (or self-hosted coturn) is required.

---

## 13. Conventions

### Git Workflow

- **Main branch:** `main` (protected, auto-deploys backend to Railway)
- **Feature branches:** `feature/<description>` or `fix/<description>`
- **Commit messages:** Conventional Commits format:
  - `feat:` new feature
  - `fix:` bug fix
  - `chore:` maintenance (gitignore, deps, config)
  - `docs:` documentation only
  - `refactor:` code restructuring without behavior change
- **Tags:** `v{major}.{minor}-{stage}` (e.g., `v1.0-beta`)
- **Push to main** triggers Railway auto-deploy. Be careful.

### Code Conventions

| Aspect | Convention |
|--------|-----------|
| Language (new code) | Kotlin |
| Language (existing) | Java for `CallActivity.java`, `MainActivity.java`, VPN |
| Package structure | `com.securecall.app.<module>` |
| Naming (functions/vars) | camelCase |
| Naming (classes) | PascalCase |
| Naming (resources) | snake_case |
| Preference keys | `pref_` prefix |
| Log tags | Short: `WS_SERVICE`, `HB`, `INCOMING_CALL`, `IFR_LOCK` |
| Error handling (non-critical) | `catch (_: Exception) {}` |
| Error handling (critical) | `Log.e(TAG, msg, throwable)` |
| Build config | Feature flags in `build.gradle` `buildConfigField` per flavor |

### Build Variants

Each flavor has its own source set (`src/free/`, `src/pro/`, `src/premium/`) that overrides:
- `FeatureFlags.kt` — compile-time boolean flags
- `FeatureProvider` implementation — runtime feature gating
- `AppInit.kt` — flavor-specific initialization (e.g., billing in Free)

The `main` source set contains all shared code. Flavor-specific code should be minimal — only feature flag values and initialization differences.

### Server URLs

| Service | URL |
|---------|-----|
| WebSocket Signaling | `wss://protective-healing-production.up.railway.app/signal` |
| Health Check | `https://protective-healing-production.up.railway.app/health` |
| STUN | `stun:stun.l.google.com:19302` |
| TURN | `turn:a.relay.metered.ca:443?transport=tcp` |
| Landing Page | `https://neabouli.github.io/stealth/` |
| GitHub Repository | `https://github.com/NeaBouli/stealth` |

---

## Appendix: Quick Reference Commands

```bash
# Set ADB path
ADB=/Users/gio/Library/Android/sdk/platform-tools/adb

# Build all debug variants
cd client_android && ./gradlew assembleFreeDebug assembleProDebug assemblePremiumDebug

# Install on all devices
$ADB -s RF8N313QMFL install -r app/build/outputs/apk/premium/debug/app-premium-debug.apk
$ADB -s ce10160adc00152604 install -r app/build/outputs/apk/free/debug/app-free-debug.apk
$ADB -s ce12182c68644439037e install -r app/build/outputs/apk/pro/debug/app-pro-debug.apk

# Watch all logs for S10
$ADB -s RF8N313QMFL logcat -s WS_SERVICE,HB,INCOMING_CALL,IFR_LOCK,SECURITY,STEALTH_DELETE

# Start backend locally
cd backend/signaling && npm start

# Check Railway deployment
curl https://protective-healing-production.up.railway.app/health

# Run emulator (unreliable — prefer physical devices)
~/Library/Android/sdk/emulator/emulator -avd Pixel_5
```
