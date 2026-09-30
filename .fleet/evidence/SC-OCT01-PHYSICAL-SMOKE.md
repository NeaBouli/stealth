# SC-OCT01-PHYSICAL-SMOKE — sanitized evidence (2026-10-01 01:25-01:30 local)

No Gradle, no rebuild, no uninstall. Original packages (com.securecall.app.pro etc.) untouched. Device serials omitted.
APKs from claude-SC-OCT01-DEVICE-QA build outputs (HEAD ff5d99f assumed, not stamped). SHA-256 prefix (16 hex):
- free app arm64-v8a: com.securecall.app.free.devtest 1.0.50-free vc 78017001, minSdk 24/target 36, 71c9dbda02ad4e9f
- free androidTest: com.securecall.app.free.devtest.test -> AndroidJUnitRunner, 2b7bff84120022e2
- premium app arm64-v8a: com.securecall.app.premium.devtest 1.0.50-premium vc 78017001, bb4ea4a9c587e7bd
- premium androidTest: com.securecall.app.premium.devtest.test -> AndroidJUnitRunner, 1a4461d22ec9e844
Devices: SM-A217F (API 31, arm64-v8a) = Free; SM-T835 (API 29, arm64-v8a) = Premium. install -r -t: 4x Success.

## Instrumentation (adb shell am instrument -w -r)
| Variant/device | run | pass | fail | skip | result |
|---|---|---|---|---|---|
| Free / A21s | 29 | 28 | 0 | 1 | OK (29 tests) |
| Premium / Tab S4 | 30 | 29 | 0 | 1 | OK (30 tests) |
Skip (both): CertificatePinInstrumentedTest.productionChainMatchesConfiguredPins (assumption failure, status -4).
Classes: CallActivity, CertificatePin, DirectEntitlement, IdentitySigningKey, MainActivity, MainScreenLayout,
SecurityFeature, SettingsActivity, TesterDeviceKey (+ PremiumVpnEntitlement on premium only).

## UI hierarchy (uiautomator, Tab S4 premium only, 1600x2560 @360dpi)
- Calls tab: 9 clickables, none <48dp, none off-screen. 4 call-log CardViews have no own label (text only in children).
- Dialer tab: 20 clickables, 20 labelled, none <48dp, none off-screen (btn0-9, *, #, alpha toggle, fabCall, bottom nav).
- Settings tab: 10 section header rows 1600x83 px = 711x37 dp -> below 48dp/44dp touch-target minimum.
  Section labels mix German and English (Konto, Sicherheit, Darstellung, Über vs Calls, Network, Diagnostics).
- Galaxy A21s: keyguard showing; not unlocked (no credentials used) -> no Free UI measurements.
- Screenshots: none taken (FLAG_SECURE left enabled by design).
