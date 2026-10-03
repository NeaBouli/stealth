# IFR Ecosystem — Partner Products

*Vendetta Labs — April 2026*

---

## ORIGO — Conway's Game of Life MMO

**Type:** Browser-based MMO built on Conway's Game of Life
**Status:** In development — Launch Q3 2026
**Developer:** Vendetta Labs
**Website:** TBA

### IFR Integration (proposal — not implemented)
Status 2026-10-03: none of the following exists. These are ideas for a future integration and need an
agreement with the IFR project; current IFR status: https://ifrunit.tech/wiki/transparency.html
- **Game token (GHIFR):** a GHIFR/IFR exchange rate is not defined; no fixed 1:1 parity is promised.
- **Voucher exchange:** there is no GHIFR→IFR voucher exchange on ifrunit.tech.
- **Fee sharing:** there is no IFR "buyback pool" that accepts partner fees. A contribution would need its own
  approved mechanism.
- **No token trading in-game.**

### Revenue Model
- Entry: €1 one-time (permanent faction, starting pattern)
- Layer ascent: €0.50–€2.00 per layer
- Cosmetics: €0.25–€1.00 (cell colors, trails, pattern skins)

No IFR buyback amounts are projected: the fee-sharing mechanism above does not exist.

### Game Mechanics
- **Entry:** €1 one-time → permanent faction, place starting pattern
- **Conway takes over:** From generation 1, nobody controls anything
- **Layer System:** Layer 0 (Earth) → Layer 1 (Orbit) → Layer 2+ (Universe)
- **Ascent:** Stable structure over 50 generations — not purchasable, not time-based
- **Fossil Decay:** Dead cells fade over 30 days

### Legal Classification
- **No gambling:** Conway's rules are deterministic — no randomness
- **No token trading in-game.**
- **GDPR-compliant:** Hetzner (EU), PostgreSQL, no US data transfer

### Technical Infrastructure
- Conway Engine: Rust (1 CPU core → 1,000x1,000 grid @ 10 ticks/s)
- WebSocket Server: Node.js (delta updates, no full grid)
- Redis: Grid state in RAM
- PostgreSQL: User accounts, GHIFR balances
- Visualization: Three.js / WebGL (client-side)
- Hosting: Hetzner CX21 (up to 1,000 users) → CX41 (up to 10,000 users)

---

## SecureCall — Encrypted Voice Calls

**Type:** Android App — E2E encrypted voice calls
**Status:** Alpha Testing (15/15 testers) — Production Q2 2026
**Developer:** Vendetta Labs
**Website:** stealthx.tech

### IFR Integration
- **Current public model:** Browser wallet verification on the website for Stripe checkout discounts
- **Holder eligibility:** Any positive IFR balance qualifies; no minimum token threshold
- **Discount:** The seller-displayed holder discount applies to the selected checkout product
- **Android app status:** No WalletConnect, no in-app IFR unlock in the public app line
- **Eligibility rule:** IFR wallet balance (hold model), as implemented in
  `backend/signaling/src/services/ifr.js` (`balanceOf` on the IFR token). Locked IFR in IFRLock is not required.

---

*Last updated: April 2026 — Vendetta Labs*
