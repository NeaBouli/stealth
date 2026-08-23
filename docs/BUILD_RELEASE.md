# SecureCall Release Build Guide

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK 36
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

### Google Play App Bundle (AAB)

```bash
cd client_android

# Only the Free flavor is published through Google Play.
./gradlew bundleFreeRelease
```

Output location:
```
app/build/outputs/bundle/freeRelease/app-free-release.aab
```

Never upload a Pro or Premium artifact to the SecureCall Google Play listing.

### APKs — direct distribution

```bash
cd client_android

./gradlew -Pinternal bundleFreeRelease \
  assembleFreeRelease assembleProRelease assemblePremiumRelease
```

Output locations:
```
app/build/outputs/apk/free/release/app-free-release.apk
app/build/outputs/apk/pro/release/app-pro-release.apk
app/build/outputs/apk/premium/release/app-premium-release.apk
```

The bundle task intentionally shares this invocation so the direct APKs are emitted as the
unsuffixed, multi-ABI files listed above. A standalone `assemble*Release` invocation enables ABI
splits and emits `app-<flavor>-universal-release.apk` plus per-ABI APKs instead.

The direct Premium APK contains the optional app-managed WireGuard feature. The Play Free AAB
and direct Pro APK remain free of the VPN service and WireGuard runtime. Read
[`DISTRIBUTION_MATRIX.md`](DISTRIBUTION_MATRIX.md) before every release.

### Build the complete distribution set

```bash
./gradlew -Pinternal bundleFreeRelease \
  assembleFreeRelease assembleProRelease assemblePremiumRelease \
  verifyNoVpnServiceSource verifyFreeReleaseVpnPolicy \
  verifyProReleaseVpnPolicy verifyPremiumReleaseVpnRuntime
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
- [ ] Website purchase links deliver the correct direct edition
- [ ] No hardcoded test credentials remain
- [ ] Version name and code match the planned release

## Version Management

Current version is defined in `client_android/app/build.gradle`:

```gradle
defaultConfig {
    versionCode <next unused code>
    versionName "<release version>"
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
