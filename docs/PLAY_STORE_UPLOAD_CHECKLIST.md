# Play Store Upload Checklist

## Before Upload

- [ ] Google Play Console Account: play.google.com/console
- [ ] Create new app: "SecureCall"
- [ ] Package name: com.securecall.app.free
- [ ] Default language: English (US)

## Upload App Bundle (Internal Testing first)
- [ ] Go to: Release → Testing → Internal Testing
- [ ] Create new release
- [ ] Upload: `app-free-release.aab` (47 MB)
- [ ] Enter release notes (from docs/PLAY_STORE_LISTING.md)
- [ ] Save → Review → Start rollout

## Fill in Store Listing
- [ ] App name: SecureCall — Encrypted Calls
- [ ] Short description (80 chars): End-to-end encrypted voice calls. No phone number. Zero metadata. Open source.
- [ ] Full description: (from docs/PLAY_STORE_LISTING.md)
- [ ] App icon: 512x512 PNG (scale from mipmap-xxxhdpi or logo.png)
- [ ] Feature graphic: 1024x500 PNG (create from og-image)
- [ ] Screenshots: 9 phone + 8 generic available in store_assets/
- [ ] Category: Communication
- [ ] Content rating: IARC questionnaire → Everyone

## Pricing & In-App Products
- [ ] App: free
- [ ] Create In-App Products:
  - `securecall_pro_monthly`: €3.49/month (Subscription)
  - `securecall_pro_yearly`: €34.99/year (Subscription)
  - `securecall_premium_monthly`: €4.99/month (Subscription)
  - `securecall_premium_yearly`: €49.99/year (Subscription)
  - `securecall_pro_lifetime`: $15 one-time (Managed product)
  - `securecall_premium_lifetime`: $25 one-time (Managed product)
  - `securecall_premium_activation_code`: current seller price is €25 one-time; verify the Play product price before publishing

## Privacy & Compliance
- [ ] Privacy Policy URL: https://stealthx.tech/privacy.html
- [ ] Data Safety questionnaire:
  - No personal data collected
  - No data shared with third parties
  - Data encrypted in transit (yes)
  - Users can request data deletion (yes — stealth-delete)
- [ ] Ads declaration: Free tier has AdMob ads

## Contact Details
- [ ] Developer email: kaspartisan@proton.me
- [ ] Website: https://stealthx.tech
- [ ] Privacy Policy: https://stealthx.tech/privacy.html

## AdMob (after app publication)
- [ ] admob.google.com → Link app
- [ ] Copy App ID → replace in `AdMobManager.kt`
- [ ] Create Ad Units (Banner + Interstitial)
- [ ] See `store_assets/ADMOB_TODO.md`

## Google Play Billing Service Account
- [ ] Google Cloud Console → new project or existing one
- [ ] Enable Android Publisher API
- [ ] Create Service Account + JSON Key
- [ ] Base64 encode: `base64 -i key.json | tr -d '\n'`
- [ ] Railway ENV: `GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64`
- [ ] See `docs/GOOGLE_PLAY_BILLING_SETUP.md`

## Release Artifacts

- [ ] Upload only `app-free-release.aab` for package `com.securecall.app.free`.
- [ ] Confirm `verifyFreeReleaseVpnPolicy` passed before upload.
- [ ] Confirm the AAB contains no `VpnService`, WireGuard dependency or native WireGuard library.
- [ ] Do not upload Pro or Premium APK/AAB artifacts to this Play listing.
- [ ] Publish direct Free/Pro/Premium APKs only through the separately documented GitHub/website
      release path in `docs/DISTRIBUTION_MATRIX.md`.

## Signing
- Certificate: CN=SecureCall, O=StealthX
- SHA-256: `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
- SHA-1: `269ddbc877e86ae84312110607237000c4592a00`
