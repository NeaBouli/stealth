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
| ID | Task | Priority |
|---|---|---|
| TODO-048 | Brevo als Email Backup (Resend gesperrt) | HIGH |
| TODO-049 | Resend Account entsperren (Support Ticket offen) | HIGH |
| TODO-053 | Tester-Codes per Email verteilen (30 PREM Codes) | HIGH |
| TODO-056 | Play Store Service Account (= TODO-029) | HIGH |
| TODO-050 | Stripe Live-Modus aktivieren | MEDIUM |
| TODO-051 | Custom Call ID Feature (Website + Backend + App) | MEDIUM |
| TODO-052 | Dialer Tastatur — ABC Toggle für Custom IDs | MEDIUM |
| TODO-055 | AGB updaten — Custom Call ID Bedingungen | MEDIUM |
| TODO-057 | DE Store Listing in Play Console eintragen | MEDIUM |
| TODO-046 | VpnService-based network traffic steering (eSIM routing) | MEDIUM |
| TODO-033 | Firebase + AdMob linking | MEDIUM |
| TODO-054 | payment-success.html Stripe Links auf Live aktualisieren | LOW |
| TODO-010 | Self-hosted TURN (coturn) | LOW |
| ~~TODO-047~~ | ~~Deactivate beta codes before production~~ | DONE |
| ~~TODO-030~~ | ~~Store Listing DE~~ — marketing/play_store_de.txt | DONE |

### TODO-048: Brevo als Email Backup
- Resend Account gesperrt (Support Ticket offen)
- **Brevo.com** als Alternative: kostenlos, sofort verfügbar
- `BREVO_API_KEY` in Railway setzen
- `email_handler.js`: Fallback auf Brevo wenn Resend fails

### TODO-049: Resend Account entsperren
- Support Ticket gesendet (Critical)
- Warten auf Resend Response

### TODO-050: Stripe Live-Modus aktivieren
- Test-Modus → Live-Modus wenn Beta abgeschlossen
- Live Secret Key in Railway ersetzen (`sk_test_` → `sk_live_`)
- Live Webhook Endpoint registrieren
- Neue Payment Links für Live-Modus erstellen
- Landing Page Links aktualisieren + TEST MODE Banner entfernen

### TODO-051: Custom Call ID Feature
- **Website:** stealthx.tech/custom-id Generator
- **Preise:** $1 (10+ Zeichen), $2 (5-9 Zeichen), $5 (3-4 Zeichen)
- 1-2 Zeichen: nur Dev Team reserviert
- **Passwortschutz:** ID + Passwort bei Aktivierung
- **Gerätemigration:** ID + Passwort auf neuem Gerät eingeben → altes Gerät automatisch deaktiviert
- **Backend:** ID-Verfügbarkeit prüfen, reservieren, speichern
- **App:** Settings → Custom Call ID eingeben + Passwort
- Nur für Premium User
- Einmal gekauft = dauerhaft (an Passwort gebunden)

### TODO-052: Dialer Tastatur — ABC Toggle
- Dialer zeigt nur Ziffernblock, kein Toggle zwischen 123 ↔ ABC
- Nötig für alphanumerische SecureCall-IDs (z.B. "marco", "trump")
- Fix: Toggle-Button im Dialer hinzufügen

### TODO-053: Tester-Codes per Email verteilen
- 30 Premium Codes generiert (`backend/codes/tester_codes_2026.json` — gitignored)
- Warten auf Resend/Brevo → dann manuell versenden

### TODO-055: AGB updaten — Custom Call ID
- Passwort vergessen = ID verloren (kein Support)
- ID ist an Passwort gebunden, nicht an Person
- Keine Rückerstattung bei Passwortverlust

### TODO-056: Play Store Service Account
- **Gehe zu:** play.google.com/console → Setup → API Zugriff
- Google Cloud Projekt verknüpfen, Service Account erstellen
- JSON Key herunterladen → sicher aufbewahren (NIE in Git!)
- Benötigt für: automatische AAB-Uploads, Billing-Verifikation
- **Status:** MANUAL STEP — Kaspartizan muss dies selbst in Play Console durchführen

### TODO-046: VpnService-basiertes Traffic Steering
- **Benötigt für:** eSIM Call Routing, Preferred Network Bindung
- **Problem:** OkHttp connection pooling umgeht `bindProcessToNetwork()`
- **Lösung:** Eigener VpnService der Traffic auf OS-Ebene auf das richtige Interface leitet
- **Status:** OPEN — hohe Komplexität, nach Production angehen
- **Workaround:** Feature funktioniert wenn nur ein Netzwerk aktiv (z.B. WiFi aus → nur Mobile)

## Open Bugs
| ID | Description | Severity |
|---|---|---|
| BUG-026 | eSIM routing needs VpnService (Coming Soon in UI) | HIGH |
| BUG-029 | No audio after call connected — VPN+VPN blocks TURN UDP relay | MEDIUM |
| BUG-036 | Dialer Tastatur — kein ABC Toggle für alphanumerische IDs | MEDIUM |
| BUG-023 | No diagnostic log export — SecLog CSV export (Pro/Premium) | LOW |

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
1. Brevo Email Backup einrichten (TODO-048) — Tester-Codes versenden
2. Custom Call ID Feature planen (TODO-051 + TODO-052)
3. Upload AAB v1.0.13 to Play Console Alpha Track
4. Play Store Service Account (TODO-056) — Kaspartizan manuell
5. Stripe Live-Modus nach Beta (TODO-050)
6. Production release nach Tester-Feedback
