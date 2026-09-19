> **CLASSIFICATION: RESTRICTED** | **DOCUMENT: SC-ROAD** | **DIVISION: StealthX // SecureCall**

---

# STRATEGIC DEVELOPMENT ROADMAP

---
#### ████ HISTORICAL IMPLEMENTATION BASELINE ████
---

- [x] End-to-End Encryption (XChaCha20-Poly1305 + X25519)
- [x] Rust Crypto Engine with JNI bridge
- [x] Per-call X25519/HKDF-SHA256 session-key derivation
- [x] Node.js Signaling Server
- [x] WebRTC peer-to-peer audio
- [x] Opus audio codec (48kHz)
- [x] Anti-recording controls implemented for targeted device testing
- [x] Material Design 3 UI
- [x] 3 Product Tiers (Free / Pro / Premium)
- [ ] Google Play purchase, restore, refund, revoke, and RTDN lifecycle
- [x] Firebase Cloud Messaging (push notifications)
- [ ] September 2026 audit remediation and independent closure evidence
- [x] R8/ProGuard optimization
- [x] Crashlytics integration (Free tier, opt-out)
- [x] Landing page website (neabouli.github.io/stealth)
- [ ] Product, security, distribution, and Wiki truth synchronized with final release

---
#### ████ ACTIVE OPERATIONS ████
---

- [ ] Integrate reviewed entitlement, browser-proof, pin, privacy, and product-truth changes
- [ ] Resolve registration authentication and identity-key-binding release blockers
- [ ] Complete S10, S7, and Tab S4 call/UI/background/reconnect matrix on the final signed build
- [ ] Pair the exact release with the isolated VLABS signer and entitlement lifecycle
- [ ] Complete Stripe Test and fiscal demo sale/refund/dispute evidence
- [ ] Build fresh direct APKs and Play Free AAB with an unused version code
- [ ] Google Play review and separately approved direct-sales launch

---
#### ████ PLANNED OPERATIONS (v0.3) ████
---

- [ ] WebRTC upgrade (latest spec)
- [ ] Improved NAT traversal
- [ ] Group Calls (Pro/Premium) — up to 5 participants
- [ ] Encrypted call recording (local, Premium only)
- [ ] Biometric lock (fingerprint/face to open app)
- [ ] Widget for quick-call

---
#### ████ LONG-RANGE OBJECTIVES (v1.0+) ████
---

- [ ] iOS Client
- [ ] Desktop Client (Linux, macOS, Windows)
- [ ] Post-Quantum Cryptography (Kyber/ML-KEM)
- [ ] Research a server-side privacy relay network (separate from the app; no app-owned VPN service)
- [ ] Federation support (self-hosted servers)
- [ ] End-to-end encrypted messaging
- [ ] GhostOS — Hardened Android distribution

---
#### ████ EXPLICITLY OUT OF SCOPE ████
---

These are explicitly **not** planned:

- Cloud backup of call history (security risk)
- Social media integration
- Read receipts or "last seen" status
- Contact sync with phone contacts
- Any form of analytics or tracking

---

> DOCUMENT END // CLASSIFICATION: RESTRICTED

[← Return to Operations Center](Home.md)
