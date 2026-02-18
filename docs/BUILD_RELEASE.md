# SecureCall Release Build Guide

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK 33
- NDK (for native crypto library)
- Rust toolchain (for core_crypto)
- Release keystore (see [KEYSTORE_INFO.md](KEYSTORE_INFO.md))

## Environment Variables

Set these before building release variants:

```bash
export SECURECALL_STORE_FILE=/path/to/securecall-release-key.jks
export SECURECALL_STORE_PASSWORD=your_keystore_password
export SECURECALL_KEY_ALIAS=securecall
export SECURECALL_KEY_PASSWORD=your_key_password
```

Alternatively, add to `client_android/gradle.properties` (local only, never commit):

```properties
SECURECALL_STORE_FILE=/path/to/securecall-release-key.jks
SECURECALL_STORE_PASSWORD=your_keystore_password
SECURECALL_KEY_ALIAS=securecall
SECURECALL_KEY_PASSWORD=your_key_password
```

## Build Commands

### Android App Bundles (AAB) — recommended for Play Store

```bash
cd client_android

# Build all flavors
./gradlew bundleFreeRelease
./gradlew bundleProRelease
./gradlew bundlePremiumRelease
```

Output locations:
```
app/build/outputs/bundle/freeRelease/app-free-release.aab
app/build/outputs/bundle/proRelease/app-pro-release.aab
app/build/outputs/bundle/premiumRelease/app-premium-release.aab
```

### APKs — for direct distribution / testing

```bash
cd client_android

./gradlew assembleFreeRelease
./gradlew assembleProRelease
./gradlew assemblePremiumRelease
```

Output locations:
```
app/build/outputs/apk/free/release/app-free-release.apk
app/build/outputs/apk/pro/release/app-pro-release.apk
app/build/outputs/apk/premium/release/app-premium-release.apk
```

### Build all at once

```bash
./gradlew bundleFreeRelease bundleProRelease bundlePremiumRelease
```

## Verification

### 1. Install and test release APK

```bash
adb install app/build/outputs/apk/free/release/app-free-release.apk
```

### 2. Verify signing

```bash
apksigner verify --verbose app-free-release.apk
# or for AAB:
jarsigner -verify -verbose app-free-release.aab
```

### 3. Pre-launch checklist

- [ ] Debug features are hidden (no debug menu, no test buttons)
- [ ] Crashlytics is enabled for FREE tier
- [ ] R8/ProGuard minification is active
- [ ] All 3 flavors install and launch correctly
- [ ] Voice calls work end-to-end (encrypted)
- [ ] In-app purchases work in sandbox mode
- [ ] No hardcoded test credentials remain
- [ ] Version name and code are correct (`0.2-beta`, versionCode `2`)

## Version Management

Current version is defined in `client_android/app/build.gradle`:

```gradle
defaultConfig {
    versionCode 2
    versionName "0.2-beta"
}
```

Increment `versionCode` for every Play Store upload. The `versionName` is user-facing.

## R8 / ProGuard

Release builds use R8 minification. Mapping files are generated at:
```
app/build/outputs/mapping/{flavor}Release/mapping.txt
```

Upload these to Play Console for crash deobfuscation, or to Firebase Crashlytics.

## Troubleshooting

| Issue | Solution |
|-------|---------|
| Signing fails | Check environment variables are set correctly |
| Native lib not found | Rebuild core_crypto: `cd core_crypto && cargo build --release` |
| R8 errors | Check `proguard-rules.pro` for missing keep rules |
| Build OOM | Increase Gradle JVM heap in `gradle.properties` |
