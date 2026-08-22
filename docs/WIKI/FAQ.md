> **CLASSIFICATION: RESTRICTED** | **DOCUMENT: SC-FAQ** | **DIVISION: StealthX // SecureCall**

---

# INTELLIGENCE FAQ — BRIEFING

---
#### ████ GENERAL ████
---

### What is SecureCall?
SecureCall is an Android app for end-to-end encrypted voice calls. Every call is encrypted using XChaCha20-Poly1305 with X25519 key exchange and Double Ratchet forward secrecy. The cryptographic engine is written in Rust, and the complete source code is publicly available on GitHub.

### Can you listen to my calls?
No. Calls are encrypted end-to-end. Encryption keys are generated on your device and never sent to our servers. We are technically unable to decrypt your calls, even if compelled by a court order.

### Do I need a phone number or email to sign up?
No. SecureCall generates an anonymous ID on your device. No personal information is required.

### Is SecureCall available for iOS?
Not yet. An iOS version is planned but not under active development. The Rust crypto engine is cross-platform, so the core security will be identical. The planned iOS client will support external device VPNs but will not provide its own VPN tunnel.

### Does SecureCall include a VPN?
The Google Play edition contains no built-in VPN service. It follows a VPN managed by Android or another trusted app and shows a green status LED while SecureCall uses that route. The separately distributed Premium APK can also run an optional, app-only WireGuard tunnel after Android VPN consent. Its configuration remains local and its private key is protected by Android Keystore.

### Why is the source code public?
Transparency builds trust. By publishing our source code, we allow security researchers, journalists, and privacy advocates to verify that our encryption works as claimed. Security through obscurity is no security at all.

---
#### ████ PRIVACY & DATA ████
---

### What data do you collect?
- **Free:** Ads and optional crash reporting where enabled
- **Pro/Premium:** No ads and no crash reporting

No call content, recordings, contact uploads, or persistent server-side call history are stored. FCM and STUN/TURN process the limited technical data required for push delivery and WebRTC connectivity, as documented in the Privacy Policy.

### Do you comply with GDPR?
SecureCall is designed for GDPR-aligned data minimization. Operational processing by FCM, STUN/TURN, ads, and optional crash reporting is documented in the Privacy Policy. StealthX is operated by Vendetta Labs in Greece (EU).

### Can law enforcement access my calls?
No. We cannot provide call content because we do not have it. End-to-end encryption means only the two call participants possess the decryption keys.

---
#### ████ SECURITY ████
---

### What encryption does SecureCall use?
- **Symmetric:** XChaCha20-Poly1305 (256-bit AEAD, 192-bit nonce)
- **Key Exchange:** X25519 (ECDH on Curve25519)
- **Forward Secrecy:** Double Ratchet protocol
- **Key Derivation:** HKDF-SHA256
- **Implementation:** Native Rust via JNI

See [Encryption Architecture](Encryption-Architecture.md) for details.

### What is anti-recording protection?
Pro and Premium tiers include active protection: FLAG_SECURE prevents screen capture, exclusive audio focus blocks other apps from recording, and continuous monitoring detects screen recording apps, microphone hijacking, and known spy/surveillance apps.

- **Pro:** Blocks threats + shows alert dialog
- **Premium:** Automatically terminates the call

### Why Rust for the crypto engine?
Rust guarantees memory safety at compile time, eliminating buffer overflows, use-after-free, and data races — the most common sources of security vulnerabilities in cryptographic code.

### Has SecureCall been audited?
Yes. An internal security audit was conducted in February 2026. All 7 Critical and 18 High findings were fixed. See the [Security Audit Report](Security-Audit.md).

---
#### ████ TIER COMPARISON ████
---

| Feature | Free | Pro (€3.49/mo or $15 Lifetime) | Premium (€4.99/mo or $25 Lifetime) |
|---------|------|-------------------------------|-----------------------------------|
| E2E Encrypted Calls | Yes | Yes | Yes |
| Audio Quality | Standard | HD (48kHz Opus) | HD (48kHz Opus) |
| Contacts | 10 max | Unlimited | Unlimited |
| Call Duration | 15 min max | Unlimited | Unlimited |
| Screen Capture Detection | — | Yes | Yes |
| Anti-Recording Protection | — | Block | Auto-Terminate |
| Spy App Detection | — | Yes | Yes |
| External device VPN compatibility | Yes | Yes | Yes |
| Crash Reports | Opt-out | Disabled | Disabled |
| Telemetry | Minimal | Zero | Zero |

---
#### ████ BILLING ████
---

### What are Forever Licenses?
Forever Licenses are **one-time purchases** that give you lifetime access to Pro or Premium — no subscription needed. Only 100 licenses are available per tier. The price starts low and increases automatically with each sale:

- **PRO Lifetime:** Starts at $15, rises to $50 at sellout (100 licenses)
- **PREMIUM Lifetime:** Starts at $25, rises to $100 at sellout (100 licenses)

Once sold out, only monthly/yearly subscriptions remain. The remaining license count and next price are shown live in the app's Upgrade screen.

### Lifetime vs. Subscription — what should I choose?
- **Lifetime:** Pay once, own forever. Best value for long-term users. Includes all future updates. Available only while licenses last.
- **Subscription:** Pay monthly or yearly. Cancel anytime. Always available.

### How do I upgrade or cancel?
Subscriptions are managed through Google Play. To cancel: Google Play → Subscriptions → SecureCall → Cancel. Features remain active until end of billing period. Lifetime purchases never expire.

### Do you see my payment information?
No. All payments are processed through Google Play Billing. We never see your credit card or billing details.

### How do I delete my data?
Settings → Delete All Data → Uninstall. Since we don't store data on servers, uninstalling removes all traces.

---

> DOCUMENT END // CLASSIFICATION: RESTRICTED

[← Return to Operations Center](Home.md)
