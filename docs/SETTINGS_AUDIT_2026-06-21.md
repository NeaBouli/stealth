# SecureCall Settings Audit
> Scope: `client_android/app/src/main/res/xml/preferences.xml`, `SettingsFragment.kt`, tier/config providers, billing upgrade screens, VPN/update/custom-id helpers. Date: 2026-06-21. Result after fixes: 0 FAIL / 0 WARN / 12 PASS.

## Summary
The Settings surface was reviewed from the first preference to the last. The main issues were stale IFR upgrade copy in the app, a VPN toggle persistence bug, unsafe external URL launching, and an XML/selectability mismatch for Licenses. These were fixed and verified with release builds.

## Findings by Domain

### Account And Upgrade - PASS
- Fixed app-side IFR references from Settings upgrade copy, the Free upgrade sideload hint, and tier-provider comments.
- Upgrade now opens `https://stealthx.tech/#pricing`.
- Activation-code unlock remains wired through `WebSocketService.activateCode(...)` and `TierManager.setActivatedTier(...)`.

### Background Service - PASS
- The existing v75 fix remains in place: the switch controls service autostart, boot start, keep-alive, WebSocket shutdown, wake-lock release, notification removal, and service stop.

### Privacy And Call History - PASS
- `pref_call_history` is intentionally not handled in `SettingsFragment`; Android stores it in default preferences.
- `CallActivity` and `IncomingCallActivity` read `pref_call_history` before persisting call records.

### Appearance - PASS
- Dark-mode list preference is wired to `AppCompatDelegate`.

### Security And Anti-Recording - PASS
- Cert pinning, attestation, hardware keystore, screenshot blocking, exclusive mic, screen-recording detection, and security level are gated from the active `FeatureProvider`.

### Anonymous Network And eSIM - PASS
- eSIM setup and routing are gated by device capability and tier.
- Preferred-network selection persists through `NetworkManager` and refreshes the displayed network status.

### VPN - PASS
- Fixed: enabling VPN no longer persists `vpn_enabled=true` before configuration exists.
- Fixed: clearing VPN config stops VPN, unchecks the toggle, clears persisted enabled state, and refreshes status.
- Fixed: VPN status refreshes on Settings resume.

### Battery Optimization - PASS
- Battery Optimization opens the correct Android settings flow and now refreshes on resume.

### About And Legal Links - PASS
- Verified links return HTTP 200: GitHub, GitHub Wiki, user manual, bug report, privacy, terms, disclaimer, custom-id page, and pricing page.
- Fixed: external URL opening is wrapped in a failure handler so Settings will not crash if no browser is available.
- Fixed: Licenses is selectable in XML and click-wired to the disclaimer/legal page.

### Support Development - PASS
- ETH, BTC, and SOL support entries copy their full configured address to the clipboard.

### Diagnostics - PASS
- SecLog enable/export/clear are gated to paid tiers and wired to `SecLogManager`.

### Advanced Reset - PASS
- Reset App remains intentionally implemented as the 5-tap stealth-delete flow.

## Verification
- `./gradlew --no-daemon --max-workers=1 verifyNoAppIfrWalletCode` passed.
- `./gradlew -Pinternal --no-daemon --max-workers=1 assembleRelease` passed.
- `./gradlew --no-daemon --max-workers=1 bundleFreeRelease` passed.
- S7 and Tab S4 were updated with SecureCall Free/Pro/Premium `versionCode=76001`.
