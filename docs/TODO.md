# SecureCall TODO Tracker
**Last updated:** 2026-03-28 | **Version:** v1.0.10 (code 21)

## DONE (35)

| ID | Task | Notes |
|----|------|-------|
| TODO-001 | FCM push notifications | Persistent tokens, survives redeploy |
| TODO-002 | WalletConnect v2 | Sign SDK 2.26.0, relay connected |
| TODO-003 | TURN credential rotation | Dynamic fetch from /ice-servers |
| TODO-004 | Release APKs all flavors | Signed, tested |
| TODO-005 | Play Store listing | Alpha Track live, 15 testers |
| TODO-006 | Activation code unlock | Verified working |
| TODO-007 | Real encryption (HKDF) | HkdfSha256.kt + GhostNetCryptoManager |
| TODO-008 | HKDF key derivation | Implemented in ghostnet/crypto/ |
| TODO-009 | Samsung lock screen | fullScreenIntent → IncomingCallActivity |
| TODO-011 | WireGuard VPN AGP 8.x | AGP 8.7.3 + Gradle 8.14 |
| TODO-013 | AdMob Free Flavor | Real IDs, SDK 23.6.0 |
| TODO-014 | F-Droid flavor | No Google services |
| TODO-015 | Play Store Listing texts | EN + DE |
| TODO-016 | Privacy Policy | stealthx.tech/privacy.html |
| TODO-017 | Emergency Broadcast System | 11 templates, WS + FCM, notification from background |
| TODO-018 | Impressum (EU) | EN/DE/EL, contact form, 24100 Kalamata |
| TODO-019 | Contact Email | kaspartisan@proton.me |
| TODO-020 | Google Search Console | Verification tag + sitemap |
| TODO-021 | SEO optimization | FAQ/Product Schema, 31 keywords, hreflang |
| TODO-022 | Google Analytics GA4 | G-V2L60E8E7R on all 23 pages |
| TODO-023 | Bing Webmaster Tools | D9B5AE056084F8FDB71EC30134F3B009 |
| TODO-024 | FCM Push fix | API key + persistent tokens |
| TODO-025 | One-Click Update | UpdateManager, always Play Store |
| TODO-026 | Play Store releases | Multiple releases uploaded |
| TODO-028 | Google Play closed test | Alpha Track live 27.03.2026, 15/15 testers |
| TODO-031 | GitHub Release APKs | v1.0.6 published |
| TODO-032 | Railway Backend deploy | All endpoints live |
| TODO-034 | S10 FCM install | Clean install, token registered |
| TODO-035 | Pro/Premium Labels | Badges + upgrade hint |
| TODO-036 | Emergency Delete | 5-tap instant wipe |
| TODO-037 | FCM Token Persistence | data/fcm_tokens.json |
| TODO-038 | FLAG_SECURE tier logic | Free=off, Pro=toggle, Premium=always |
| TODO-039 | EBS Notification background | System notification + Activity |
| TODO-040 | Deep Link Invite | securecall://add-contact + QR code |
| TODO-041 | Bug Report Form | wiki/bug-report.html → GitHub Issues |

## OPEN (9)

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| TODO-029 | Google Play Service Account | HIGH | For billing purchase verification (manual) |
| TODO-042 | Auto-Reconnect bei Netzwerkwechsel | HIGH | ConnectivityManager NetworkCallback — BUG-009/024 |
| TODO-043 | Ads waehrend aktiver Anrufe unterdruecken | HIGH | AdMob pause in CallActivity — BUG-012 |
| TODO-030 | Store Listing DE in Play Console | MEDIUM | marketing/play_store/de/store_listing.md ready |
| TODO-033 | Firebase + AdMob linking | MEDIUM | AdMob Console → Firebase (manual) |
| TODO-044 | Kontakt-Sync mit Telefonbuch | MEDIUM | Namen nach Anruf aus Phonebook uebernehmen — BUG-013 |
| TODO-041a | P2P Kontaktaustausch Visitenkarte | MEDIUM | QR Code + NFC + Bluetooth Proximity |
| TODO-010 | Self-hosted TURN (coturn) | LOW | Using Metered.ca, optional |
| TODO-045 | SecLog Diagnose-Export CSV (Pro/Premium) | LOW | BUG-023 |
