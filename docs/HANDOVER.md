# Project Handover — SecureCall (StealthX Platform)

## Overview

SecureCall is an end-to-end encrypted voice calling app for Android. The monorepo contains the Android client, a Node.js signaling backend (deployed on Railway), a Rust crypto engine, and supporting infrastructure including a landing page and documentation wiki.

**Current version:** v1.0-beta
**Status:** Feature-complete, tested across 3 physical devices, ready for public beta.

---

## Repository Structure

```
stealth/
├── client_android/              # Android app (Kotlin + Java)
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/            # Shared code across all flavors
│   │   │   │   ├── java/com/securecall/app/
│   │   │   │   │   ├── audio/          # JitterBuffer, AudioCapture
│   │   │   │   │   ├── billing/        # Google Play billing, SubscriptionManager
│   │   │   │   │   ├── call/           # CallController
│   │   │   │   │   ├── config/         # FeatureProvider, TierManager, IfrLockManager
│   │   │   │   │   ├── crypto/         # EphemeralKeyProvider
│   │   │   │   │   ├── data/           # Contact, ContactRepository, CallHistory, PhoneUtils
│   │   │   │   │   ├── fcm/            # FCM push (placeholder — needs Firebase project)
│   │   │   │   │   ├── ghostnet/       # GhostNet encrypted transport protocol
│   │   │   │   │   ├── net/            # WebSocketService, HeartbeatClient, WebRtcManager, NetworkManager
│   │   │   │   │   ├── security/       # SecurityEnforcer, StealthDeleteManager, monitors
│   │   │   │   │   ├── ui/             # Fragments (Contacts, Calls, Dialer, Settings), adapters
│   │   │   │   │   ├── vpn/            # GhostVpnService (WireGuard split tunnel)
│   │   │   │   │   ├── CallActivity.java
│   │   │   │   │   ├── IncomingCallActivity.kt
│   │   │   │   │   └── MainActivity.java
│   │   │   │   ├── res/                # Layouts, strings, preferences, drawables
│   │   │   │   └── cpp/               # libopus 1.4 JNI (native audio codec)
│   │   │   ├── free/            # Free flavor: FeatureFlags, UpgradeActivity, RuntimeFeatureProvider
│   │   │   ├── pro/             # Pro flavor: FeatureFlags
│   │   │   └── premium/         # Premium flavor: FeatureFlags
│   │   └── build.gradle         # AGP 7.4.2, compileSdk 33, 3 flavors
│   └── gradlew
│
├── backend/                     # Node.js signaling server
│   └── signaling/
│       ├── src/
│       │   ├── server.js        # WebSocket signaling, phone registry, IFR verification
│       │   ├── rate_limit.js    # Per-client rate limiting
│       │   ├── heartbeat.js     # Connection keepalive
│       │   ├── fcm.js           # Firebase Cloud Messaging (placeholder)
│       │   ├── pkd.js           # Public Key Directory
│       │   └── subscriptions.js # Subscription verification
│       ├── data/
│       │   ├── activation_codes.json  # Beta activation codes
│       │   └── wallets.json           # IFR wallet → device mappings
│       └── package.json         # express, ws, ethers, firebase-admin, uuid
│
├── core_crypto/                 # Rust crypto library
│   └── src/lib.rs               # XChaCha20-Poly1305, X25519, HKDF
│
├── website/                     # GitHub Pages landing page
│   ├── index.html               # Landing page with pricing, audit results, IFR section
│   ├── faq.html                 # Full FAQ
│   ├── css/style.css
│   ├── js/main.js
│   └── wiki/                    # Documentation wiki
│       ├── index.html
│       ├── getting-started.html
│       ├── security-design.html
│       ├── security-audit.html  # Includes device emulation test results
│       ├── ifr-unlock.html      # IFR token unlock guide
│       └── ...
│
├── docs/                        # Internal documentation
├── deploy/                      # Deployment scripts
├── tools/                       # Test scripts
└── native/                      # Native code modules
```

---

## Build System

### Prerequisites
- Android Studio (AGP 7.4.2)
- JDK 11+
- NDK (for libopus native build)
- Node.js 18+ (for backend)

### Android App — 3 Build Flavors

| Flavor | Package Name | Tier | Key Differences |
|--------|-------------|------|-----------------|
| `free` | `com.securecall.app.free` | FREE | 15-min calls, 10 contacts, billing enabled, no anti-recording |
| `pro` | `com.securecall.app.pro` | PRO | Unlimited, anti-recording (block), cert pinning |
| `premium` | `com.securecall.app.premium` | PREMIUM | All Pro + hardware keystore, debugger detection, terminate on threat |

```bash
cd client_android
./gradlew assembleFreeDebug       # Free variant
./gradlew assembleProDebug        # Pro variant
./gradlew assemblePremiumDebug    # Premium variant
```

APK output: `app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk`

### Release Build
Requires keystore environment variables:
```
SECURECALL_STORE_FILE, SECURECALL_STORE_PASSWORD,
SECURECALL_KEY_ALIAS, SECURECALL_KEY_PASSWORD
```

### Backend
```bash
cd backend/signaling
npm install
npm start                         # Starts on port 8080
```
Deployed on Railway (auto-deploy from GitHub push to main).

---

## Architecture

### Call Flow
1. App connects to signaling server via WebSocket (`wss://protective-healing-production.up.railway.app/signal`)
2. User dials phone number → `PHONE_LOOKUP` resolves to SecureCall ID
3. `CALL_INVITE` sent with X25519 public key
4. Callee accepts → `CALL_ACCEPT` with their public key
5. Both derive session key via X25519 ECDH + HKDF
6. WebRTC DataChannel established (P2P)
7. Opus-encoded audio encrypted with XChaCha20-Poly1305, sent via DataChannel
8. Call ends → session keys zeroized

### Tier System
The effective tier is determined by `TierManager.getCurrentTier()`:
1. Check `activated_tier` in SharedPreferences (from activation code or IFR)
2. Fall back to `BuildConfig.FLAVOR`
3. Return the highest tier

Feature gating uses `FeatureProviderRegistry.get()` which returns a `FeatureProvider` with all 19 feature flags mapped per tier.

### IFR Token Integration
- IFR ERC-20 token: `0x77e99917Eca8539c62F509ED1193ac36580A6e7B` (Ethereum mainnet, 9 decimals)
- Server calls `balanceOf(wallet)` via ethers.js
- Thresholds: 1,000 IFR = Pro, 5,000 IFR = Premium
- Manual wallet entry: 30-day expiration, one wallet per device
- WalletConnect: lifetime, unlimited devices (not yet implemented)
- Re-verification every 24 hours

### Activation Codes
- Server validates codes from in-memory map (loaded from `activation_codes.json`)
- Codes: `TEST-PRO1-CODE` (pro), `TEST-PREM-CODE` (premium), `BETA-PRO0-2026`, `BETA-PREM-2026`
- Usage tracked in-memory (resets on server restart)

---

## Key Server Endpoints (WebSocket Messages)

| Message | Direction | Purpose |
|---------|-----------|---------|
| `REGISTER` | Client → Server | Register clientId + phone number |
| `PHONE_LOOKUP` | Client → Server | Resolve phone to clientId (rate limited: 10/min) |
| `BATCH_PHONE_LOOKUP` | Client → Server | Privacy-preserving SHA-256 hashed contact check |
| `ONLINE_STATUS_REQUEST` | Client → Server | Check online/offline status of phone numbers |
| `CALL_INVITE` / `CALL_ACCEPT` / `CALL_END` | Bidirectional | Call signaling |
| `CALL_BUSY` | Callee → Caller | Reject if already in active call |
| `WEBRTC_OFFER` / `WEBRTC_ANSWER` / `ICE_CANDIDATE` | Bidirectional | WebRTC P2P setup |
| `ACTIVATE_CODE` | Client → Server | Validate activation code |
| `VERIFY_IFR_LOCK` | Client → Server | Check IFR token balance on Ethereum |
| `SECUREID_CHANGED` | Server → Clients | Notify when device gets new SecureCall ID |
| `DEREGISTER` | Client → Server | Remove all mappings (stealth-delete) |

---

## Physical Test Devices

| Device | Serial | Flavor | Phone | SecureCall ID |
|--------|--------|--------|-------|---------------|
| Galaxy S10 | RF8N313QMFL | Premium | +4915231794100 | android-8c766ae8 |
| Galaxy S7 | ce10160adc00152604 | Free | +4915203487046 | android-117f1741 |
| Galaxy Tab S4 | ce12182c68644439037e | Pro | +491752536807 | android-50965c37 |

**adb path:** `/Users/gio/Library/Android/sdk/platform-tools/adb`
**Emulator:** `~/Library/Android/sdk/emulator/emulator -avd Pixel_5`

Install commands:
```bash
ADB=/Users/gio/Library/Android/sdk/platform-tools/adb
$ADB -s RF8N313QMFL install -r app/build/outputs/apk/premium/debug/app-premium-debug.apk
$ADB -s ce10160adc00152604 install -r app/build/outputs/apk/free/debug/app-free-debug.apk
$ADB -s ce12182c68644439037e install -r app/build/outputs/apk/pro/debug/app-pro-debug.apk
```

---

## Features by Tier

### All Tiers (FREE)
- E2E encrypted voice calls (XChaCha20-Poly1305 + X25519)
- Contact verification (SHA-256 privacy-preserving batch lookup)
- Call history, dark/light mode
- Basic security warnings
- Contact deduplication (phone + SecureID merge)
- STEALTH-DELETE: 5-tap trigger in Settings for emergency data wipe

### PRO (activation code, IFR, or build flavor)
- Online/offline contact status (green/red dots)
- Anti-recording detection (block mode)
- Enhanced security monitoring
- Unlimited calls and contacts

### PREMIUM (activation code, IFR, or build flavor)
- All Pro features
- Anonymous eSIM network binding (EuiccManager)
- WireGuard VPN split tunnel (only app traffic)
- Kill switch (blackhole traffic if VPN drops)
- FLAG_SECURE on all Activities (screenshot blocking)
- Advanced threat detection, auto-terminate on threat
- Certificate pinning, hardware keystore, debugger detection

---

## Known Issues / TODOs

1. **Firebase not configured.** FCM push notifications require a real Firebase project. Token registration silently fails. See `FcmTokenManager.kt` for integration plan.
2. **TURN credentials hardcoded** in `build.gradle`. Should be fetched at runtime from `/ice-servers` endpoint.
3. **WalletConnect** not yet implemented. Manual wallet entry only (30-day expiry).
4. **Emulator instability.** Pixel 5 AVD (API 36) frequently shows "System UI not responding."
5. **Background activity launch on Samsung.** `fullScreenIntent` notifications don't bring IncomingCallActivity to foreground on unlocked Samsung devices.
6. **T9 dialer suggestions** shift button positions on Galaxy S7 during adb tap testing.

---

## Code Conventions

- **Language:** Kotlin for new code, Java for CallActivity and VPN. XML for resources. Rust for crypto.
- **Package:** `com.securecall.app.<module>` (net, ui, audio, config, security, vpn, data)
- **Naming:** camelCase functions/variables, PascalCase classes, snake_case resource IDs
- **Preference keys:** `pref_` prefix (e.g., `pref_dark_mode`, `pref_vpn_enabled`)
- **Log tags:** Short descriptive — `WS_SERVICE`, `HB`, `INCOMING_CALL`, `STEALTH_DELETE`, `IFR_LOCK`
- **Error handling:** Non-critical: `catch (_: Exception) {}`. Critical: `Log.e(TAG, msg, throwable)`

---

## Server URLs

| Service | URL |
|---------|-----|
| WebSocket Signaling | `wss://protective-healing-production.up.railway.app/signal` |
| Health Check | `https://protective-healing-production.up.railway.app/health` |
| STUN | `stun:stun.l.google.com:19302` |
| TURN | `turn:a.relay.metered.ca:443?transport=tcp` |
| Landing Page | `https://neabouli.github.io/stealth/` |
| GitHub | `https://github.com/NeaBouli/stealth` |
