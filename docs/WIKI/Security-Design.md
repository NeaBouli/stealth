# Security Design

## Security Goals

SecureCall is designed around four core security principles:

1. **End-to-End Encryption** — Only the two call participants can decrypt voice data
2. **Metadata Minimization** — Collect and store as little metadata as possible
3. **Zero-Retention** — No call content or logs are stored on servers
4. **Verifiable Transparency** — All source code is publicly auditable

## Threat Model

SecureCall protects against these attacker classes:

| Attacker | Capability | Protection |
|----------|-----------|------------|
| **Network** | Passive eavesdropping, MITM | E2E encryption (XChaCha20-Poly1305), DTLS transport |
| **Server Operator** | Access to signaling infrastructure | Zero-knowledge architecture, E2E encryption |
| **Local (Device)** | Physical access, malware | FLAG_SECURE, key zeroization, anti-recording |
| **App-Level** | Screen recording, mic hijacking | Active monitoring, spy app detection |

### What We Cannot Protect Against

- Compromised operating system (root-level access)
- Hardware implants or baseband attacks
- Shoulder surfing / someone physically listening
- Bugs in third-party code (Android OS, hardware drivers)

## Architecture

```
┌─────────────────────────────────────────────┐
│                 Android App                  │
│  ┌─────────┐  ┌──────────┐  ┌────────────┐ │
│  │   UI    │  │ Security │  │   Audio    │ │
│  │ (MD3)  │  │ Monitor  │  │  Pipeline  │ │
│  └────┬────┘  └────┬─────┘  └─────┬──────┘ │
│       │            │              │          │
│  ┌────┴────────────┴──────────────┴───────┐ │
│  │         Call Manager / Session         │ │
│  └────────────────┬──────────────────────┘ │
│                   │ JNI                     │
│  ┌────────────────┴──────────────────────┐ │
│  │      Rust Crypto Engine (native)      │ │
│  │  XChaCha20-Poly1305 | X25519 | HKDF  │ │
│  └───────────────────────────────────────┘ │
└────────────────────┬────────────────────────┘
                     │ WSS / WebRTC
┌────────────────────┴────────────────────────┐
│           Signaling Server (Node.js)         │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐ │
│  │ Session  │  │   PKD    │  │   Rate    │ │
│  │ Router   │  │ (Keys)   │  │  Limiter  │ │
│  └──────────┘  └──────────┘  └───────────┘ │
└──────────────────────────────────────────────┘
```

## Security Enforcement by Tier

| Threat | Free | Pro | Premium |
|--------|------|-----|---------|
| Screen recording detected | Toast warning | Block + dialog | Auto-terminate call |
| Microphone hijack detected | Toast warning | Block + dialog | Auto-terminate call |
| Spy app detected | Toast warning | Block + dialog | Auto-terminate call |
| Accessibility spy detected | — | Alert | Auto-terminate call |
| FLAG_SECURE | Optional | Default ON | Always enforced |
| Crash reports | Opt-out | Disabled | Disabled |
| IP address visible | Transient | Transient | Masked (GhostNet) |

## Anti-Recording Protection

SecureCall uses six layers of active protection:

1. **FLAG_SECURE** — Android system flag prevents screenshots and screen recording
2. **Audio Focus Lock** — Exclusive audio focus prevents other apps from recording
3. **Screen Recording Detection** — API 34+ callbacks + process monitoring
4. **Microphone Monitoring** — AudioRecordingCallback detects other apps on the mic
5. **Accessibility Detection** — Identifies spy apps using accessibility services
6. **Call Recording App Scan** — Database of 20+ known recording apps

## Key Zeroization

All cryptographic key material is:
- Stored in `ByteArray` (not `String`) to allow explicit clearing
- Wrapped in `Zeroizing<>` types in Rust
- Zeroed from memory immediately after use
- Never logged or transmitted to servers

## For Full Details

- [Encryption Architecture](Encryption-Architecture.md) — Detailed crypto design
- [Security Audit Report](Security-Audit.md) — Audit findings and fixes
- [Full Security Design Document](https://github.com/NeaBouli/stealth/blob/main/docs/SECURITY_DESIGN.md) — Complete German-language design document

---

[← Back to Home](Home.md)
