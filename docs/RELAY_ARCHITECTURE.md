# StealthX Platform — Relay Architecture Summary v1.0

_Based on: StealthX Relay Architecture Handbook v1.0 (April 2026)_
_Classification: INTERNAL — Developer Reference_

> **Current release note (Android 1.0.50):** GhostNet relay and IP-masking
> references below describe a research architecture, not functionality shipped
> in the current SecureCall app. The released client uses WebRTC/STUN/TURN and
> supports externally managed device VPNs. The direct Premium APK may also use
> its optional app-only WireGuard tunnel; Play builds contain no VPN service.

## Core Problem

Even with perfect E2E encryption, third-party relays expose metadata:
Who communicates with whom, when, for how long, from which IP.
Encryption protects the content — not the communication structure.

## Current State Analysis

### SecureCall — WebRTC / TURN Relay

| Mode | Description | Relay needed? | Metadata Risk |
|------|-------------|---------------|---------------|
| STUN/ICE Direct | P2P via NAT traversal | No | Low |
| STUN + Relay Fallback | P2P attempted, TURN as fallback | Sometimes | Medium |
| TURN-Only (Forced) | All packets via TURN server | Yes (always) | High |

Critical points:
- TURN server knows client IP, call time and duration
- A future privacy relay could mask the client IP, but would be a single point of trust
- FCM (Firebase): Google sees when-metadata
- Dynamic TURN credentials: positive, but credential server is centralized

### SecureChat — Kaspa Ephemeral Relay Nodes

| Component | Function | Risk |
|-----------|----------|------|
| Kaspa Identity Layer | Public key on BlockDAG | Low (decentralized) |
| Ephemeral Relay Pool | Message forwarding | Medium (IP visible) |
| SMP-style Queues | Unidirectional queues | Medium |
| KAS Micropayments | Relay incentivization | Low |
| Onion Routing (planned) | 2-hop IP obfuscation | Low |

## Threat Model

| Level | Attacker | Protection Status |
|-------|----------|-------------------|
| L1 — Passive Observer | Network sniffing | Achieved (E2E) |
| L2 — Active Relay Operator | IP, timing, partner | Partial (GhostNet) |
| L3 — State-Level Adversary | Global observation | Goal of this handbook |

## Four Architecture Options

### Option A — Tor Hidden Services

- **SecureChat:** Recommended. Relay nodes as .onion Hidden Services. Latency 200-400ms acceptable for messaging.
- **SecureCall:** Only for signaling/handshake. Tor too slow for real-time voice (200-600ms RTT).
- Implementation: `tor-android` library, SOCKS5 proxy for OkHttp/Ktor
- Risks: +15-20MB APK, battery drain, Tor blocking in some countries (mitigation: obfs4/Snowflake)

### Option B — WebRTC Direct P2P Maximization (SecureCall)

- Aggressive ICE candidate management: prioritize STUN, multiple STUN servers
- Goal: TURN rate < 30%, P2P rate > 70%
- ICE monitoring in SecLog: log `connection_type` (host/srflx/relay)
- Expected P2P rates: Full Cone NAT >98%, Restricted >90%, Port-Restricted >75%, Symmetric 0%

### Option C — Internal 2-Hop Onion Routing (Briar approach)

- Own lightweight onion routing instead of external Tor dependency
- 2-hop (Pro) / 3-hop (Elite): Each node knows only predecessor and successor
- Based entirely on existing lazysodium X25519/XChaCha20 stack
- Node discovery via Kaspa BlockDAG: X25519 PubKey + .onion in OP_RETURN Tx
- Sybil protection: KAS deposit as stake requirement
- Critical mass: Minimum 50+ active relay nodes

### Option D — Nym Mixnet (Long-term)

- Timing obfuscation beyond Tor (cover traffic, time delay)
- Currently too immature for Android production
- Roadmap candidate for 2027+

## Compatibility with Existing Stack

Both crypto stacks (SecureCall + SecureChat) are fully transport-agnostic.
XChaCha20-Poly1305, X25519, Double Ratchet work identically over Tor, P2P, or onion routing.
No changes to the crypto core required.

## Recommendations per Product

### SecureCall — Immediate (v1.x, < 4 weeks)

1. **ICE Monitoring:** Extend SecLog with `connection_type` (host/srflx/relay)
2. **STUN Optimization:** Multiple STUN servers, candidate prioritization
3. **Tor Signaling:** WebRTC handshake via Tor/.onion (protects IP during setup)

### SecureCall — Medium-term (v2.x, 1-3 months)

4. **Self-hosted TURN via .onion:** Own TURN servers as Tor Hidden Services
5. **UnifiedPush Evaluation:** Decentralized push infrastructure as FCM alternative

### SecureChat — Phase 2 (Q3 2026)

1. **Tor Hidden Services** for all relay nodes (standard)
2. **Kaspa Node Registry on-chain:** X25519 + .onion via OP_RETURN
3. **2-Hop Onion Routing** for Pro tier

### SecureChat — Phase 3 (Q4 2026)

4. **3-Hop Onion Routing** (Elite tier)
5. **Cover Traffic:** Dummy packets against traffic analysis
6. **Pluggable Transports:** obfs4/Snowflake for censored regions

## Implementation Roadmap

| Phase | Timeframe | Product | Measure | Effort | Priority |
|-------|-----------|---------|---------|--------|----------|
| P0 | Immediate | SecureCall | ICE type monitoring in SecLog | 2 days | High |
| P0 | Immediate | SecureCall | Optimize ICE configuration | 3 days | High |
| P1 | < 4 wks | SecureCall | WebRTC signaling via Tor | 1-2 wks | High |
| P1 | < 4 wks | SecureChat | tor-android library integration | 1 wk | High |
| P2 | Q3 2026 | SecureChat | Relay nodes as .onion Hidden Services | 3-4 wks | Medium |
| P2 | Q3 2026 | SecureChat | Kaspa node registry on-chain | 2-3 wks | Medium |
| P2 | Q3 2026 | SecureChat | 2-hop onion packet implementation | 4-6 wks | Medium |
| P3 | Q4 2026 | SecureCall | Self-hosted TURN via .onion | 2-3 wks | Low |
| P3 | Q4 2026 | SecureChat | 3-hop onion (Elite tier) | 3-4 wks | Low |
| P3 | Q4 2026 | SecureChat | Cover traffic implementation | 2-3 wks | Low |
| P4 | 2027 | Both | Nym Mixnet evaluation | Research | Long-term |
| P4 | 2027 | SecureCall | UnifiedPush as FCM replacement | 3-4 wks | Long-term |

## Security Audit Requirements

The following changes require an external security audit before release:

1. **Internal Onion Routing (SecureChat Phase 3):** Nested encryption, nonce reuse, padding
2. **Kaspa Node Registry:** On-chain data format, replay protection, KAS deposit logic
3. **Tor Integration (SecureCall):** DTLS/SRTP compatibility, leak scenarios on circuit break
4. **Cover Traffic:** Dummy packets must not leak real packet size patterns
