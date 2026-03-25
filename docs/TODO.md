# SecureCall TODO Tracker
**Last updated:** 2026-03-25 | **Version:** v1.0.4 (code 14)

## DONE

| ID | Task | Notes |
|----|------|-------|
| TODO-001 | FCM push notifications | Client + Server verified, tokens registered |
| TODO-002 | WalletConnect v2 integration | Sign SDK 2.26.0, relay connected |
| TODO-003 | TURN credential rotation | Dynamic fetch from /ice-servers |
| TODO-004 | Release APKs all 3 flavors | Signed, smoke tested |
| TODO-005 | Play Store listing | EN + DE texts, screenshots, v1.0.4 uploaded |
| TODO-006 | Activation code premium unlock | Verified working |
| TODO-009 | Samsung background Activity launch | fullScreenIntent + FLAG_KEEP_SCREEN_ON implemented |
| TODO-011 | WireGuard VPN: AGP 8.x | AGP 8.7.3 + Gradle 8.14 |
| TODO-013 | AdMob Free Flavor | Real IDs: ca-app-pub-4336336811005394 |
| TODO-014 | F-Droid flavor | No Google services, builds successfully |
| TODO-015 | Play Store Listing texts | EN + DE in docs/ |
| TODO-016 | Privacy Policy | stealthx.tech/privacy.html (EN + DE + account deletion) |
| TODO-017 | Emergency Broadcast System | 10 templates, WS + FCM delivery |
| TODO-018 | Impressum (EU) | EN/DE/EL, Canvas anti-scraping |
| TODO-019 | Contact Email | kaspartisan@proton.me |
| TODO-020 | Google Search Console | Verification tag inserted, sitemap submitted |
| TODO-021 | SEO optimization | FAQ Schema (7 questions), Product Schema, 31 keywords, hreflang |
| TODO-024 | FCM Push fix | v4.0-fcm-fixed — API key restrictions corrected |
| TODO-025 | One-Click Update | UpdateManager auto-detect Play Store vs sideload |
| TODO-026 | Play Store v1.0.4 | Internal Testing Release 14 live |
| TODO-032 | Railway Backend deploy | /admin/broadcast, /gift, /status/live, /invite all live |
| TODO-035 | Pro/Premium Labels | Locked features show badge + upgrade hint |
| TODO-036 | Emergency Delete | 5-tap instant wipe, no dialog |

## IN PROGRESS

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| TODO-028 | Google Play closed test | HIGH | 12/12 testers collected, start 14-day phase |

## OPEN

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| TODO-007 | Real encryption (replace placeholder crypto) | CRITICAL | GhostNet audio uses placeholder in debug builds |
| TODO-008 | HKDF key derivation (CRYPTO-03) | HIGH | Uses random bytes instead of HKDF-SHA256 |
| TODO-010 | Self-hosted TURN (coturn) | LOW | Config in deployment/coturn_config/, using Metered.ca for now |
| TODO-022 | Google Analytics (GA4) | LOW | Not yet added to stealthx.tech |
| TODO-023 | Bing Webmaster Tools | LOW | Not yet registered |
| TODO-029 | Google Play Service Account | HIGH | For billing purchase verification on Railway |
| TODO-030 | Store Listing DE translation | MEDIUM | German listing in Play Console |
| TODO-031 | GitHub Release APKs | MEDIUM | Free + F-Droid APK public release |
| TODO-033 | Firebase + AdMob linking | MEDIUM | AdMob → Firebase → sxslot |
| TODO-034 | S10 Premium FCM install | MEDIUM | S10 needs v1.0.4 for FCM token |
