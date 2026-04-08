# F-Droid Distribution — SecureCall

## Requirements
F-Droid requires apps to be free of proprietary libraries.
The `fdroid` build flavor removes:
- Google AdMob (no ads)
- Google Play Billing (no IAP)
- Firebase FCM (uses WebSocket for push)
- Firebase Crashlytics (no crash reporting)

## Build Flavor
```
com.securecall.app.fdroid
```

## Building
```bash
cd client_android
./gradlew assembleFdroidRelease
```

## Differences from Free Flavor
| Feature | Free | F-Droid |
|---------|------|---------|
| Ads | AdMob banner + interstitial | None |
| Push notifications | FCM | WebSocket only |
| Crash reporting | Firebase Crashlytics | None |
| In-app purchase | Google Play Billing | Disabled |
| Tier unlock | IAP + Code + IFR | Code + IFR only |
| Donation prompt | No | Yes (crypto addresses) |

## F-Droid Metadata
Metadata file: `fdroid/metadata/com.securecall.app.fdroid.yml`

## Current Release
- Version: 1.0.13-fdroid (versionCode 31)
- APK: `releases/app-fdroid-release-v31.apk` (77 MB)
- Tag: `v4.0-fcm-fixed`

## F-Droid Submission Guide

### Step 1: Fork fdroiddata
- Go to: https://gitlab.com/fdroid/fdroiddata
- Click "Fork" to create your copy

### Step 2: Add metadata
- Copy `fdroid/metadata/com.securecall.app.fdroid.yml`
- Into your fork at: `metadata/com.securecall.app.fdroid.yml`

### Step 3: Create Merge Request
- Title: `New app: SecureCall — E2E encrypted calls`
- Description: Include app summary and privacy note
- Target branch: `master`

### Step 4: Review process
- F-Droid team reviews (typically 1-4 weeks)
- They build the app from source themselves
- Address any feedback in MR comments

### Pre-submission checklist
- [x] No hardcoded API keys in fdroid flavor
- [x] No Google Play Services in fdroid flavor
- [x] Firebase/Crashlytics plugins disabled for fdroid
- [x] Telemetry + analytics disabled (CompileTimeFeatureProvider)
- [x] Build reproducible (`assembleFdroidRelease` works)
- [x] License: GPL-3.0-only (F-Droid compliant)
- [ ] Create stable release tag matching metadata commit ref
