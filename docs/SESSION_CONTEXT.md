# StealthX / SecureCall — Handover Document
**Date:** 28.03.2026 (final checkpoint)
**Predecessor session:** Available for questions in same chat

---

## Project Identity
- **App:** SecureCall (brand: StealthX)
- **Repo:** https://github.com/NeaBouli/stealth (branch: main)
- **Backend:** https://protective-healing-production.up.railway.app
- **Website:** https://stealthx.tech (GitHub Pages)
- **Play Store:** Closed Alpha Testing — com.securecall.app.free
- **Firebase project:** sxslot
- **GA4:** G-V2L60E8E7R (on all 23 pages)
- **Bing:** D9B5AE056084F8FDB71EC30134F3B009

## Dev Environment
- **Machine:** iMac, /Users/gio/Desktop/stealth
- **PATH:** export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:$PATH:/Users/gio/Library/Android/sdk/platform-tools"
- **Resume:** cd /Users/gio/Desktop/stealth && export PATH=... && cat docs/SESSION_CONTEXT.md

## Device Serials
| Device | Serial | Flavor | Number |
|---|---|---|---|
| S10 | RF8N313QMFL | Premium | +4915231794100 |
| S7 | ce10160adc00152604 | Pro | +4915203487046 |
| Tab S4 | ce12182c68644439037e | Free | +491752536807 |

## Current Version
- versionCode: 21 / versionName: 1.0.10
- AAB: ~/Documents/SecureCall-Release/final/app-free-release-v21.aab
- Play Store: Alpha Track, Release 21 live

## Git Tags (recent)
| Tag | Description |
|---|---|
| v1.0.11-stable | Latest checkpoint — deep link, QR invite, contacts sort, donation fix |
| v1.0.9-alpha | Alpha track versionCode 20 |
| v1.0.8 | EBS template 10, contact filter, verified badge |
| v1.0.7 | Invite dialog, verified badge, contacts filter |
| v4.0-fcm-fixed | FCM push notifications working |

## Backend Endpoints (Live)
- GET /health, /status/live, /status/last-broadcast
- POST /admin/broadcast (X-Admin-Key header)
- POST /admin/gift, GET /admin/gifts, DELETE /admin/gift/:code
- GET /invite/:secureId, POST /invite/accepted
- POST /api/report (Bug Report → GitHub Issues)
- POST /billing/verify-purchase

## Admin Key
- Railway: ADMIN_API_KEY env var
- Local: /Users/gio/Desktop/stealth/.env.local

## Activation Codes
- Pro: TEST-PRO1-CODE, BETA-PRO0-2026
- Premium: TEST-PREM-CODE, BETA-PREM-2026

## EBS Templates (1-10)
1 CRITICAL, 2 Security, 3 Update Required, 4 Maintenance, 5 Stealth,
6 Emergency, 7 Network, 8 All Clear, 9 Update Available, 10 Beta Update

## AdMob (Real IDs)
- App: ca-app-pub-4336336811005394~8119860953
- Banner: ca-app-pub-4336336811005394/5437857296
- Interstitial: ca-app-pub-4336336811005394/4739986746

## Beta Testers (15)
| # | Tester | Status |
|---|---|---|
| 1-11 | (see TESTER_BUGS.md) | Active |
| 12 | u..........m@gmail.com | Active |
| 13 | r..........1@gmail.com | Active |
| 14 | b..........r@googlemail.com | Active |
| 15 | c...........4@gmail.com | Active |

## Recent Fixes (this session, Mar 25-28)
- FCM Token persistence (survives Railway redeploy)
- FLAG_SECURE tier-based (Free=off, Pro=toggle, Premium=always)
- Emergency Delete: 5-tap instant wipe
- Pro/Premium feature labels in Settings
- AdMob SDK 23.0.0 → 23.6.0
- Emergency Broadcast notification from background
- Samsung lock screen: FCM → IncomingCallActivity
- Invite system: deep link + QR code + sender name
- securecall://add-contact custom scheme
- invite.html: Open in App + APK download + Play Store
- Contacts: online first, offline below, search shows all
- Donation section: always collapsed on start
- Deep link: Add Contact dialog with name + Call Now
- Bug Report form: wiki/bug-report.html → GitHub Issues
- GA4 + Bing verification on all pages
- Impressum: contact form + 24100 Kalamata address
- Wiki: canonical sidebar on all 15 pages

## Open TODOs
| ID | Task | Priority |
|---|---|---|
| TODO-029 | Google Play Service Account (billing verification) | HIGH |
| TODO-033 | Firebase + AdMob linking | MEDIUM |
| TODO-010 | Self-hosted TURN (coturn) | LOW |

## Next Session Steps
1. Monitor 14-day closed testing phase
2. Process tester bug reports (gh issue list --label user-report)
3. Build v1.0.11 if bugs found
4. Prepare for Production release after testing phase
5. Google Play Service Account for billing verification
