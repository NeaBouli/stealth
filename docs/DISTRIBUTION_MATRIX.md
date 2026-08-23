# SecureCall Distribution Matrix

This file is a mandatory release gate. SecureCall has separate Google Play and direct-download
channels. They share most application code but their artifacts are not interchangeable.

| Distribution | Gradle variant | Package | Artifact | Built-in VPN |
|---|---|---|---|---|
| Google Play | `freeRelease` | `com.securecall.app.free` | `app-free-release.aab` | Never. External VPN detection/indicator only, including after a paid tier unlock. |
| Direct Pro | `proRelease` with `-Pinternal` | `com.securecall.app.pro` | `app-pro-release.apk` | Never. External VPN detection/indicator only. |
| Direct Premium | `premiumRelease` with `-Pinternal` | `com.securecall.app.premium` | `app-premium-release.apk` | Optional app-managed WireGuard after Android consent. |

## Permanent Rules

- Google Play receives only the Free AAB. The Play artifact must not contain `VpnService`,
  WireGuard dependencies, native WireGuard libraries, VPN configuration controls or dynamic VPN
  installation/download behavior.
- Google Play Billing or an activation code may unlock Pro/Premium application features in the
  Play package. This changes entitlement only: it does not install VPN code and does not turn the
  Play package into the direct Premium APK.
- Direct Premium is a separately installed package and is the only SecureCall distribution with
  the optional built-in WireGuard feature. SecureCall does not supply an endpoint; users provide a
  valid configuration from their VPN provider.
- Direct Pro remains VPN-service-free.
- Every edition may follow an external Android VPN. The shared green LED appears only when Android
  reports `TRANSPORT_VPN` for the active route.
- Store, website, Wiki, README and release notes must clearly distinguish a Play entitlement from
  the separately installed direct Premium package.

## Required Release Gates

Run from `client_android` with the Android SDK configured:

```bash
./gradlew --no-daemon --max-workers=1 -Pinternal \
  testFreeDebugUnitTest testPremiumDebugUnitTest \
  verifyNoVpnServiceSource verifyFreeReleaseVpnPolicy \
  verifyProReleaseVpnPolicy verifyPremiumReleaseVpnRuntime \
  lintFreeRelease assembleFreeRelease bundleFreeRelease \
  assembleProRelease assemblePremiumRelease
```

Then inspect the outputs:

- Free AAB: zero `VpnService`, `wireguard`, or `libwg` entries.
- Pro APK: zero `VpnService`, `wireguard`, or `libwg` entries.
- Premium APK: both Premium controller and upstream GoBackend VPN services plus expected native
  WireGuard libraries.
- Confirm package names, version name/code and signatures before publishing.

## Release Destinations

- `app-free-release.aab` -> SecureCall Google Play listing only.
- `app-free-release.apk` -> optional direct Free download.
- `app-pro-release.apk` -> direct Pro download only.
- `app-premium-release.apk` -> direct Premium download only.

Record actual build, artifact inspection and physical-device results in both project Bridges. A
Google Play upload, GitHub release, website deployment or production rollout remains a separate
external action and must use the matching artifact from this matrix.
