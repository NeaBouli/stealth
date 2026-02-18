# Privacy Policy for SecureCall

**Effective Date:** February 18, 2026
**Last Updated:** February 18, 2026

StealthX ("we", "us", "our") operates the SecureCall mobile application (the "App"). This Privacy Policy describes how we collect, use, and protect your information.

## Our Commitment

SecureCall is built on the principle that privacy is a fundamental right. We collect the absolute minimum data necessary to provide our service, and in most cases, we collect no data at all.

## Data Collection by Tier

### FREE Version

| Data Type | Collected | Details |
|-----------|-----------|---------|
| Call content | No | End-to-end encrypted; we cannot access it |
| Call metadata | No | No call logs stored on servers |
| Contacts | No | Contact data never leaves your device |
| Crash reports | Yes (opt-out) | Anonymous crash data via Firebase Crashlytics |
| Analytics | No | No usage analytics or behavioral tracking |
| IP address | Transient | Visible to signaling server during call setup only; not logged |
| Device info | Minimal | OS version and device model included in crash reports only |

### PRO Version

| Data Type | Collected | Details |
|-----------|-----------|---------|
| Call content | No | End-to-end encrypted |
| Call metadata | No | No call logs stored |
| Contacts | No | Never leaves your device |
| Crash reports | No | Crashlytics disabled |
| Analytics | No | Zero telemetry |
| IP address | Transient | Visible during signaling only; not logged |

### PREMIUM Version

| Data Type | Collected | Details |
|-----------|-----------|---------|
| Call content | No | End-to-end encrypted |
| Call metadata | No | No call logs stored |
| Contacts | No | Never leaves your device |
| Crash reports | No | Crashlytics disabled |
| Analytics | No | Zero telemetry |
| IP address | No | Masked via GhostNet relay network |

## How Encryption Works

- All voice calls are encrypted end-to-end using **XChaCha20-Poly1305** (AEAD encryption)
- Key exchange uses **X25519** (Elliptic Curve Diffie-Hellman)
- **Perfect Forward Secrecy** ensures past communications remain secure even if keys are compromised
- Encryption keys are generated on your device and never transmitted to our servers
- We have **zero ability** to decrypt your calls

## Data That Reaches Our Servers

The only data that passes through our infrastructure:

1. **Signaling data** — Encrypted call setup messages (who is calling whom, routed via temporary identifiers)
2. **Encrypted audio packets** — Opaque encrypted data; we cannot read or listen to it

We do **not** store signaling data or audio packets after the call ends.

## Third-Party Services

### FREE Version Only
- **Firebase Crashlytics** (Google) — Collects anonymous crash reports to help us fix bugs. You can opt out in Settings. [Google's Privacy Policy](https://policies.google.com/privacy)

### PRO and PREMIUM Versions
- No third-party services. Zero external data sharing.

### All Versions
- **Google Play Billing** — If you purchase Pro or Premium, Google handles payment processing. We never see your payment details. [Google Play Terms](https://play.google.com/intl/en/about/play-terms/)

## Data Storage

- We do not maintain user accounts or databases of personal information
- Call history is stored **locally on your device only**
- Contact data is stored **locally on your device only**
- No server-side data retention

## Data Sharing

We do **not** sell, trade, rent, or share your personal data with anyone. Period.

We may disclose information only if required by law, but since we don't collect or store personal data, we have nothing to disclose.

## Your Rights (GDPR)

Under the EU General Data Protection Regulation, you have the right to:

- **Access** — Request what data we hold about you (answer: effectively none)
- **Rectification** — Correct inaccurate data
- **Erasure** — Request deletion of your data
- **Portability** — Receive your data in a portable format
- **Object** — Object to data processing
- **Restrict** — Restrict data processing

Since we collect virtually no personal data, most of these rights are satisfied by default. For the FREE version, you can disable Crashlytics in the app settings to stop all data collection.

## Data Deletion

To delete all data associated with SecureCall:

1. Open SecureCall Settings
2. Select "Delete All Data"
3. Uninstall the app

Since we don't store data on our servers, uninstalling the app removes all traces.

## Children's Privacy

SecureCall is not intended for children under 18. We do not knowingly collect data from children.

## Changes to This Policy

We may update this Privacy Policy from time to time. Changes will be posted in the app and on our website. Continued use of the app constitutes acceptance of the updated policy.

## Open Source Transparency

Our source code is publicly available for inspection. You don't have to trust our claims — you can verify them yourself.

Source Code: https://github.com/stealthx/securecall

## Contact

For privacy-related inquiries:

- **Email:** privacy@stealthx.app
- **Website:** https://stealthx.app/privacy
- **GitHub:** https://github.com/stealthx/securecall

---

StealthX
Germany

*This privacy policy is provided in compliance with the EU General Data Protection Regulation (GDPR) and the German Bundesdatenschutzgesetz (BDSG).*
