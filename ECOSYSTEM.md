# StealthX Platform — Ecosystem Map

## Drei Produkte. Ein Stack. Ein Token.

| Produkt | Repo | Status | Typ |
|---------|------|--------|-----|
| SecureCall | NeaBouli/stealth | Live Beta | Android App |
| SecureChat | NeaBouli/securechat | In Development | Android App + Site |
| Chameleon | NeaBouli/chameleon | Alpha | Android App |

## Gemeinsamer Crypto Stack
- Verschlüsselung: XChaCha20-Poly1305 (lazysodium)
- Key Exchange: X25519 ECDH
- Forward Secrecy: Double Ratchet (HKDF-SHA256)
- Key Storage: Android Keystore (StrongBox/TEE)
- KDF: Argon2id

## IFR Token Tier System (Cross-Product)

| IFR Lock | SecureCall | SecureChat | Chameleon | Suite |
|----------|------------|------------|-----------|-------|
| 0 | Free | Free | Free | — |
| >= 1.000 | Pro | — | — | — |
| >= 2.000 | Pro | Pro | Pro | — |
| >= 5.000 | Premium | Pro | Pro | — |
| >= 6.000 | Premium | Elite | Elite | — |
| >= 8.000 | Premium | Elite | Elite | Suite (alles) |

## Relay Architektur Roadmap (aus Handbuch v1.0)

### SecureCall
- Sofort (v1.x): ICE-Monitoring, STUN-Optimierung,
  Tor-Signaling für Handshake
- Mittelfristig (v2.x): Self-hosted TURN via .onion,
  UnifiedPush statt FCM

### SecureChat
- Phase 2 (Q3 2026): Tor Hidden Services für Relay Nodes,
  Kaspa Node-Registry on-chain, 2-Hop Onion Routing (Pro)
- Phase 3 (Q4 2026): 3-Hop Onion Routing (Elite),
  Cover Traffic, Pluggable Transports (obfs4/Snowflake)

## Websites
- stealthx.tech — SecureCall + Platform Hub
- securechat.stealthx.tech — SecureChat
- ifrunit.tech — IFR Token

## Shared Documentation
Folgende Dokumente existieren in ALLEN drei Repos (identisch):
- ECOSYSTEM.md (diese Datei)
- docs/RELEASE_PROCESS.md
- docs/RELAY_ARCHITECTURE.md (Zusammenfassung des Handbuchs)
