> **CLASSIFICATION: RESTRICTED** | **DOCUMENT: SC-ARCH** | **DIVISION: StealthX // SecureCall**

---

# SYSTEM ARCHITECTURE BRIEF

---
#### ████ COMPONENT INVENTORY ████
---

SecureCall consists of three core components:

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Android Client** | Kotlin / Java | UI, audio capture, call management, security monitoring |
| **Crypto Engine** | Rust (via JNI) | XChaCha20-Poly1305, X25519, HKDF-SHA256, key management |
| **Signaling Server** | Node.js / Express / WebSocket | Connection establishment, key exchange relay |

---
#### ████ ARCHITECTURE OVERVIEW ████
---

```
┌──────────────────────────────────────────────────────────┐
│                    Android Client                         │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐ │
│  │    UI    │ │  Audio   │ │ Security │ │  Network   │ │
│  │  (MD3)  │ │ Pipeline │ │ Monitor  │ │  Manager   │ │
│  │         │ │          │ │          │ │            │ │
│  │ - Call  │ │ - Opus   │ │ - Screen │ │ - WebRTC   │ │
│  │ - Dial  │ │ - 48kHz  │ │ - Mic    │ │ - WebSocket│ │
│  │ - Cont. │ │ - AEC    │ │ - SpyApp │ │ - ICE/TURN │ │
│  │ - Sett. │ │ - AGC    │ │ - Focus  │ │ - DTLS     │ │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └─────┬──────┘ │
│       └─────────────┴────────────┴─────────────┘         │
│                          │ JNI                            │
│  ┌───────────────────────┴───────────────────────────┐   │
│  │            Rust Crypto Engine (Native .so)          │   │
│  │  ┌──────────┐ ┌─────────┐ ┌────────┐ ┌─────────┐ │   │
│  │  │ XChaCha20│ │  X25519 │ │  HKDF  │ │ Double  │ │   │
│  │  │ Poly1305 │ │  ECDH   │ │ SHA256 │ │ Ratchet │ │   │
│  │  └──────────┘ └─────────┘ └────────┘ └─────────┘ │   │
│  └───────────────────────────────────────────────────┘   │
└──────────────────────────┬───────────────────────────────┘
                           │ WSS (Signaling)
                           │ WebRTC (Media — P2P)
┌──────────────────────────┴───────────────────────────────┐
│               Signaling Server (Node.js)                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ Session  │ │ Call     │ │   PKD    │ │   Rate     │  │
│  │ Registry │ │ Router   │ │ (Keys)   │ │  Limiter   │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ Heartbeat│ │ Presence │ │   FCM    │ │  Validator │  │
│  │ Manager  │ │ Tracker  │ │ (Push)   │ │ (Input)    │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---
#### ████ DIRECTORY STRUCTURE ████
---

```
stealth/
├── client_android/          # Android app (Kotlin/Java)
│   └── app/src/main/java/com/securecall/app/
│       ├── CallActivity.java       # Main call screen
│       ├── MainActivity.java       # Entry point
│       ├── security/               # Security monitoring
│       │   ├── AudioFocusManager.kt
│       │   ├── ScreenRecordingDetector.kt
│       │   ├── MicrophoneMonitor.kt
│       │   ├── AccessibilityDetector.kt
│       │   ├── CallRecordingDetector.kt
│       │   ├── SecureCallMonitor.kt
│       │   └── SecurityEnforcer.kt
│       ├── config/                 # Tier-based feature flags
│       └── ui/                     # UI fragments
│
├── core_crypto/             # Rust crypto engine
│   └── src/
│       ├── lib.rs                  # Public API
│       ├── aead/                   # XChaCha20-Poly1305
│       ├── identity/               # Key pairs
│       ├── session/                # Session state
│       ├── hkdf/                   # Key derivation
│       └── ffi/                    # JNI bridge
│
├── backend/signaling/       # Node.js signaling server
│   └── src/
│       ├── server.js               # Main entry
│       ├── call_routing.js         # Call routing logic
│       ├── sessions.js             # Session management
│       ├── pkd.js                  # Public Key Directory
│       ├── rateLimit.js            # Rate limiting
│       └── validator.js            # Input validation
│
├── website/                 # Landing page (neabouli.github.io/stealth)
├── docs/                    # Documentation
├── deploy/                  # Docker deployment
├── deployment/              # PM2 bare-metal deployment
├── marketing/               # Play Store listings
└── tools/                   # Build & test scripts
```

---
#### ████ DATA FLOW ANALYSIS ████
---

### Signaling (Call Setup)
```
Client A → WSS → Signaling Server → WSS → Client B
```
- Encrypted WebSocket connection
- Server relays encrypted signaling messages
- Server cannot read message content

### Media (Voice)
```
Client A → WebRTC (P2P, DTLS-SRTP) → Client B
```
- Direct peer-to-peer connection
- Server is NOT in the media path
- Each voice frame individually encrypted

### TURN Relay (When P2P fails)
```
Client A → TURN Server → Client B
```
- Used when direct P2P is blocked by NAT/firewall (~10-15% of connections)
- TURN server sees only encrypted packets
- Cannot decrypt voice content

---
#### ████ BUILD CONFIGURATIONS ████
---

| Flavor | Package Name | Features |
|--------|-------------|----------|
| `free` | `com.securecall.app.free` | Basic E2E, 10 contacts, 15 min |
| `pro` | `com.securecall.app.pro` | Unlimited, HD audio, anti-recording |
| `premium` | `com.securecall.app.premium` | Everything + hardware keystore, auto-terminate |

---

> DOCUMENT END // CLASSIFICATION: RESTRICTED

[← Return to Operations Center](Home.md)
