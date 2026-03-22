# AdMob Setup — SecureCall Free Flavor

## Current Status
Test Ad IDs are active for development. Replace with production IDs before Play Store release.

## Production Setup
1. Go to [admob.google.com](https://admob.google.com)
2. Add App → Android → `com.securecall.app.free`
3. Create Ad Units:
   - Banner (320x50) → copy Ad Unit ID
   - Interstitial → copy Ad Unit ID
4. Copy the App ID from AdMob dashboard
5. Replace in `AdMobManager.kt`:
   - `BANNER_ID` → your banner ad unit ID
   - `INTERSTITIAL_ID` → your interstitial ad unit ID
6. Replace in `src/free/AndroidManifest.xml`:
   - `com.google.android.gms.ads.APPLICATION_ID` → your app ID

## Test IDs (current — safe for debug)
- App ID: `ca-app-pub-3940256099942544~3347511713`
- Banner: `ca-app-pub-3940256099942544/6300978111`
- Interstitial: `ca-app-pub-3940256099942544/1033173712`

## Ad Placement
- **Banner**: Bottom of MainActivity, above BottomNavigationView
- **Interstitial**: After every 3rd completed call
- **Never during**: Active calls, incoming call screen

## GDPR (EU — Greece)
- Consent dialog required before showing personalized ads
- TODO: Implement UMP (User Messaging Platform) SDK
- For now: test ads don't require consent

## Flavor Architecture
- `src/free/` → Full AdMobManager with real ad loading
- `src/pro/` → No-op stub (no ads)
- `src/premium/` → No-op stub (no ads)
