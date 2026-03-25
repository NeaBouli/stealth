# StealthX / SecureCall — Handover Document
**Date:** 25.03.2026 (updated)
**Predecessor session:** Available for questions in same chat

---

## Project Identity
- **App:** SecureCall (brand: StealthX)
- **Repo:** https://github.com/NeaBouli/stealth (branch: main)
- **Backend:** https://protective-healing-production.up.railway.app
- **Website:** https://stealthx.tech (GitHub Pages)
- **Play Store:** Internal Testing — com.securecall.app.free
- **Firebase project:** sxslot

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
- versionCode: 16 / versionName: 1.0.6
- AAB: ~/Documents/SecureCall-Release/final/app-free-release-v16.aab
- Play Store: Internal Testing Track, Release 16 live

## Git Tags
| Tag | Description |
|---|---|
| v1.0.4 | Latest release — FCM + update system — wiki redesigned, favicon, all fixes |
| v3.8-wiki-redesign | Wiki card grid, stats, badges |
| v3.7-language-fix | Wiki EN, /status/live endpoint |
| v3.6-polish | EBS templates 9+10, EBS redesign, gift fix |
| v3.5-invite-system | Deep Link Invite System |
| v3.1-play-store-live | Google Play Internal Testing live |

## Backend Endpoints (Live)
- GET /health
- GET /status/live → {server, uptime, connectedClients, heartbeats}
- GET /status/last-broadcast → last EBS template
- GET /version/latest → {version, versionCode, required}
- POST /admin/broadcast → X-Admin-Key header (key in Railway Variables + .env.local)
- POST /admin/gift → generate gift code
- GET /invite/:secureId

## Admin Key Location
- Railway Dashboard → protective-healing-production → Variables → ADMIN_API_KEY
- Lokal: /Users/gio/Desktop/stealth/.env.local

## Activation Codes
- Pro: TEST-PRO1-CODE, BETA-PRO0-2026
- Premium: TEST-PREM-CODE, BETA-PREM-2026
- Lifetime Premium: EUR49 via Google Play

## EBS Templates
1. 🔴 CRITICAL, 2. 🟠 Security Alert, 3. 🟡 Update Required
4. 🔵 Maintenance, 5. ⚫ Stealth Protocol, 6. 📻 Emergency
7. ⚠️ Network Warning, 8. 🟢 All Clear (default)
9. 🔄 Update Available (production), 10. 🧪 Beta Update (testers)

Send: curl -X POST .../admin/broadcast -H "X-Admin-Key: KEY" -d '{"template_id": 10}'

## AdMob
- App ID: ca-app-pub-4336336811005394~8119860953
- Banner: ca-app-pub-4336336811005394/5437857296
- Interstitial: ca-app-pub-4336336811005394/4739986746

## WalletConnect
- Project ID: 32f56abaa4b1d7f59fb1571c0c0a551f
- IFR Lock Contract: 0x769928aBDfc949D0718d8766a1C2d7dBb63954Eb
- Pro: >=1000 IFR, Premium: >=5000 IFR

## Beta Testers (12/12)
| # | Tester | Status |
|---|---|---|
| 1 | B................1@gmail.com | Active |
| 2 | H................1@gmail.com | Active |
| 3 | a..............s@gmail.com | Active |
| 4 | d................3@gmail.com | Active |
| 5 | e................2@gmail.com | Active |
| 6 | g..............i@gmail.com | Active |
| 7 | l............a@gmail.com | Active |
| 8 | n......n@gmail.com | Active |
| 9 | p............a@gmail.com | Active |
| 10 | r......s@gmail.com | Active |
| 11 | z......r@gmail.com | Active |
| 12 | 12th tester | Active |

## Bug Tracker
| ID | Description | Status |
|---|---|---|
| TB-001 | Online status dots | FIXED |
| TB-002 | Upgrade screen prices wrong | FIXED |
| TB-003 | Peer not found message | FIXED |
| TB-004 | Phone number not saved | FIXED |
| TB-005 | Navbar overlap Samsung S21+ | FIXED |
| TB-006 | Contact detection | FIXED |
| TB-007 | SMS invite link wrong | FIXED |
| TB-008 | Support Development not collapsible | FIXED |
| TB-009 | Ad banner not collapsible | WONTFIX |
| TB-010 | No invite system | FIXED |
| TB-011 | WalletConnect cancel hangs — 30s timeout fix | FIXED |
| TB-012 | Donation accordion won't close | FIXED |
| TB-013 | Ad banner invisible | FIXED |

## Open TODOs (Priority)
| ID | Task | Priority |
|---|---|---|
| TODO-007 | Real encryption (replace placeholder crypto) | CRITICAL |
| TODO-008 | HKDF key derivation | HIGH |
| TODO-028 | Play Store 14-day closed test (12/12 testers ready) | HIGH |
| TODO-029 | Google Play Service Account for Billing | HIGH |
| TODO-030 | Store Listing DE translation | MEDIUM |
| TODO-031 | GitHub Release APKs (Free + F-Droid) | MEDIUM |
| TODO-033 | Firebase + AdMob link | MEDIUM |
| TODO-034 | S10 FCM install | DONE |

## FCM Status (CRITICAL — in progress)
- FIS_AUTH_ERROR was caused by API key restrictions blocking app packages
- SHA-1 added to all 3 apps in Firebase Console: 35:82:FA:AF:F6:A9:BB:8B:01:9A:0F:1F:B3:6D:18:E0:8D:E3:1F:6E
- RESOLVED: API key restrictions removed in Google Cloud Console from Firebase Console (after SHA-1 addition), rebuild and test
- google-services.json at: client_android/app/google-services.json (not versioned)

## Next Session — First Steps
1. Download NEW google-services.json from Firebase Console (must contain SHA-1 fingerprints)
2. Run FCM rebuild:
   cd client_android
   ./gradlew assembleFreeDebug assembleProDebug assemblePremiumDebug
   adb -s RF8N313QMFL install -r .../app-premium-debug.apk
   adb -s ce10160adc00152604 install -r .../app-pro-debug.apk
   adb -s ce12182c68644439037e install -r .../app-free-debug.apk

3. Test FCM: curl broadcast template 10 → check fcm_targets > 0

4. Test all 3 devices manually (analog — off USB):
   - WalletConnect cancel → should reset after 30s
   - Donation accordion → should open AND close
   - Ad banner → should be visible in Free

5. If all pass → build AAB v14 + upload to Play Store

## Keystore
- Path: ~/Desktop/stealth/securecall-release-key.jks
- Upload key SHA-256: 1E:0A:8E:B4:19:54:0D:E8:54:5F:77:0E:78:DC:DB:93:AB:1B:A8:A0:71:3D:A8:99:92:22:FC:88:C3:FD:B2:1D

## Contact
- Email: kaspartisan@proton.me
- X: @secureslot
- Play Store: georgios.mariotti@gmail.com
- Company: Vendetta Labs, Greece

## Predecessor Agent Note
The predecessor Claude session is still available for questions.
If anything is unclear, ask the user to paste the question back
into the previous chat window for clarification.
