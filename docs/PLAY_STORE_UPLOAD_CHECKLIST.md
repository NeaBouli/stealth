# Play Store Upload Checklist

## Vor dem Upload

- [ ] Google Play Console Account: play.google.com/console
- [ ] Neue App erstellen: "SecureCall"
- [ ] Package name: com.securecall.app.free
- [ ] Default language: English (US)

## App Bundle hochladen (Internal Testing zuerst)
- [ ] Gehe zu: Release → Testing → Internal Testing
- [ ] Create new release
- [ ] Upload: `app-free-release.aab` (47 MB)
- [ ] Release notes eingeben (aus docs/PLAY_STORE_LISTING.md)
- [ ] Save → Review → Start rollout

## Store Listing ausfüllen
- [ ] App name: SecureCall — Encrypted Calls
- [ ] Short description (80 chars): End-to-end encrypted voice calls. No phone number. Zero metadata. Open source.
- [ ] Full description: (aus docs/PLAY_STORE_LISTING.md)
- [ ] App icon: 512x512 PNG (aus mipmap-xxxhdpi skalieren oder logo.png)
- [ ] Feature graphic: 1024x500 PNG (aus og-image erstellen)
- [ ] Screenshots: 9 phone + 8 generic vorhanden in store_assets/
- [ ] Category: Communication
- [ ] Content rating: IARC questionnaire → Everyone

## Preise & In-App Products
- [ ] App: kostenlos
- [ ] In-App Products erstellen:
  - `securecall_pro_monthly`: €3.49/month (Subscription)
  - `securecall_pro_yearly`: €34.99/year (Subscription)
  - `securecall_premium_monthly`: €4.99/month (Subscription)
  - `securecall_premium_yearly`: €49.99/year (Subscription)
  - `securecall_pro_lifetime`: $15 one-time (Managed product)
  - `securecall_premium_lifetime`: $25 one-time (Managed product)
  - `securecall_premium_activation_code`: €49 one-time (Managed product)

## Datenschutz & Compliance
- [ ] Privacy Policy URL: https://stealthx.tech/privacy.html
- [ ] Data Safety questionnaire:
  - No personal data collected
  - No data shared with third parties
  - Data encrypted in transit (yes)
  - Users can request data deletion (yes — stealth-delete)
- [ ] Ads declaration: Free tier has AdMob ads

## Kontaktdaten
- [ ] Developer email: kaspartisan@proton.me
- [ ] Website: https://stealthx.tech
- [ ] Privacy Policy: https://stealthx.tech/privacy.html

## AdMob (nach App-Veröffentlichung)
- [ ] admob.google.com → App verknüpfen
- [ ] App ID kopieren → `AdMobManager.kt` ersetzen
- [ ] Ad Units erstellen (Banner + Interstitial)
- [ ] Siehe `store_assets/ADMOB_TODO.md`

## Google Play Billing Service Account
- [ ] Google Cloud Console → neues Projekt oder bestehendes
- [ ] Android Publisher API aktivieren
- [ ] Service Account erstellen + JSON Key
- [ ] Base64 encode: `base64 -i key.json | tr -d '\n'`
- [ ] Railway ENV: `GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64`
- [ ] Siehe `docs/GOOGLE_PLAY_BILLING_SETUP.md`

## Release Artifacts
Gesichert in `~/Documents/SecureCall-Release/final/`:
- `app-free-release.apk` (79 MB) + `.aab` (47 MB)
- `app-pro-release.apk` (77 MB) + `.aab` (44 MB)
- `app-premium-release.apk` (77 MB) + `.aab` (44 MB)
- Keystore: `~/Documents/SecureCall-Release/securecall-release-key.jks`

## Signing
- Certificate: CN=SecureCall, O=StealthX
- SHA-256: `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`
- SHA-1: `269ddbc877e86ae84312110607237000c4592a00`
