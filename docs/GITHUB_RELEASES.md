# GitHub Releases Plan

## Release Assets

### Public direct downloads
- `SecureCall-Free-LATEST.apk` — Free direct edition
- `SecureCall-Pro-LATEST.apk` — Pro direct edition; no app-managed VPN
- `SecureCall-Premium-LATEST.apk` — Premium direct edition with optional app-managed WireGuard

### Google Play only
- `app-free-release.aab` — Free Play edition; no app-managed VPN

Pro and Premium artifacts must never be uploaded to the SecureCall Google Play listing. The Play
edition may detect an independently managed system VPN, but it does not create or configure one.

## Release Notes Template

```
# SecureCall <version>

End-to-end encrypted voice calls for Android.
No phone number. No account. Zero metadata.

## Downloads
- **Google Play:** Free edition without an app-managed VPN
- **Direct Free:** SecureCall-Free-LATEST.apk
- **Direct Pro:** SecureCall-Pro-LATEST.apk
- **Direct Premium:** SecureCall-Premium-LATEST.apk with optional app-managed WireGuard

## Features
- E2E encrypted calls (X25519 + DTLS-SRTP)
- No phone number or account required
- Device-managed VPN compatibility
- STEALTH-DELETE emergency wipe
- Emergency Broadcast System
- Website IFR holder discount via signed browser-wallet verification

## Requirements
- Android 7.0+ (API 24)
- Microphone permission
- Internet connection

## Links
- Website: https://stealthx.tech
- Privacy Policy: https://stealthx.tech/privacy.html
- Disclaimer: https://stealthx.tech/disclaimer.html
```

## Create Release via CLI
```bash
gh release create <tag> \
  SecureCall-Free-LATEST.apk \
  SecureCall-Pro-LATEST.apk \
  SecureCall-Premium-LATEST.apk \
  --title "SecureCall <version>" \
  --generate-notes \
  --latest
```

The website uses `/releases/latest/download/...`; therefore the public download release must be a
normal latest release, not a draft or prerelease.
