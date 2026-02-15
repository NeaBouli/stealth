<!-- markdownlint-disable MD033 MD041 -->
<div align="center">

<!-- TODO: Replace with actual logo -->
<img src="https://via.placeholder.com/200x200?text=SecureCall" alt="SecureCall Logo" width="120" />

# SecureCall

**End-to-End Encrypted Voice Calls**

*Powered by the StealthX Platform*

[![Source Available - Not Open Source](https://img.shields.io/badge/Source%20Available-Not%20Open%20Source-red.svg)](LICENSE)
[![License: Source Available](https://img.shields.io/badge/License-Source%20Available-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Crypto: XChaCha20-Poly1305](https://img.shields.io/badge/Crypto-XChaCha20--Poly1305-purple.svg)](docs/SECURITY_DESIGN.md)
[![Security Audit](https://img.shields.io/badge/Security-Audited-orange.svg)](docs/SECURITY_AUDIT_REPORT.md)

---

**SecureCall is a voice communication app built from the ground up for privacy.**
No metadata. No compromises. Every call is encrypted end-to-end using military-grade cryptography.

[Download](#download) | [Features](#features) | [Security](#security) | [FAQ](#faq) | [Deutsch](#deutsch)

</div>

---

## Features

- **End-to-End Encryption** -- Every voice call is encrypted using XChaCha20-Poly1305 (AEAD). Keys never leave your device.
- **X25519 Key Exchange** -- Ephemeral Diffie-Hellman key agreement ensures perfect forward secrecy. Each call uses a unique session key.
- **Zero-Knowledge Architecture** -- The server facilitates connections but cannot decrypt calls. No call content, no metadata, no logs.
- **GhostNet Transport** -- Custom transport protocol with encrypted frame headers, replay protection, and nonce management.
- **Rust Crypto Core** -- All cryptographic operations run in a native Rust library via JNI -- no Java crypto, no OpenSSL.
- **Open Source Transparency** -- The complete source code is publicly available for independent security review.

## Screenshots

<!-- TODO: Add actual screenshots -->
<div align="center">
<table>
<tr>
<td><img src="https://via.placeholder.com/250x500?text=Home+Screen" alt="Home Screen" width="200"/></td>
<td><img src="https://via.placeholder.com/250x500?text=In+Call" alt="In Call" width="200"/></td>
<td><img src="https://via.placeholder.com/250x500?text=Settings" alt="Settings" width="200"/></td>
</tr>
</table>
</div>

## Download

<!-- TODO: Replace with actual Play Store link -->
<div align="center">

<a href="#">
<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" width="200"/>
</a>

*Coming soon to Google Play*

</div>

## Building from Source

> **This repository is Source Available, not Open Source.**
>
> You may NOT build, distribute, or sell this app yourself.
> Download the official app from Google Play Store only.
> The source code is published for security auditing and transparency.

See the [LICENSE](LICENSE) for full terms.

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

Found a vulnerability? Please report it responsibly: **security@stealthx.app**

See [SECURITY.md](SECURITY.md) for our full security policy.

## Why Source-Available?

We believe encryption software must be transparent. You should never have to trust a black box with your private conversations.

By making our source code publicly available, we enable:
- **Independent security audits** by researchers and the community
- **Verification** that our encryption works as advertised
- **Trust through transparency**, not through marketing

This repository uses a [Source Available License](LICENSE). The code may be viewed and audited, but may not be compiled, distributed, or used commercially. See the [FAQ](#faq) for details.

## FAQ

<details>
<summary><strong>Why can't I build the app myself?</strong></summary>

SecureCall is published under a source-available license. The code is open for inspection and security auditing, but compiling, distributing, or creating derivative works is not permitted. This ensures a single, verified distribution channel through the official app store listing.

</details>

<details>
<summary><strong>How do I know the app is secure?</strong></summary>

The complete source code is publicly available in this repository. We have conducted a comprehensive security audit (see the [Security Audit Report](docs/SECURITY_AUDIT_REPORT.md)) and welcome independent review by security researchers.

</details>

<details>
<summary><strong>What data does the server see?</strong></summary>

The signaling server only facilitates connection establishment. It relays encrypted key exchange messages and signaling data. All voice data is encrypted end-to-end -- the server cannot decrypt any call content. No metadata or call logs are stored.

</details>

<details>
<summary><strong>What cryptographic algorithms are used?</strong></summary>

- **Key Exchange:** X25519 (Curve25519 Diffie-Hellman)
- **Key Derivation:** HKDF-SHA256
- **Encryption:** XChaCha20-Poly1305 (AEAD)
- **Implementation:** Native Rust via JNI (no Java/Android crypto APIs)

</details>

<details>
<summary><strong>How can I report a security issue?</strong></summary>

Please email **security@stealthx.app** with details. Do not open a public GitHub issue for security vulnerabilities. See [SECURITY.md](SECURITY.md) for our full disclosure policy.

</details>

<details>
<summary><strong>Can I contribute code?</strong></summary>

We do not accept code contributions (pull requests) at this time. However, we welcome bug reports, feature requests, and security findings via GitHub Issues. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

</details>

---

## Deutsch

### SecureCall -- Ende-zu-Ende verschluesselte Sprachanrufe

SecureCall ist eine Sprachkommunikations-App, die von Grund auf fuer Privatsphaere entwickelt wurde. Jeder Anruf wird mit XChaCha20-Poly1305 verschluesselt. Die Schluessel verlassen nie Ihr Geraet.

**Warum quelloffen?** Verschluesselungssoftware muss transparent sein. Sie sollten nie einer Blackbox Ihre privaten Gespraeche anvertrauen muessen. Der vollstaendige Quellcode ist oeffentlich einsehbar -- fuer unabhaengige Sicherheitspruefungen und Verifikation.

**Wichtig:** Dieser Code steht unter einer Source-Available-Lizenz. Er darf eingesehen und geprueft, aber nicht kompiliert, verteilt oder kommerziell genutzt werden.

- [Sicherheitsaudit-Bericht](docs/SECURITY_AUDIT_REPORT.md)
- [Architektur-Uebersicht](docs/ARCHITECTURE_OVERVIEW.md)
- [Sicherheitsdesign](docs/SECURITY_DESIGN.md)
- [Sicherheitsmeldungen: security@stealthx.app](SECURITY.md)

---

<div align="center">

**SecureCall** is a product of the **StealthX** platform.

[Contributing](CONTRIBUTING.md) | [License](LICENSE) | [Code of Conduct](CODE_OF_CONDUCT.md) | [Security](SECURITY.md)

</div>
