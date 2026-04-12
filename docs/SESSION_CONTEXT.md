# StealthX / SecureCall — Handover Document
**Date:** 12.04.2026 (session checkpoint)
**Predecessor session:** 10.04.2026

---

## Project Identity
- **App:** SecureCall (brand: StealthX)
- **Repo:** https://github.com/NeaBouli/stealth (branch: main)
- **Backend:** https://protective-healing-production.up.railway.app
- **Website:** https://stealthx.tech (GitHub Pages)
- **Play Store:** Closed Alpha Testing — com.securecall.app.free (vC38 uploaded)
- **Stripe:** LIVE mode (acct_1QJAg3BtrTFeYCjz) — Payment Links + Webhook aktiv
- **Firebase project:** sxslot
- **GA4:** G-V2L60E8E7R (on all 23 pages)
- **Bing:** D9B5AE056084F8FDB71EC30134F3B009

## Dev Environment
- **Machine:** iMac, /Users/gio/Desktop/stealth
- **PATH:** export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:$PATH:/Users/gio/Library/Android/sdk/platform-tools"
- **Resume:** cd /Users/gio/Desktop/stealth && export PATH=... && cat docs/SESSION_CONTEXT.md

## Device Serials
| Device | Serial | Flavor | Number | Client ID |
|---|---|---|---|---|
| S10 | RF8N313QMFL | Premium | +4915231794100 | android-bc2b28d9 |
| S7 | ce10160adc00152604 | Pro | +4915203487046 | android-d7d20df4 |
| Tab S4 | ce12182c68644439037e | Free | +491752536807 | android-ee2e746b |

## Current Version
- versionCode: 38 / versionName: 1.0.17
- AAB: ~/Documents/SecureCall-Release/final/app-free-release-v38.aab
- APK: ~/Documents/SecureCall-Release/final/app-free-release-v38.apk
- F-Droid APK: ~/Desktop/SecureCall-v1.0.17-fdroid.apk
- GitHub Release: https://github.com/NeaBouli/stealth/releases/tag/v1.0.17-stable
- Play Store: Alpha Track, vC38 uploaded, Pro €3.49/mo + Premium €4.99/mo aktiv

## Git Tags (recent)
| Tag | Description |
|---|---|
| v1.0.17-stable | vC38 — REQUEST_INSTALL_PACKAGES removed, Play Console compliance |
| v1.0.17-fdroid | vC38 — F-Droid build, GitLab Pipeline 2446464856 |
| v1.0.16-stable | vC37 — In-app updater for sideload users |
| v1.0.15-stable | vC36 — WalletConnect catch-Throwable + lock-screen call UI |
| v1.0.14-stable | vC35 — WalletConnect crash fix + lock screen fix |
| v1.0.13-stable | vC31 — BUG-010/011/013/034/035 fixed, call history long-press |

## Backend Endpoints (Live)
- GET /health, /status/live, /status/last-broadcast
- POST /admin/broadcast (X-Admin-Key header) — **NEVER call from AI/dev session**
- POST /admin/gift, GET /admin/gifts, DELETE /admin/gift/:code
- GET /invite/:secureId, POST /invite/accepted
- POST /api/report (Bug Report → GitHub Issues)
- POST /billing/verify-purchase
- POST /stripe/create-checkout, POST /stripe/webhook, POST /stripe/test-email
- GET /custom-id/check, POST /custom-id/activate, POST /custom-id/purchase, POST /custom-id/activate-token, GET /custom-id/resolve
- GET /ice-servers (dynamic TURN credentials, 1h cache)

## Stripe Payment Links (LIVE)
- Premium Lifetime €49: https://buy.stripe.com/test_28E3cu3Sf545baKgeL6g800
- Pro €3.49/mo: https://buy.stripe.com/test_00w4gyewTaop1AabYv6g801
- Premium €4.99/mo: https://buy.stripe.com/test_5kQ3cu88vdABgv44w36g802
- Custom ID $1/$2/$5: Stripe Checkout via /custom-id/purchase

## Admin Key
- Railway: ADMIN_API_KEY env var
- Local: /Users/gio/Desktop/stealth/.env.local

## Activation Codes
- Pro: TEST-PRO1-CODE (dev only, 10 uses)
- Premium: TEST-PREM-CODE (dev only, 10 uses)
- ~~BETA-PRO0-2026~~ DEACTIVATED
- ~~BETA-PREM-2026~~ DEACTIVATED
- 30 x PREM-XXXX-XXXX-XXXX tester reward codes (see backend/codes/tester_codes_2026.json — gitignored)

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

## Recent Fixes (Apr 5 session — v1.0.13)
- **BUG-010 FIX:** FCM CALL_INVITE starts IncomingCallActivity directly without WS. WakeLock + full-screen notification + WS reconnect in background. Duplicate suppression via fcmPendingSessionId.
- **BUG-011 FIX:** ICE reconnect grace period — 15s delay on server CALL_END(peer_disconnected) when WebRTC is still alive. Survives WiFi toggle during call.
- **BUG-013 FIX:** Contact name sync — PhoneBookResolver checks secureId field + phone book via stored phone. CallsFragment enrichment uses resolveCallerName() and persists results. CallRecord stores phoneNumber for future re-resolution.
- **BUG-034 FIX:** 0s duration calls after WS reconnect — isRegistered gate blocks outgoing calls for 1.5s after REGISTER until server processes it. Queued calls flushed automatically.
- **BUG-035 FIX:** DNS resolution failures after network switch use 30s reconnect delay instead of fast 2s retry.
- **NEW:** Call history long-press menu: Save as Contact / Edit Contact / Block / Unblock / Delete
- **FIX:** App-saved contacts now always visible in Contacts tab (were filtered out by isContactRegistered)
- **FIX:** Save Contact dialog: empty input with "Name, Alias" hint instead of prefilled number

## Recent Fixes (Apr 3 session)
- 73 website inconsistencies fixed across 19 HTML files (commit 3cbb1df)

## Recent Fixes (Mar 30 session — BUG-028 through BUG-032)
- BUG-028: OkHttp ping timeout fix (5s→15s for VPN connections)
- BUG-029: HeartbeatClient anti-flap recovery mode
- BUG-030: Ring timeout handler (60s auto-decline)
- BUG-031: clearSession() after decline/dismiss prevents stale session
- BUG-032: BUSY loop fix — clearSession() after decline

## Open TODOs
| ID | Task | Priority | Status |
|---|---|---|---|
| TODO-049 | Resend API Key in Railway setzen (Account entsperrt?) | HIGH | OPEN |
| TODO-053 | Tester-Codes per Email verteilen (30 PREM Codes) | HIGH | OPEN |
| TODO-056 | Play Store Service Account (= TODO-029) | HIGH | MANUAL |
| TODO-067 | Production Release vorbereiten (nach 14 Tage Closed Test) | HIGH | OPEN |
| TODO-097 | WalletConnect SDK PushClient Bug fixen (isInitialized=false) | HIGH | OPEN |
| TODO-098 | Dependabot: 7 Vulnerabilities (5 high) — npm audit backend | HIGH | OPEN |
| TODO-099 | Resend API Key in Railway env setzen | MEDIUM | OPEN |
| TODO-100 | Audio Test S10→Samsung A manuell (anderes Endgerät) | MEDIUM | OPEN |
| TODO-101 | Stripe "myproduct" Test-Produkte archivieren (Dashboard) | MEDIUM | MANUAL |
| TODO-102 | Custom ID Preise USD→EUR im Stripe Dashboard umstellen | MEDIUM | MANUAL |
| TODO-054 | payment-success.html Stripe Links auf Live aktualisieren | MEDIUM | OPEN |
| TODO-064 | WireGuard VPN Test-Konfiguration (nicht funktional ohne Config) | LOW | OPEN |
| TODO-046 | VpnService-based network traffic steering (eSIM routing) | LOW | DEFERRED |
| TODO-075 | F-Droid Repository Einreichung (fdroid.org, nach Production) | LOW | DEFERRED |
| TODO-010 | Self-hosted TURN (coturn) | LOW | DEFERRED |

### Erledigte TODOs (Session 2026-04-10 bis 2026-04-12)
| ID | Task | Erledigt |
|---|---|---|
| ~~TODO-050~~ | Stripe Live-Modus — LIVE account acct_1QJAg3, Payment Links, Webhook, Email Flow | 2026-04-11 |
| ~~TODO-051~~ | Custom Call ID Feature — alle Endpoints produktiv, Stripe Checkout live | 2026-04-11 |
| ~~TODO-070~~ | Stripe Live Payment Links auf Website | 2026-04-11 |
| ~~TODO-071~~ | Audio Quality Test — SecLog analysiert, BUG-037/038/039 gefixt | 2026-04-10 |
| ~~TODO-072~~ | Custom Call ID Stripe Checkout E2E getestet | 2026-04-11 |
| ~~TODO-074~~ | F-Droid APK Build vC38 + GitHub Release + GitLab Pipeline | 2026-04-11 |
| ~~TODO-077~~ | Google Play Zahlungsprofil bestätigt | 2026-04-11 |
| ~~TODO-065~~ | FLAG_SECURE — Premium=mandatory, Pro=toggle, Free=off — getestet + funktional | 2026-04-12 |
| ~~TODO-066~~ | FCM Push Notifications getestet + verifiziert | 2026-04-10 |
| ~~TODO-068~~ | GitHub Releases sync mit Play Store vC38 | 2026-04-11 |
| ~~TODO-063~~ | IFR Wallet Token-Anzeige funktioniert (200M IFR → PREMIUM active) | 2026-04-12 |

### Erledigte TODOs (letzte 7 Tage)
| ID | Task | Erledigt |
|---|---|---|
| ~~TODO-048~~ | Brevo als Email Backup | 2026-04-06 |
| ~~TODO-052~~ | Dialer Tastatur — ABC Toggle | 2026-04-06 |
| ~~TODO-055~~ | AGB updaten — Custom Call ID | 2026-04-06 |
| ~~TODO-058~~ | Google Search Coverage: Sitemap, canonical, noindex, JSON-LD | 2026-04-07 |
| ~~TODO-057~~ | DE Store Listing (marketing/play_store_de.txt) | 2026-04-05 |
| ~~TODO-073~~ | DE Store Listing in Play Console — entfällt, Google übersetzt automatisch | 2026-04-07 |
| ~~TODO-069~~ | AI Spuren entfernen — CLAUDE.md untracked, keine AI-Refs in Code | 2026-04-07 |
| ~~TODO-047~~ | Deactivate beta codes before production | 2026-04-05 |
| ~~TODO-030~~ | Store Listing DE | 2026-04-03 |

### TODO-097: WalletConnect SDK PushClient Bug
- `isInitialized=false` weil `com.walletconnect.android.push.client.PushClient` nicht aufgelöst wird
- `catch(Throwable)` in `WalletConnectManager.init()` fängt den Error → kein Crash, aber Connect Wallet blockiert
- Root Cause: `android-core:1.26.0` referenziert PushClient, der nicht im Build enthalten ist
- Fix-Optionen: (A) Exclude `push-client` transitive dep, (B) Stub-Klasse, (C) SDK-Version downgrade

### TODO-098: npm audit — 7 Vulnerabilities
- `cd backend/signaling && npm audit` → 5 high, 2 moderate
- Dependabot PRs prüfen oder manuell updaten

### TODO-053: Tester-Codes per Email
- 30 Premium Codes generiert (`backend/codes/tester_codes_2026.json` — gitignored)
- Brevo ist aktiv, Resend API Key noch in Railway setzen

### TODO-046: VpnService-basiertes Traffic Steering
- **Status:** DEFERRED — hohe Komplexität, nach Production angehen

## Open Bugs
| ID | Description | Severity |
|---|---|---|
| BUG-026 | eSIM routing needs VpnService (Coming Soon in UI) | LOW |
| BUG-029 | No audio after call connected — VPN+VPN blocks TURN UDP relay | MEDIUM |
| BUG-023 | No diagnostic log export — SecLog CSV export (Pro/Premium) | LOW |

### Erledigte Bugs (Session 2026-04-12)
| ID | Description | Fixed In |
|---|---|---|
| ~~BUG-041~~ | Dialer Suggestion Tap no-op (trailing lambda → onLongClick statt onCallClick) | 2026-04-12 |
| ~~BUG-042~~ | Kontakte-Suche ignoriert Nummernformat (fehlende Normalisierung) | 2026-04-12 |
| ~~BUG-043~~ | Kontakt-Zeile nicht klickbar (nur Call-Icon hatte onClickListener) | 2026-04-12 |
| ~~BUG-044~~ | Background Service Toggle: Samsung Notification bleibt nach stopForeground | 2026-04-12 |
| ~~BUG-045~~ | foregroundStarted nicht zurückgesetzt → Re-enable skippt startForeground() | 2026-04-12 |
| ~~BUG-046~~ | Broadcast Template 9+10 hardcoded "Google Play" Text für alle Install-Sources | 2026-04-12 |

**No critical bugs remaining.**

## Release Rules
- Bei jedem Play Store Release: GitHub Release mit aktuellem APK synchron halten
- GitHub Release URL: https://github.com/NeaBouli/stealth/releases/tag/v1.0.17-stable
- Beta Activation Codes (BETA-PRO0-2026 / BETA-PREM-2026) bereits deaktiviert
- **NEVER** call admin/broadcast from dev/AI session — only Kaspartizan

## Support Policy
- Aufgrund der Priorisierung auf Anonymität bieten wir keinen persönlichen Support an
- Community-Support: GitHub Issues only
- Bug Reports: stealthx.tech/wiki/bug-report.html
- Terms of Service: stealthx.tech/terms.html

## Next Session Steps
1. TODO-097: WalletConnect SDK PushClient Bug fixen (Connect Wallet blockiert)
2. TODO-098: npm audit — 7 Vulnerabilities im Backend
3. TODO-067: Production Release vorbereiten (Closed Test seit 10.04)
4. TODO-053: Tester-Codes versenden (Brevo aktiv)
5. TODO-100: Audio Test S10→Samsung A (anderes Endgerät)
6. TODO-101/102: Stripe Dashboard aufräumen (Test-Produkte, USD→EUR)
