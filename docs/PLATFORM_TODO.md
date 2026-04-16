# StealthX Platform — TODO & Memo

## KRITISCH (sofort)
- [ ] Railway ENV setzen:
      ALLOWED_SIGNATURES=1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d
      Erst dann ist Fork-Protection aktiv
- [x] F-Droid Submission SecureCall: EINGEREICHT
      MR: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/36495 (AKTIV)
      Package: com.securecall.app.fdroid
      Status: Wartet auf Re-Review nach 2. Reviewer-Feedback-Runde (@linsui)
      Runde 1 Fixes (fedc66697): commit hash, fastlane structure, App Inclusion template
      Runde 2 Fixes (5deae6a68): AllowedAPKSigningKeys, Binaries, rustup from debian,
                                 removed all || true, fixed rewritemeta trailing space
      MR !36557 geschlossen (Duplikat), MR !36115 geschlossen (alt)
- [ ] Chameleon v0.1.0-alpha: physisches Geraet Test ausstehend
- [ ] IFR BuilderRegistry: Chameleon on-chain registrieren

## DIESE WOCHE
- [x] SecureChat v0.1.0-alpha gebaut (SC-00 bis SC-09 DONE)
- [x] Emergency Broadcast Modul (SC-10 Stub) erstellt
- [x] ifrunit.tech Links korrigiert (8 Links)
- [ ] stealth: ICE-Monitoring in SecLog (Relay Architektur v1.x)
- [ ] stealth: STUN-Konfiguration optimieren
- [ ] securechat: Kaspa SDK Android-Kompatibilitaet testen
- [x] Alle Repos: stable Tags gesetzt (stable-2026-04-16-pre-appdev)

## OFFEN — SC-10 Emergency Broadcast
- [ ] BroadcastManager Implementation (Phase 2 — Q3 2026)
- [ ] Transport via Kaspa Relay Nodes
- [ ] UI vollstaendig (aktuell Stub/Placeholder)
- [ ] Unit Tests fuer Broadcast-Verschluesselung

## KRITISCH (nach SecureChat Release)
- [ ] SecureChat Release-Keystore erstellen + APK signieren
- [ ] SecureChat SHA-256 Fingerprint in docs/RELAY_NODE_SECURITY.md
- [ ] ifrunit.tech DNS/Hosting pruefen — war nicht erreichbar
- [ ] Trail of Bits Audit-Anfrage stellen
- [ ] SecureCall: 551 Log.d() → Timber migrieren
- [ ] SecureCall: stealthxId additiv zu REGISTER-Message
- [ ] Backend: stealthxId im REGISTER Handler speichern

## NAECHSTER MONAT
- [ ] SecureCall v1.x: Tor-Signaling fuer WebRTC Handshake
- [ ] SecureChat Phase 1: Core Messaging App Android starten
- [ ] Chameleon: physisches Geraet Volltest
- [ ] Trail of Bits: Audit-Anfrage stellen

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
- Stufen ueberspringen: 1 (intern) -> 2 (beta) -> 3 (live)

## MEMO — RELEASE REGELN
1. NIE direkt auf main entwickeln — immer Feature Branch
2. VOR jedem neuen Feature: stable Tag auf aktuellem Stand
3. SecureCall App: KEINE Code-Aenderungen ohne separaten
   Feature Branch und vollstaendige Tests
4. Stufe 1 -> 2 -> 3 (intern -> closed beta -> live)
   Kein Sprung erlaubt.

## WICHTIGE LINKS
- Platform Hub: https://stealthx.tech
- SecureChat Site: https://securechat.stealthx.tech
- IFR Token: https://ifrunit.tech
- Relay Architektur: docs/RELAY_ARCHITECTURE.md
- Release Process: docs/RELEASE_PROCESS.md
- Ecosystem Map: ECOSYSTEM.md
