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

## Submission
1. Fork [F-Droid Data](https://gitlab.com/fdroid/fdroiddata)
2. Add metadata YAML
3. Submit merge request
