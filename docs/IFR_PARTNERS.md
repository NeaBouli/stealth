# IFR Ecosystem — Partner Products

*Vendetta Labs — April 2026*

---

## ORIGO — Conway's Game of Life MMO

**Type:** Browser-based MMO built on Conway's Game of Life
**Status:** In development — Launch Q3 2026
**Developer:** Vendetta Labs
**Website:** TBA

### IFR Integration
- **Game Main Token (GHIFR):** 1 GHIFR = 1 IFR (1:1 parity, fixed exchange rate)
- **Voucher System:** Players exchange GHIFR for IFR vouchers on ifrunit.tech
- **Fee Sharing:** 10% of game fees go to the IFR buyback pool
- **No token trading in-game:** Only external voucher exchange via ifrunit.tech

### Revenue Model
- Entry: €1 one-time (permanent faction, starting pattern)
- Layer ascent: €0.50–€2.00 per layer
- Cosmetics: €0.25–€1.00 (cell colors, trails, pattern skins)

| Player Base | Monthly IFR Buyback |
|---|---|
| 1,000 | ~€370 → ~46,000 IFR |
| 10,000 | ~€3,700 → ~462,000 IFR |
| 100,000 | ~€37,000 → ~4.6M IFR |

### Game Mechanics
- **Entry:** €1 one-time → permanent faction, place starting pattern
- **Conway takes over:** From generation 1, nobody controls anything
- **Layer System:** Layer 0 (Earth) → Layer 1 (Orbit) → Layer 2+ (Universe)
- **Ascent:** Stable structure over 50 generations — not purchasable, not time-based
- **Fossil Decay:** Dead cells fade over 30 days

### Legal Classification
- **No gambling:** Conway's rules are deterministic — no randomness
- **No token trading in-game:** Only external voucher exchange via ifrunit.tech
- **GDPR-compliant:** Hetzner (EU), PostgreSQL, no US data transfer

### Technical Infrastructure
- Conway Engine: Rust (1 CPU core → 1,000x1,000 grid @ 10 ticks/s)
- WebSocket Server: Node.js (delta updates, no full grid)
- Redis: Grid state in RAM
- PostgreSQL: User accounts, GHIFR balances, voucher log
- Visualization: Three.js / WebGL (client-side)
- Hosting: Hetzner CX21 (up to 1,000 users) → CX41 (up to 10,000 users)

---

## SecureCall — Encrypted Voice Calls

**Type:** Android App — E2E encrypted voice calls
**Status:** Alpha Testing (15/15 testers) — Production Q2 2026
**Developer:** Vendetta Labs
**Website:** stealthx.tech

### IFR Integration
- **Pro Tier:** Lock >= 1,000 IFR → Lifetime Pro access
- **Premium Tier:** Lock >= 5,000 IFR → Lifetime Premium access
- **WalletConnect v2:** Wallet verification directly in the app
- **Multi-Device:** WalletConnect-verified wallets = unlimited devices

---

*Last updated: April 2026 — Vendetta Labs*
