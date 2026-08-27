# AdMob Setup — SecureCall Free Flavor

## Current Status
Production AdMob IDs are configured for the Free flavor. UMP 3.0.0 refreshes
consent on launch, keeps ad requests fail-closed until consent permits them,
and exposes the privacy-options entry point when Google requires it.

## Production Setup
1. Go to [admob.google.com](https://admob.google.com)
2. Add App → Android → `com.securecall.app.free`
3. Create Ad Units:
   - Banner (320x50) → copy Ad Unit ID
   - Interstitial → copy Ad Unit ID
4. Copy the App ID from AdMob dashboard
5. Verify the IDs in `AdMobManager.kt` and `src/free/AndroidManifest.xml`.
6. Publish the required GDPR/privacy message in AdMob Privacy & messaging.

## Google Test IDs
- App ID: `ca-app-pub-3940256099942544~3347511713`
- Banner: `ca-app-pub-3940256099942544/6300978111`
- Interstitial: `ca-app-pub-3940256099942544/1033173712`

## Ad Placement
- **Banner**: Bottom of MainActivity, above BottomNavigationView
- **Interstitial**: After every 3rd completed call
- **Never during**: Active calls, incoming call screen

## GDPR (EU — Greece)
- UMP requests current consent information on every app launch.
- No ad request is sent unless `canRequestAds()` is true.
- A visible settings entry opens Google's privacy-options form when required.
- The corresponding privacy message must remain published in AdMob.

## Flavor Architecture
- `src/free/` → Full AdMobManager with real ad loading
- `src/pro/` → No-op stub (no ads)
- `src/premium/` → No-op stub (no ads)
