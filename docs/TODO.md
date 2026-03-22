# SecureCall TODO Tracker

| ID | Task | Status | Priority | Notes |
|----|------|--------|----------|-------|
| TODO-001 | FCM push notifications | DONE | High | Client ✅ Server ✅ Verified: force-stopped S10 woken by FCM, IncomingCallActivity launched |
| TODO-002 | WalletConnect v2 integration | DONE | High | Sign SDK 2.26.0, Project ID registered, deep link securecall://wc |
| TODO-003 | TURN credential rotation | DONE | Medium | Dynamic fetch from /ice-servers, no hardcoded credentials in APK |
| TODO-004 | Release APKs all 3 flavors | DONE | High | Signed with securecall-release-key.jks, smoke tested on all devices |
| TODO-005 | Play Store listing | OPEN | High | Checklist at docs/PLAY_STORE_CHECKLIST.md |
| TODO-006 | Activation code premium unlock | VERIFIED | High | Fully implemented and working |
| TODO-007 | Wire real encryption (replace mock handshake) | OPEN | Critical | GhostNet uses placeholder crypto in debug |
| TODO-008 | HKDF key derivation (CRYPTO-03) | OPEN | High | Uses random bytes instead of HKDF-SHA256 |
| TODO-009 | Samsung background Activity launch | OPEN | Medium | fullScreenIntent unreliable on Samsung locked screen |
| TODO-010 | Self-hosted TURN (coturn) | OPEN | Medium | Config exists in deployment/coturn_config/ |
| TODO-011 | WireGuard VPN: upgrade AGP 7.4→8.x | DONE | High | AGP 8.7.3 + Gradle 8.14 + WireGuard tunnel 1.0.20260102 + GoBackend |
| TODO-012 | WalletConnect v2 Integration | DONE | High | Merged with TODO-002 |
| TODO-013 | AdMob Werbung Free Flavor | OPEN | High | Banner + Interstitial nach Anrufen |
| TODO-014 | F-Droid Version vorbereiten | OPEN | Medium | Separate Build ohne proprietäre Libs |
| TODO-015 | Play Store Listing | OPEN | High | Screenshots morgen, Beschreibung + Datenschutz heute |
| TODO-016 | Datenschutzerklärung | OPEN | High | Hosted auf GitHub Pages (keine eigene Domain nötig) |

## Completed

- TODO-006: Activation code feature verified working. UI in SettingsFragment, backend validates codes, tier persisted and applied on restart. Re-verified 2026-03-19: TEST-PRO1-CODE on Tab S4 Free → PRO upgrade confirmed via TierManager log.
