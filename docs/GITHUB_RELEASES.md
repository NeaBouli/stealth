# GitHub Releases Plan

## v1.0-beta Release Assets

### PUBLIC (direct download)
- `app-free-release.apk` (79 MB) — Free tier with ads

### NOT uploaded (Play Store only)
- `app-pro-release.apk` — upgrade via in-app purchase
- `app-premium-release.apk` — upgrade via in-app purchase
- `.aab` bundles — Play Store internal

## Release Notes Template

```
# SecureCall v1.0 Beta

End-to-end encrypted voice calls for Android.
No phone number. No account. Zero metadata.

## Downloads
- **Free (with ads):** app-free-release.apk
- **Pro & Premium:** [Google Play](https://play.google.com/store/apps/details?id=com.securecall.app.free)

## Features
- E2E encrypted calls (X25519 + DTLS-SRTP)
- No phone number or account required
- WireGuard VPN (Premium)
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
gh release create v1.0-beta \
  ~/Documents/SecureCall-Release/final/app-free-release.apk \
  --title "SecureCall v1.0 Beta" \
  --notes-file docs/RELEASE_NOTES.md \
  --prerelease
```
