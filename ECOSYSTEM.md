# StealthX Platform — Ecosystem Map

## Three Products. One Stack. One Token.

| Product | Repo | Status | Type |
|---------|------|--------|------|
| SecureCall | NeaBouli/stealth | Live Beta | Android App |
| SecureChat | NeaBouli/securechat | v0.1.0-alpha (Internal Alpha) | Android App + Site |
| Chameleon | NeaBouli/chameleon | Alpha | Privacy OS — Part of SecureChat Ecosystem |

## Shared Crypto Stack
- Encryption: XChaCha20-Poly1305 (lazysodium)
- Key Exchange: X25519 ECDH
- Forward Secrecy: Double Ratchet (HKDF-SHA256)
- Key Storage: Android Keystore (StrongBox/TEE)
- KDF: Argon2id

## IFR Holder Discount (Cross-Product)

| Verified IFR status | SecureCall | SecureChat | Chameleon | Suite |
|----------|------------|------------|-----------|-------|
| No IFR balance | Normal checkout | Normal checkout | Normal checkout | — |
| Positive IFR balance | Seller-defined discount | Seller-defined discount | Seller-defined discount | Unavailable until bundle fulfillment launches |

Verification is browser-only before purchase. There is no token-amount tier threshold and no IFR or wallet mechanism inside the Android apps.

## Product Structure

```
StealthX Platform
├── SecureCall (stealthx.tech) — Voice App
└── SecureChat (securechat.stealthx.tech) — Messaging Ecosystem
    ├── SecureChat App — the Messenger
    └── Chameleon (securechat.stealthx.tech/chameleon)
        └── Privacy OS / Overlay Layer
```

## Relay Architecture Roadmap (from Handbook v1.0)

### SecureCall
- Short-term (v1.x): ICE monitoring, STUN optimization,
  Tor signaling for handshake
- Medium-term (v2.x): Self-hosted TURN via .onion,
  UnifiedPush instead of FCM

### SecureChat
- Phase 2 (Q3 2026): Tor Hidden Services for Relay Nodes,
  Kaspa Node Registry on-chain, 2-Hop Onion Routing (Pro)
- Phase 3 (Q4 2026): 3-Hop Onion Routing (Elite),
  Cover Traffic, Pluggable Transports (obfs4/Snowflake)

## SecureChat Feature Matrix

| Feature | Tier | Status | Description |
|---------|------|--------|-------------|
| E2E Messaging | Free | v0.1.0-alpha | XChaCha20-Poly1305 + Double Ratchet |
| QR/NFC Key Exchange | Free | v0.1.0-alpha | Safety Number (6x4) |
| Unlimited Contacts | Pro | Phase 2 | Paid activation; IFR holders may receive the seller-defined browser checkout discount |
| Kaspa Identity | Pro | Phase 2 | On-chain Public Key |
| 2-Hop Onion Routing | Pro | Phase 2 | Tor Hidden Services |
| 3-Hop Onion Routing | Elite | Phase 3 | Maximum Anonymity |
| Decoy Profile | Elite | Phase 3 | Wrong PIN → Empty Profile |
| Emergency Broadcast | Elite | SC-10 Stub — Phase 2 | Encrypted alert to all contacts |

## Websites
- stealthx.tech — SecureCall + Platform Hub
- securechat.stealthx.tech — SecureChat
- ifrunit.tech — IFR Token

## Fork Protection
All StealthX apps send their app signature hash (SHA-256 of
the signing certificate) when connecting to the server.
Official servers only accept signed original apps.
Based on: Android PackageManager.GET_SIGNING_CERTIFICATES
Implementation: AppSignature.kt (chameleon/stealthx-ifr)
Backend: signatureVerifier.js Middleware

## Shared Documentation
The following documents exist in ALL three repos (identical):
- ECOSYSTEM.md (this file)
- docs/RELEASE_PROCESS.md
- docs/RELAY_ARCHITECTURE.md (summary of the handbook)
- docs/PRICING.md
