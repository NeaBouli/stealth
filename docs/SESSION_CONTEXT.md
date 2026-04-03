# StealthX / SecureCall — Handover Document
**Date:** 03.04.2026 (session checkpoint)
**Predecessor session:** 30.03.2026

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
- versionCode: 30 / versionName: 1.0.12
- AAB: ~/Documents/SecureCall-Release/final/app-free-release-v28.aab
- APK: ~/Documents/SecureCall-Release/final/app-free-release-v28.apk
- GitHub Release: https://github.com/NeaBouli/stealth/releases/tag/v1.0.12
- Play Store: Alpha Track, 15/15 testers

## Git Tags (recent)
| Tag | Description |
|---|---|
| v1.0.12-stable-website-fix | 73 website inconsistencies fixed (prices, legal, versions, devices) |
| v1.0.12 | versionCode 28 — IFR link, upgrade button, collapsible fix, battery protection |
| v1.0.11-stable | 12 bugs fixed — phone norm, auto-reconnect, settings, ads, disconnect btn |
| v1.0.9-alpha | Alpha track versionCode 20 |
| v1.0.8 | EBS template 10, contact filter, verified badge |
| v1.0.7 | Invite dialog, verified badge, contacts filter |

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

## Recent Fixes (Apr 3 session)
- 73 website inconsistencies fixed across 19 HTML files (commit 3cbb1df)
  - Legal: Germany→Greece, BDSG removed (matches Impressum)
  - Prices: USD→EUR standardized (€3.49/€4.99 monthly, €15/€25 lifetime)
  - Android: API 29→API 24 (minSdk 24 = Android 7.0)
  - Devices: S7=Pro, Tab S4=Free in security-audit (was swapped)
  - Versions: versionCode 28→30, removed v0.2-beta references
  - Heartbeat: 8s→15s, "open source"→"source available"
  - Railway "free tier" removed, WalletConnect "coming soon"→available
  - Beta codes hidden from public page, F-Droid "available"→"planned"
  - Sitemap: added invite.html + announcements.html
  - Dates: "February 2026"→"April 2026" on 12 pages

## Recent Fixes (Mar 30 session — BUG-028 through BUG-032)
- BUG-028: OkHttp ping timeout fix (5s→15s for VPN connections)
- BUG-029: HeartbeatClient anti-flap recovery mode
- BUG-030: Ring timeout handler (60s auto-decline)
- BUG-031: clearSession() after decline/dismiss prevents stale session
- BUG-032: BUSY loop fix — clearSession() after decline
- Long-press contact: Verify / Block / Delete actions
- User Manual page (wiki/user-manual.html) linked from Settings

## Previous Fixes (Mar 28 — Bug Sprint)
- BUG-025 FIX: Phone normalization — PhoneUtils.normalize() strips all formatting, 00→+ conversion
- BUG-018 FIX: Report a Bug → stealthx.tech/wiki/bug-report.html
- BUG-016 FIX: "Anonymous Network" → "Network"
- BUG-017 FIX: New Call FAB hidden in Calls/Contacts tabs
- BUG-012 FIX: Ads paused during active call (pauseForCall/resumeAfterCall)
- BUG-021/020 FIX: Emergency Delete first, IFR Token last in Settings
- BUG-014 FIX: CollapsiblePreferenceCategory — tap to expand AND collapse with arrow
- BUG-019 FIX: Check for Updates detects install source (sideload → GitHub Releases)
- BUG-015 FIX: Disconnect/Reconnect button in toolbar (green/gray/yellow tint)
- BUG-022 FIX: eSIM status refreshes on every Settings visit
- BUG-009/024 FIX: Auto-reconnect on network change via ConnectivityManager NetworkCallback
- Dark mode as default for new installs
- Reconnect fix: forceReconnect() clears isClosed, connects immediately
- SecLog diagnostic logging (Pro/Premium) with CSV export
- eSIM settings gated by device capability + Pro/Premium tier
- Preferred Network binding always applied on change
- IFR Token "Learn about IFR" link to ifrunit.tech
- Matrix Integration wiki page + roadmap + landing page Coming Soon
- Landing page: 3 new feature cards + 9 devices tested

## Previous Fixes (Mar 25-28)
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
| TODO-046 | VpnService-based network traffic steering (eSIM routing) | HIGH |
| TODO-047 | Deactivate beta codes before production | HIGH |
| TODO-030 | Store Listing DE in Play Console | MEDIUM |
| TODO-033 | Firebase + AdMob linking | MEDIUM |
| TODO-044 | Kontakt-Sync mit Telefonbuch (BUG-013) | MEDIUM |
| TODO-010 | Self-hosted TURN (coturn) | LOW |

## Open Bugs
| ID | Description | Severity |
|---|---|---|
| BUG-010 | FCM not waking app reliably (incoming calls when closed) | CRITICAL |
| BUG-011 | WebRTC P2P unstable (call drops after connecting) | CRITICAL |
| BUG-013 | Saved contact shows phone number not phonebook name | MEDIUM |
| BUG-026 | eSIM routing needs VpnService (Coming Soon in UI) | HIGH |

## Release Rules
- Bei jedem Play Store Release: GitHub Release mit aktuellem APK synchron halten
- GitHub Release URL: https://github.com/NeaBouli/stealth/releases/tag/v1.0.12
- Beta Activation Codes (BETA-PRO0-2026 / BETA-PREM-2026) VOR Production deaktivieren

## Next Session Steps
1. Wait for tester feedback on vC30 build
2. Fix BUG-010 (FCM wake) + BUG-011 (WebRTC stability) — CRITICAL
3. Fix BUG-013 (contact sync — phonebook name display)
4. Google Play Service Account for billing verification (TODO-029)
5. Deactivate beta codes before production (TODO-047)
6. Production release after testing phase
