# AdMob Setup — SecureCall Free

## Steps to register

1. Go to https://admob.google.com
2. Click "Apps" → "Add App"
3. Fill in:
   - **App Name:** SecureCall Free
   - **Platform:** Android
   - **Package:** `com.securecall.app.free`
4. Copy the **App ID** (format: `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`)

## Replace test IDs in code

File: `client_android/app/src/free/java/com/securecall/app/ads/AdMobManager.kt`

| Constant | Current (test) | Replace with |
|----------|---------------|--------------|
| `APP_ID` | `ca-app-pub-3940256099942544~3347511713` | Your real App ID |
| `BANNER_AD_UNIT_ID` | `ca-app-pub-3940256099942544/6300978111` | Create Banner unit → copy ID |
| `INTERSTITIAL_AD_UNIT_ID` | `ca-app-pub-3940256099942544/1033173712` | Create Interstitial unit → copy ID |

## Also update AndroidManifest.xml (free flavor)

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="YOUR_REAL_APP_ID"/>
```

## Important
- Do NOT use real IDs during development — Google will ban the account
- Only switch to real IDs right before Play Store submission
- Pro and Premium flavors have no-op AdMob stubs (no ads shown)
