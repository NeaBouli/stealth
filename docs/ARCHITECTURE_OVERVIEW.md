# SecureCall Architecture Overview

## 1. Scope

This document describes the current SecureCall Android 1.0.50 candidate. Research concepts such
as GHOSTOS, QUIC transport, SilentCarrier and multi-hop GhostNet routing are not implemented,
distributed or sold as current SecureCall functionality.

## 2. Current Components

1. **Android client**
   - user interface, contacts, calling and local preferences;
   - WebSocket signaling and FCM notification handling;
   - WebRTC media transport with direct ICE or TURN fallback;
   - Rust/JNI application-frame cryptography;
   - build-flavor and entitlement gates.
2. **Signaling service**
   - pseudonymous SecureID registration;
   - call invites, answers, ICE candidates and call-end messages;
   - push-delivery coordination;
   - entitlement verification and revocation paths.
3. **STUN/TURN providers**
   - network discovery and NAT traversal;
   - TURN forwards encrypted media when a direct WebRTC route is unavailable.

## 3. Current Trust Boundary

- X25519 and HKDF-SHA256 derive per-call key material.
- XChaCha20-Poly1305 protects application media frames in the Rust crypto path.
- Per-call material is discarded after the call.
- SecureCall does not currently implement a Double Ratchet, authenticated long-term identity-key
  binding or post-compromise security.
- The signaling service is not designed to receive call plaintext, but an actively malicious
  signaling service remains outside the current cryptographic protection boundary.
- The service and network providers process the operational metadata documented in the privacy
  policy; SecureCall must not be described as zero-metadata or zero-knowledge.

## 4. Current Data Flow

```text
Client A                 Signaling service                 Client B
   | -- register/invite/ICE/public key material ----------> |
   | <---------- answer/ICE/public key material ----------- |
   |                                                        |
   | ===== encrypted application media over WebRTC ======== |
   |          direct ICE where possible, TURN fallback      |
```

The signaling service coordinates setup. Media does not traverse the signaling service. A TURN
provider may forward encrypted media packets but is not given the per-call private key material.

## 5. Distribution Boundary

- **Google Play Free AAB:** no app-owned VPN or WireGuard implementation; it may follow an
  independently managed Android VPN and display the active-route state.
- **Direct Pro APK:** no app-owned VPN; external Android VPN compatibility only.
- **Direct Premium APK:** may include the separately reviewed optional app-only WireGuard runtime,
  activated only after Android user consent and local configuration.
- No Android variant contains WalletConnect, SIWE or IFR entitlement logic. Any IFR holder benefit
  is a browser-only pre-purchase discount and remains launch-gated.

## 6. Research Roadmap

GHOSTOS, multi-hop relays, QUIC, SilentCarrier, authenticated identity-key binding and alternative
federated signaling are research or future work. They require separate architecture, security,
compatibility and release review before any product claim.
