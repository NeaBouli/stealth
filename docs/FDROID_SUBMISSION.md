# F-Droid Submission — SecureCall

## Merge Request Title
New app: SecureCall — E2E encrypted voice calls (GPL-3.0)

## Description
SecureCall is an end-to-end encrypted voice calling app for Android.

**Key points:**
- GPL-3.0 licensed
- No Google Play Services in F-Droid build
- No tracking, no ads, no personal data
- WebRTC + XChaCha20-Poly1305 encryption
- No phone number or account required
- 30-day trial, then unlock via IFR token or activation code
- Source: https://github.com/NeaBouli/stealth

**F-Droid Build:**
- Package: com.securecall.app.fdroid
- No Firebase, no AdMob, no Crashlytics
- Separate fdroid flavor in build.gradle

**Privacy:**
- Zero personal data stored
- HMAC-SHA256 for all identifiers
- GDPR compliant (EU company)

## Checklist
- [x] GPL-3.0 license
- [x] No proprietary dependencies in fdroid flavor
- [x] Reproducible build (no timestamps)
- [x] Metadata complete
- [x] Source code on GitHub
