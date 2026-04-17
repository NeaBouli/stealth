# StealthX Platform — TODO & Memo

## KRITISCH — Play Store (v1.0.22)
- [x] v1.0.22 AAB gebaut + Closed Alpha deployed (beta-test23, vC43)
- [x] F-Droid Pipeline gruen (MR !36495)
- [x] GitHub Release v1.0.22 mit 4 Assets
- [ ] Alpha-Feedback (beta-test23) auswerten
- [ ] Pre-Launch-Report Google pruefen
      (Play Console > Ueberpruefen > Pre-Launch-Bericht)
- [ ] TODO-047: BETA-Codes Backend deaktivieren
- [ ] Issue #16: FCM-Volume-Permission Fix
- [ ] WalletConnect als GitHub-Issue anlegen
- [ ] Gate-Check: Alpha-Stabilitaet > GO/HOLD/BLOCK Entscheidung
- [ ] Production-Promotion: Staged Rollout 10% (NICHT 100%)
- [ ] ALLOWED_SIGNATURES auf Railway reaktivieren nach Production-Rollout

## v1.0.23 Scope (vorbereiten)
- [ ] Fork-Protection #15 — release-blocker
- [ ] FCM-Fix #16
- [ ] Interner Test Track: v1.0.12 > v1.0.22 syncen oder deaktivieren

## F-Droid
- [x] MR !36495 Pipeline GREEN
      https://gitlab.com/fdroid/fdroiddata/-/merge_requests/36495
      Package: com.securecall.app.fdroid
      Builds: v1.0.21 (vC42) + v1.0.22 (vC43)
      Status: Wartet auf @linsui Review

## SecureChat + Chameleon
- [x] SecureChat v0.1.0-alpha (SC-00 bis SC-10 DONE)
- [x] Emergency Broadcast Modul (SC-10 Stub)
- [ ] SecureChat Release-Keystore erstellen + APK signieren
- [ ] SecureChat SHA-256 Fingerprint in docs/RELAY_NODE_SECURITY.md
- [ ] BroadcastManager Implementation (Phase 2 — Q3 2026)
- [ ] Chameleon v0.1.0-alpha: physisches Geraet Test
- [ ] IFR BuilderRegistry: Chameleon on-chain registrieren

## Infrastruktur
- [ ] ifrunit.tech DNS/Hosting pruefen — war nicht erreichbar
- [ ] Trail of Bits Audit-Anfrage stellen
- [ ] SecureCall: 551 Log.d() > Timber migrieren
- [ ] SecureCall: stealthxId additiv zu REGISTER-Message
- [ ] Backend: stealthxId im REGISTER Handler speichern

## PROZESS-REGEL §13 (BINDEND)
Play Console / App Store Status NIEMALS ohne Screenshot/API-Nachweis
als verifiziert dokumentieren. Ohne Nachweis > "UNVERIFIZIERT" markieren.
Release-Formulierung immer: "v1.0.X in Track Y mit Z% Rollout"

## MEMO FUER NEUE DEVS
WICHTIG: Lies vor allem anderen:
1. ECOSYSTEM.md — Produktuebersicht und IFR-Tiers
2. docs/RELEASE_PROCESS.md — Wie Releases funktionieren
3. docs/RELAY_ARCHITECTURE.md — Technische Architektur
4. docs/PLATFORM_TODO.md — Was offen ist (diese Datei)

NIEMALS:
- Direkt auf main entwickeln
- Stabile Features ohne Feature Branch anfassen
- SecureCall App-Code ohne separaten Branch aendern
- Stufen ueberspringen: 1 (intern) > 2 (beta) > 3 (live)
- Release-Status ohne Nachweis dokumentieren (§13)

## WICHTIGE LINKS
- Platform Hub: https://stealthx.tech
- SecureChat Site: https://securechat.stealthx.tech
- Credentials: ~/.stealthx/credentials.env
- Relay Architektur: docs/RELAY_ARCHITECTURE.md
- Release Process: docs/RELEASE_PROCESS.md
- Ecosystem Map: ECOSYSTEM.md
- Pricing: docs/PRICING.md
