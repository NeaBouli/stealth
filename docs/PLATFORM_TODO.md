# StealthX Platform — TODO & Memo

## KRITISCH (sofort)
- [ ] Railway ENV setzen:
      ALLOWED_SIGNATURES=1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d
      Erst dann ist Fork-Protection aktiv
- [x] F-Droid Submission SecureCall: EINGEREICHT
      MR: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/36557
      Package: com.securecall.app.free
      Status: Wartet auf F-Droid Review
- [ ] Chameleon v0.1.0-alpha: physisches Geraet Test ausstehend
- [ ] IFR BuilderRegistry: Chameleon on-chain registrieren

## DIESE WOCHE
- [ ] stealth: ICE-Monitoring in SecLog (Relay Architektur v1.x)
- [ ] stealth: STUN-Konfiguration optimieren
- [ ] securechat: Kaspa SDK Android-Kompatibilitaet testen
- [ ] Alle Repos: stable Tags setzen vor naechster Entwicklung
      git tag stable-$(date +%Y-%m-%d) && git push origin --tags

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
