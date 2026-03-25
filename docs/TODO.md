# SecureCall TODO Tracker
**Last updated:** 2026-03-26 | **Version:** v1.0.5 (code 15)

## DONE (28)

| ID | Task | Notes |
|----|------|-------|
| TODO-001 | FCM push notifications | Client + Server, persistent tokens |
| TODO-002 | WalletConnect v2 integration | Sign SDK 2.26.0, relay connected |
| TODO-003 | TURN credential rotation | Dynamic fetch from /ice-servers |
| TODO-004 | Release APKs all 3 flavors | Signed, smoke tested |
| TODO-005 | Play Store listing | EN + DE texts, screenshots, uploaded |
| TODO-006 | Activation code premium unlock | Verified working |
| TODO-007 | Real encryption (HKDF-SHA256) | HkdfSha256.kt + GhostNetCryptoManager implemented |
| TODO-008 | HKDF key derivation (CRYPTO-03) | Implemented in ghostnet/crypto/ |
| TODO-009 | Samsung background Activity launch | fullScreenIntent + FLAG_KEEP_SCREEN_ON |
| TODO-011 | WireGuard VPN: AGP 8.x | AGP 8.7.3 + Gradle 8.14 |
| TODO-013 | AdMob Free Flavor | Real IDs, SDK 23.6.0 |
| TODO-014 | F-Droid flavor | No Google services, builds |
| TODO-015 | Play Store Listing texts | EN + DE in docs/ |
| TODO-016 | Privacy Policy | stealthx.tech/privacy.html |
| TODO-017 | Emergency Broadcast System | 10 templates, WS + FCM, notification from background |
| TODO-018 | Impressum (EU) | EN/DE/EL, anti-scraping |
| TODO-019 | Contact Email | kaspartisan@proton.me |
| TODO-020 | Google Search Console | Verification tag, sitemap submitted |
| TODO-021 | SEO optimization | FAQ/Product Schema, 31 keywords, hreflang |
| TODO-024 | FCM Push fix | API key + persistent tokens |
| TODO-025 | One-Click Update | UpdateManager, always Play Store |
| TODO-026 | Play Store v1.0.4+ | Internal Testing Release 15 |
| TODO-032 | Railway Backend deploy | All endpoints live |
| TODO-034 | S10 FCM install | Clean install, token registered |
| TODO-035 | Pro/Premium Labels | Badges + upgrade hint |
| TODO-036 | Emergency Delete | 5-tap instant wipe |
| TODO-037 | FCM Token Persistence | data/fcm_tokens.json |
| TODO-038 | FLAG_SECURE tier logic | Free=off, Pro=toggle, Premium=always |

## IN PROGRESS (1)

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| TODO-028 | Google Play closed test | HIGH | 12/12 testers, start 14-day phase |

## OPEN (7)

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| TODO-010 | Self-hosted TURN (coturn) | LOW | Using Metered.ca for now |
| TODO-022 | Google Analytics (GA4) | LOW | Manually add in Firebase Console |
| TODO-023 | Bing Webmaster Tools | LOW | Register at bing.com/webmasters |
| TODO-029 | Google Play Service Account | HIGH | For billing purchase verification |
| TODO-030 | Store Listing DE in Play Console | MEDIUM | docs/PLAY_STORE_LISTING_DE.md exists |
| TODO-031 | GitHub Release APKs | MEDIUM | APKs ready, need gh release create |
| TODO-033 | Firebase + AdMob linking | MEDIUM | AdMob Console → Firebase |
