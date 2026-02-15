# Build Flavors — SecureCall Android

## Overview

SecureCall uses Android Product Flavors to produce three distinct app tiers from a single codebase. Each tier has its own application ID, feature flags, security enforcement level, and app name.

## Tiers

| | FREE | PRO | PREMIUM |
|---|---|---|---|
| **Application ID** | `com.securecall.app.free` | `com.securecall.app.pro` | `com.securecall.app.premium` |
| **App Name** | SecureCall | SecureCall Pro | SecureCall Premium |
| **Call Duration** | 15 min | Unlimited | Unlimited |
| **Contacts** | 10 | Unlimited | Unlimited |
| **Device Attestation** | No | Yes | Yes |
| **Root Detection** | Warn | Block | Terminate |
| **Certificate Pinning** | No | Yes | Yes |
| **Screen Capture Detection** | No | No | Yes |
| **Debugger Detection** | No | No | Yes (terminate) |
| **Emulator Detection** | No | No | Yes (block) |
| **Hardware Keystore** | No | No | Required |
| **Key Rotation** | Standard | Standard | Aggressive |
| **Telemetry** | Yes (opt-out) | No | No |
| **Analytics** | Yes | No | No |
| **Call Recording** | Allowed | Blocked | Blocked |
| **Logging Level** | DEBUG | WARN | ERROR_ONLY |
| **Enforcement** | WARN | BLOCK | TERMINATE |

## Directory Structure

```
app/src/
├── main/                          # Shared code & resources
│   ├── java/com/securecall/app/
│   │   └── security/
│   │       └── SecurityEnforcer.kt
│   ├── res/
│   │   ├── drawable/logo.png
│   │   ├── mipmap-mdpi/ic_launcher.png
│   │   ├── mipmap-hdpi/ic_launcher.png
│   │   ├── mipmap-xhdpi/ic_launcher.png
│   │   ├── mipmap-xxhdpi/ic_launcher.png
│   │   ├── mipmap-xxxhdpi/ic_launcher.png
│   │   └── values/strings.xml
│   └── AndroidManifest.xml
├── free/
│   ├── java/com/securecall/app/config/FeatureFlags.kt
│   ├── res/values/strings.xml      # app_name = "SecureCall"
│   └── AndroidManifest.xml
├── pro/
│   ├── java/com/securecall/app/config/FeatureFlags.kt
│   ├── res/values/strings.xml      # app_name = "SecureCall Pro"
│   └── AndroidManifest.xml         # usesCleartextTraffic=false
└── premium/
    ├── java/com/securecall/app/config/FeatureFlags.kt
    ├── res/values/strings.xml      # app_name = "SecureCall Premium"
    └── AndroidManifest.xml         # usesCleartextTraffic=false, debuggable=false
```

## Build Commands

```bash
# Debug builds
./gradlew assembleFreeDebug
./gradlew assembleProDebug
./gradlew assemblePremiumDebug

# Release builds
./gradlew assembleFreeRelease
./gradlew assembleProRelease
./gradlew assemblePremiumRelease

# All flavors at once
./gradlew assembleDebug
./gradlew assembleRelease

# Run tests per flavor
./gradlew testFreeDebugUnitTest
./gradlew testProDebugUnitTest
./gradlew testPremiumDebugUnitTest
```

## APK Output

APKs are generated at:
```
app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk
app/build/outputs/apk/{flavor}/release/app-{flavor}-release.apk
```

## SecurityEnforcer

`SecurityEnforcer.kt` is shared code in `main/` that reads `FeatureFlags.SECURITY_ENFORCEMENT` at runtime. It handles violations according to the tier:

- **WARN** (FREE): Log warning, allow app to continue
- **BLOCK** (PRO): Log error, block the action
- **TERMINATE** (PREMIUM): Log error, kill the process immediately

Violation types: `ROOT_DETECTED`, `EMULATOR_DETECTED`, `DEBUGGER_ATTACHED`, `SCREEN_CAPTURE`, `DEVICE_ATTESTATION_FAILED`, `CERTIFICATE_PINNING_FAILED`, `HARDWARE_KEYSTORE_UNAVAILABLE`, `INTEGRITY_CHECK_FAILED`.

## FeatureFlags

Each flavor has its own `FeatureFlags.kt` with `const val` compile-time constants. The Kotlin compiler inlines these values, so there is zero runtime overhead — unused code paths are eliminated at compile time.
