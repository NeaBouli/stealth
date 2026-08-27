<!-- markdownlint-disable MD033 MD041 -->
<div align="center">

<img src="logo.png" alt="SecureCall Logo" width="120" />

# SecureCall

**End-to-End Encrypted Voice Calls**

*Powered by the StealthX Platform*

[![Source Available](https://img.shields.io/badge/License-Source--Available-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-34A853.svg)](https://developer.android.com)
[![Crypto: XChaCha20-Poly1305](https://img.shields.io/badge/Crypto-XChaCha20--Poly1305-7C6CFF.svg)](docs/SECURITY_DESIGN.md)
[![Security: Audited](https://img.shields.io/badge/Security-Audited-orange.svg)](docs/SECURITY_AUDIT_REPORT.md)
[![Rust Crypto Engine](https://img.shields.io/badge/Engine-Rust-DEA584.svg)](core_crypto/)
[![Version](https://img.shields.io/badge/Version-v1.0.50-4ade80.svg)](https://github.com/NeaBouli/stealth/releases)
[![Website](https://img.shields.io/badge/Website-stealthx.tech-34D399.svg)](https://stealthx.tech)
[![Testers](https://img.shields.io/badge/Beta_Testers-15-blue.svg)](https://play.google.com/apps/testing/com.securecall.app.free)

---

**SecureCall is a voice communication app built from the ground up for privacy.**
No call content ever leaves your device unencrypted. Every call is protected end-to-end using military-grade cryptography.

[Website](https://stealthx.tech) | [Play Store Beta](https://play.google.com/apps/testing/com.securecall.app.free) | [Download APK](https://github.com/NeaBouli/stealth/releases/latest) | [Features](#features) | [Security](#security)

</div>

---

## Features

- **End-to-End Encryption** -- Every voice call is encrypted using XChaCha20-Poly1305 (AEAD). Keys never leave your device.
- **X25519 Key Exchange** -- Ephemeral Diffie-Hellman key agreement ensures perfect forward secrecy. Each call uses a unique session key.
- **Zero-Knowledge Architecture** -- The server facilitates connections but cannot decrypt calls. No call content is stored or accessible server-side. Signaling metadata is processed transiently for connection setup.
- **Anti-Recording Protection** -- Active detection of screen recording, microphone hijacking, and spy apps (Pro/Premium).
- **VPN-aware routing** -- Every edition follows Android's active VPN route and shows a green status LED while SecureCall traffic uses it. The Google Play edition contains no VPN service. The direct-download Premium APK additionally supports an optional, consent-gated WireGuard configuration stored locally with its private key encrypted by Android Keystore.
- **Rust Crypto Core** -- All cryptographic operations run in a native Rust library via JNI -- no Java crypto, no OpenSSL.
- **Source-available client** -- The client source code is publicly visible for transparency and independent audit. Copying, building, running, distributing, rebranding, or using the software requires prior written permission from Vendetta Labs.

## IFR Status

The current SecureCall release does not include WalletConnect or in-app IFR tier unlocking. The former IFR/SIWE Android build is retained only as the internal test tag `internal-ifr-wallet-test-2026-06-18`. IFR holder benefits are browser-only before purchase: prove wallet ownership, verify any positive IFR balance read-only, receive the seller-defined checkout discount, then unlock SecureCall through the normal license or activation-code path.

## Architecture

SecureCall consists of three core components:

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Android Client** | Kotlin / Java | User interface, audio capture, call management |
| **Crypto Engine** | Rust (via JNI) | XChaCha20-Poly1305, X25519, HKDF-SHA256 |
| **Signaling Server** | Node.js | Connection establishment, key exchange relay |

For technical details, see the [Architecture Overview](docs/ARCHITECTURE_OVERVIEW.md).

## Security

Security is not a feature -- it's the foundation. Our approach:

- **Independently auditable** -- All source code is publicly available.
- **No trust required** -- Verify the cryptographic implementation yourself.
- **Formal security audit** -- Read the full [Security Audit Report](docs/SECURITY_AUDIT_REPORT.md).
- **Security design** -- Review our [Security Design Document](docs/SECURITY_DESIGN.md).

Found a vulnerability? Please report it via [GitHub Issues](https://github.com/NeaBouli/stealth/issues).

See [SECURITY.md](SECURITY.md) for our full security policy.

## Documentation

Complete documentation is available in the [Wiki](https://github.com/NeaBouli/stealth/wiki) and the `docs/WIKI/` directory:

| Category | Pages |
|----------|-------|
| **User Docs** | [Installation Guide](docs/WIKI/Installation-Guide.md) · [User Manual](docs/WIKI/User-Manual.md) · [FAQ](docs/WIKI/FAQ.md) |
| **Security** | [Security Design](docs/WIKI/Security-Design.md) · [Audit Report](docs/WIKI/Security-Audit.md) · [Encryption Architecture](docs/WIKI/Encryption-Architecture.md) |
| **Developer** | [Architecture](docs/WIKI/Architecture.md) · [Build Instructions](docs/WIKI/Build-Instructions.md) · [API Docs](docs/WIKI/API-Documentation.md) |
| **Project** | [Roadmap](docs/WIKI/Roadmap.md) · [Changelog](docs/WIKI/Changelog.md) · [Known Issues](docs/WIKI/Known-Issues.md) |

## Download

<div align="center">

**Download:** [GitHub Releases](https://github.com/NeaBouli/stealth/releases/latest) | [stealthx.tech](https://stealthx.tech)

**Distribution note:** Google Play ships the Free edition without an app-owned VPN service to meet
Google Play's VpnService distribution requirements. It still follows and indicates an external
device VPN. Paid features unlocked inside the Play package remain VPN-service-free. The separately
distributed Premium APK adds optional app-managed WireGuard support after Android consent and
requires a valid configuration from the user's VPN provider.

</div>

## Building from Source

> **This repository is source-available, not open source.**
>
> You may read and inspect the source code for transparency and security review.
> You may not copy, modify, build, run, distribute, rebrand, host, or use the
> software or official StealthX services without prior written permission from
> Vendetta Labs. The SecureCall name and branding are trademarked.

See the [LICENSE](LICENSE) for full terms.

## Third-Party Services & Transparency

SecureCall uses the following third-party services. **All voice data is encrypted end-to-end on your device before any data leaves it.** No third party can read, intercept, or decrypt your call content.

| Service | Purpose | Data Access |
|---------|---------|-------------|
| **Railway.app** | Cloud hosting for the signaling server | Relays encrypted signaling messages only. Cannot decrypt calls. No call logs stored. |
| **Metered.ca** | TURN relay server for NAT traversal | Relays encrypted media packets when direct peer-to-peer connection fails. Cannot decrypt content. |
| **Google STUN** | NAT discovery (public IP detection) | Receives IP address only for connection setup. No call data transmitted. Standard WebRTC protocol. |
| **Firebase Cloud Messaging** | Push notifications for incoming calls | Delivers notification metadata only (caller name, session ID). No call content is transmitted via FCM. |
| **GitHub Pages** | Project website hosting | Static website only. No user data collected or processed. |

**Key guarantees:**

- The signaling server is **zero-knowledge** -- it facilitates connections but cannot decrypt any call content.
- TURN relay servers only see encrypted packets -- decryption keys exist only on the two call participants' devices.
- Firebase provides push notifications for all tiers. The Free tier can send anonymous Crashlytics diagnostics and exposes an in-app opt-out; Pro and Premium keep Crashlytics disabled. No Firebase Analytics events are collected.
- No call content or recordings are shared with, sold to, or accessible by any third party. Signaling metadata is processed transiently for connection setup.

For the full privacy policy, see [Privacy Policy](https://stealthx.tech/privacy.html).

## Security by Tier

| Feature | Free | Pro | Premium |
|---------|------|-----|---------|
| E2E Encryption (XChaCha20-Poly1305) | Yes | Yes | Yes |
| Root Detection | Warning only | Blocks | Blocks |
| Certificate Pinning | Yes | Yes | Yes |
| Screen Capture Detection | No | No | Yes |
| Debugger Detection | No | No | Yes |
| Emulator Detection | No | No | Yes |
| Hardware Keystore Required | No | No | Yes |
| Call Recording Allowed | Yes | No | No |
| Aggressive Key Rotation | No | No | Yes |
| Ads | Yes (AdMob) | No | No |
| Crash Reports | Yes (opt-out) | No | No |

> **Note:** Core encryption is identical across all tiers. Security differences affect device-level protections and privacy features.

## FAQ

<details>
<summary><strong>Can I build the app myself?</strong></summary>

SecureCall's client source code is publicly available for transparency and independent security auditing. You may read and inspect the code, but you may not copy, modify, build, run, distribute, rebrand, host, or use SecureCall or official StealthX services without prior written permission from Vendetta Labs.

</details>

<details>
<summary><strong>How do I know the app is secure?</strong></summary>

The complete source code is publicly available in this repository. We have conducted a comprehensive security audit (see the [Security Audit Report](docs/SECURITY_AUDIT_REPORT.md)) and welcome independent review by security researchers.

</details>

<details>
<summary><strong>What data does the server see?</strong></summary>

The signaling server only facilitates connection establishment. It relays encrypted key exchange messages and signaling data. All voice data is encrypted end-to-end -- the server cannot decrypt any call content. No persistent call history or call recordings are stored. Signaling metadata (connection IDs, session IDs) is processed transiently. FCM tokens may be stored for push delivery and cleared on deregistration. STUN/TURN providers may see network-level IPs required for WebRTC connectivity.

</details>

<details>
<summary><strong>What cryptographic algorithms are used?</strong></summary>

- **Key Exchange:** X25519 (Curve25519 Diffie-Hellman)
- **Key Derivation:** HKDF-SHA256
- **Encryption:** XChaCha20-Poly1305 (AEAD)
- **Forward Secrecy:** Double Ratchet protocol
- **Implementation:** Native Rust via JNI (no Java/Android crypto APIs)

</details>

<details>
<summary><strong>Does SecureCall include a VPN?</strong></summary>

The Google Play edition contains no built-in VPN service. It follows a VPN already managed by Android or another trusted app and shows a green status LED while that route is active. Paid feature entitlements do not change that boundary. The separately distributed Premium APK can also run an optional, app-managed WireGuard tunnel after Android's VPN consent; its configuration stays on the device and its private key is encrypted with Android Keystore. TURN relay remains available when direct WebRTC connectivity is unavailable.

</details>

<details>
<summary><strong>How can I report a security issue?</strong></summary>

Please [open a GitHub Issue](https://github.com/NeaBouli/stealth/issues). See [SECURITY.md](SECURITY.md) for our full disclosure policy.

</details>

---

## Deutsch

### SecureCall -- Ende-zu-Ende verschluesselte Sprachanrufe

SecureCall ist eine Sprachkommunikations-App, die von Grund auf fuer Privatsphaere entwickelt wurde. Jeder Anruf wird mit XChaCha20-Poly1305 verschluesselt. Die Schluessel verlassen nie Ihr Geraet.

**Warum quelloffen?** Verschluesselungssoftware muss transparent sein. Sie sollten nie einer Blackbox Ihre privaten Gespraeche anvertrauen muessen. Der vollstaendige Quellcode ist oeffentlich einsehbar -- fuer unabhaengige Sicherheitspruefungen und Verifikation.

**Wichtig:** Der Client-Quellcode ist oeffentlich einsehbar fuer Transparenz und unabhaengige Pruefung. Die offiziellen SecureCall-/StealthX-Marken, Backend-Dienste, Store-Releases sowie Pro/Premium-Lizenzen werden von Vendetta Labs betrieben und duerfen nicht ohne Erlaubnis kommerziell oder als offizielle Angebote genutzt werden.

- [Sicherheitsaudit-Bericht](docs/SECURITY_AUDIT_REPORT.md)
- [Architektur-Uebersicht](docs/ARCHITECTURE_OVERVIEW.md)
- [Sicherheitsdesign](docs/SECURITY_DESIGN.md)
- [Issue melden](https://github.com/NeaBouli/stealth/issues)

---

<div align="center">

**SecureCall** is a product of the **StealthX** platform.

[Contributing](CONTRIBUTING.md) | [License](LICENSE) | [Code of Conduct](CODE_OF_CONDUCT.md) | [Security](SECURITY.md)

</div>
