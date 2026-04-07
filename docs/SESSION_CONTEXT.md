# StealthX / SecureCall — Handover Document
**Date:** 05.04.2026 (session checkpoint)
**Predecessor session:** 03.04.2026

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
| Device | Serial | Flavor | Number | Client ID |
|---|---|---|---|---|
| S10 | RF8N313QMFL | Premium | +4915231794100 | android-bc2b28d9 |
| S7 | ce10160adc00152604 | Pro | +4915203487046 | android-d7d20df4 |
| Tab S4 | ce12182c68644439037e | Free | +491752536807 | android-ee2e746b |

## Current Version
- versionCode: 31 / versionName: 1.0.13
- AAB: ~/Documents/SecureCall-Release/final/app-free-release-v31.aab
- APK: ~/Documents/SecureCall-Release/final/app-free-release-v31.apk
- GitHub Release: https://github.com/NeaBouli/stealth/releases/tag/v1.0.13
- Play Store: Alpha Track, 15/15 testers

## Git Tags (recent)
| Tag | Description |
|---|---|
| v1.0.13-stable | vC31 — BUG-010/011/013/034/035 fixed, call history long-press, contacts visibility |
| v1.0.12-stable-website-fix | 73 website inconsistencies fixed (prices, legal, versions, devices) |
| v1.0.12 | versionCode 28 — IFR link, upgrade button, collapsible fix, battery protection |
| v1.0.11-stable | 12 bugs fixed — phone norm, auto-reconnect, settings, ads, disconnect btn |

## Backend Endpoints (Live)
- GET /health, /status/live, /status/last-broadcast
- POST /admin/broadcast (X-Admin-Key header) — **NEVER call from AI/dev session**
- POST /admin/gift, GET /admin/gifts, DELETE /admin/gift/:code
- GET /invite/:secureId, POST /invite/accepted
- POST /api/report (Bug Report → GitHub Issues)
- POST /billing/verify-purchase

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
| TODO-049 | Resend Account entsperren (Support Ticket offen) | HIGH | OPEN |
| TODO-053 | Tester-Codes per Email verteilen (30 PREM Codes) | HIGH | OPEN |
| TODO-056 | Play Store Service Account (= TODO-029) | HIGH | MANUAL |
| TODO-067 | Production Release vorbereiten (nach 14 Tage Closed Test) | HIGH | OPEN |
| ~~TODO-069~~ | ~~Alle Claude/AI Spuren aus GitHub entfernen~~ — keine gefunden in Code | — | DONE |
| TODO-050 | Stripe Live-Modus aktivieren | MEDIUM | OPEN |
| TODO-051 | Custom Call ID Feature (Website + Backend + App) | MEDIUM | IN PROGRESS |
| ~~TODO-059~~ | ~~Custom Call ID — In-App Settings UI (Eingabe + Passwort)~~ | — | DONE |
| ~~TODO-060~~ | ~~Custom Call ID — manueller Transfer mit Passwort (Gerätewechsel)~~ | — | DONE |
| TODO-070 | Stripe Live Payment Links auf Website aktualisieren (nach Live-Mode) | MEDIUM | OPEN |
| TODO-071 | Audio Quality manueller Test nach BUG-037/038/039 Fixes (S7 → Tab S4) | MEDIUM | OPEN |
| TODO-072 | Custom Call ID Stripe Checkout auf Website testen (custom-id.html) | MEDIUM | OPEN |
| ~~TODO-073~~ | ~~DE Store Listing~~ — entfällt, Google Play übersetzt automatisch | — | DONE |
| TODO-061 | WalletConnect vollständige Implementierung (aktuell "Coming Soon") | MEDIUM | OPEN |
| ~~TODO-062~~ | ~~TURN Credential Rotation~~ — IceServerFetcher.kt + /ice-servers Endpoint live | — | DONE |
| ~~TODO-061~~ | ~~WalletConnect vollständige Implementierung~~ — Sign SDK + verify flow complete | — | DONE |
| ~~TODO-063~~ | ~~IFR Wallet Token-Anzeige~~ — bereits in Settings UI implementiert | — | DONE |
| TODO-074 | F-Droid APK Build + GitHub Release (assembleFdroidRelease) | MEDIUM | OPEN |
| ~~TODO-076~~ | ~~F-Droid Trial Expired UI — Dialog + Buttons nach 30 Tagen~~ | — | DONE |
| TODO-063 | IFR Wallet Token-Anzeige (Anzahl IFR wird nicht angezeigt) | LOW | OPEN |
| TODO-064 | WireGuard VPN Test-Konfiguration (nicht funktional ohne Config) | LOW | OPEN |
| TODO-065 | FLAG_SECURE Screenshot-Blocking (inkonsistent auf verschiedenen Geräten) | LOW | OPEN |
| TODO-066 | FCM Push Notifications für eingehende Calls im Hintergrund (testen + verifizieren) | LOW | OPEN |
| TODO-068 | GitHub Releases sync mit Play Store versionCode (immer sync halten) | LOW | OPEN |
| TODO-046 | VpnService-based network traffic steering (eSIM routing) | LOW | DEFERRED |
| ~~TODO-033~~ | ~~Firebase + AdMob linking~~ — Code vollständig, Console-Linking manuell | — | DONE |
| TODO-054 | payment-success.html Stripe Links auf Live aktualisieren | LOW | OPEN |
| TODO-075 | F-Droid Repository Einreichung (fdroid.org, nach Production) | LOW | DEFERRED |
| TODO-010 | Self-hosted TURN (coturn) | LOW | DEFERRED |

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

### TODO-049: Resend Account entsperren
- Support Ticket gesendet (Critical)
- Warten auf Resend Response

### TODO-050: Stripe Live-Modus aktivieren
- Test-Modus → Live-Modus wenn Beta abgeschlossen
- Live Secret Key in Railway ersetzen (`sk_test_` → `sk_live_`)
- Live Webhook Endpoint registrieren
- Neue Payment Links für Live-Modus erstellen
- Landing Page Links aktualisieren + TEST MODE Banner entfernen

### TODO-051: Custom Call ID Feature (IN PROGRESS)
- **Website:** wiki/custom-id.html mit Live-Generator + Stripe Checkout (DONE 2026-04-07)
- **Backend:** /custom-id/check, /custom-id/activate, /custom-id/purchase, /custom-id/activate-token (DONE 2026-04-07)
- **App:** Deep Link Handler securecall://custom-id (DONE 2026-04-07)
- **OFFEN:** TODO-059 In-App Settings UI, TODO-060 Passwort-Transfer, TODO-072 E2E Test

### TODO-053: Tester-Codes per Email verteilen
- 30 Premium Codes generiert (`backend/codes/tester_codes_2026.json` — gitignored)
- Warten auf Resend/Brevo → dann manuell versenden

### TODO-056: Play Store Service Account
- **Gehe zu:** play.google.com/console → Setup → API Zugriff
- Google Cloud Projekt verknüpfen, Service Account erstellen
- JSON Key herunterladen → sicher aufbewahren (NIE in Git!)
- **Status:** MANUAL STEP — Kaspartizan muss dies selbst in Play Console durchführen

### TODO-074: F-Droid APK Build + GitHub Release
- `./gradlew assembleFdroidRelease`
- APK: `app-fdroid-release.apk`
- GitHub Release mit fdroid tag erstellen
- Package: `com.securecall.app.fdroid` (kein Google Services, kein AdMob)

### TODO-075: F-Droid Repository Einreichung
- fdroid.org/packages/ submission
- `fdroid/metadata/` Dateien prüfen (siehe `docs/FDROID_SETUP.md`)
- **Status:** DEFERRED — nach Production Release

### TODO-076: F-Droid Trial Expired UI
- Nach 30 Tagen Trial: Dialog anzeigen
- Text: "Trial expired — enter activation code or lock IFR tokens"
- Button → Settings → Activation Code
- Button → Settings → IFR Token (WalletConnect)
- Nur für fdroid Flavor (free/pro/premium haben kein Trial)

### TODO-046: VpnService-basiertes Traffic Steering
- **Status:** DEFERRED — hohe Komplexität, nach Production angehen
- **Workaround:** Feature funktioniert wenn nur ein Netzwerk aktiv (z.B. WiFi aus → nur Mobile)

## Open Bugs
| ID | Description | Severity |
|---|---|---|
| BUG-026 | eSIM routing needs VpnService (Coming Soon in UI) | HIGH |
| BUG-029 | No audio after call connected — VPN+VPN blocks TURN UDP relay | MEDIUM |
| BUG-023 | No diagnostic log export — SecLog CSV export (Pro/Premium) | LOW |

### Erledigte Bugs (letzte 7 Tage)
| ID | Description | Fixed In |
|---|---|---|
| ~~BUG-036~~ | Dialer Tastatur — kein ABC Toggle | 2026-04-06 |
| ~~BUG-037~~ | ICE MAXBUNDLE — nur 4 ICE Pairs | 2026-04-07 |
| ~~BUG-038~~ | Doppeltes audio guard bei Call active | 2026-04-07 |
| ~~BUG-039~~ | Audio Latenz vor config | 2026-04-07 |

**No critical bugs remaining.**

## Release Rules
- Bei jedem Play Store Release: GitHub Release mit aktuellem APK synchron halten
- GitHub Release URL: https://github.com/NeaBouli/stealth/releases/tag/v1.0.13
- Beta Activation Codes (BETA-PRO0-2026 / BETA-PREM-2026) VOR Production deaktivieren
- **NEVER** call admin/broadcast from dev/AI session — only Kaspartizan

## Support Policy
- Aufgrund der Priorisierung auf Anonymität bieten wir keinen persönlichen Support an
- Community-Support: GitHub Issues only
- Bug Reports: stealthx.tech/wiki/bug-report.html
- Terms of Service: stealthx.tech/terms.html

## Next Session Steps
1. TODO-071: Audio Quality Test S7 → Tab S4 (nach BUG-037/038/039 Fixes)
2. TODO-072: Custom Call ID Stripe Checkout E2E testen
3. TODO-059: Custom Call ID In-App Settings UI
4. TODO-053: Tester-Codes versenden (wenn Brevo/Resend läuft)
5. TODO-067: Production Release vorbereiten
6. TODO-050: Stripe Live-Modus nach Beta
7. TODO-069: AI Spuren entfernen vor Production
