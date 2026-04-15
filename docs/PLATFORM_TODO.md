# StealthX Platform — TODO & Memo

## KRITISCH (sofort)
- [ ] SecureCall stable Tag setzen: git tag stable-2026-04-15
- [ ] Chameleon v0.1.0-alpha: physisches Geraet Test ausstehend
- [ ] IFR BuilderRegistry: Chameleon on-chain registrieren

## DIESE WOCHE
- [ ] stealth repo: ICE-Monitoring in SecLog implementieren
- [ ] stealth repo: STUN-Konfiguration optimieren
- [ ] securechat repo: Kaspa SDK Android-Kompatibilitaet testen
- [ ] Alle Repos: stable Tags setzen vor naechster Entwicklung

## NAECHSTER MONAT
- [ ] SecureCall v1.x: Tor-Signaling fuer WebRTC Handshake
- [ ] SecureChat Phase 1: Core Messaging App Android starten
- [ ] Chameleon: physisches Geraet Volltest
- [ ] Trail of Bits: Audit-Anfrage stellen

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
