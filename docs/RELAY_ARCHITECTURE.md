# StealthX Platform — Relay Architecture Summary v1.0

_Basierend auf: StealthX Relay Architektur Handbuch v1.0 (April 2026)_
_Klassifikation: INTERN — Developer Reference_

## Kernproblem

Selbst bei perfekter E2E-Verschlüsselung exponieren Third-Party-Relays Metadaten:
Wer kommuniziert mit wem, wann, wie lange, von welcher IP.
Verschlüsselung schützt den Inhalt — nicht die Kommunikationsstruktur.

## Ist-Analyse

### SecureCall — WebRTC / TURN-Relay

| Modus | Beschreibung | Relay nötig? | Metadaten-Risiko |
|-------|-------------|--------------|------------------|
| STUN/ICE Direct | P2P via NAT-Traversal | Nein | Niedrig |
| STUN + Relay Fallback | P2P versucht, TURN als Fallback | Manchmal | Mittel |
| TURN-Only (Forced) | Alle Pakete über TURN-Server | Ja (immer) | Hoch |

Kritische Punkte:
- TURN-Server kennt Client-IP, Gesprächszeitpunkt und -dauer
- GhostNet maskiert Client-IP, aber ist Single Point of Trust
- FCM (Firebase): Google sieht Wann-Metadaten
- Dynamische TURN-Credentials: positiv, aber Credential-Server ist zentral

### SecureChat — Kaspa Ephemeral Relay Nodes

| Komponente | Funktion | Risiko |
|-----------|----------|--------|
| Kaspa Identity Layer | Public Key auf BlockDAG | Niedrig (dezentral) |
| Ephemeral Relay Pool | Nachrichtenweiterleitung | Mittel (IP sichtbar) |
| SMP-style Queues | Unidirektionale Warteschlangen | Mittel |
| KAS Micropayments | Relay-Incentivierung | Niedrig |
| Onion Routing (geplant) | 2-Hop IP-Verschleierung | Niedrig |

## Bedrohungsmodell

| Level | Angreifer | Schutz-Status |
|-------|----------|---------------|
| L1 — Passive Observer | Netzwerk-Sniffing | Erreicht (E2E) |
| L2 — Aktiver Relay-Betreiber | IP, Timing, Partner | Partiell (GhostNet) |
| L3 — State-Level Adversary | Globale Beobachtung | Ziel dieses Handbuchs |

## Vier Architekturoptionen

### Option A — Tor Hidden Services

- **SecureChat:** Empfohlen. Relay Nodes als .onion Hidden Services. Latenz 200-400ms akzeptabel für Messaging.
- **SecureCall:** Nur für Signaling/Handshake. Tor zu langsam für Real-Time Voice (200-600ms RTT).
- Implementierung: `tor-android` Library, SOCKS5-Proxy für OkHttp/Ktor
- Risiken: +15-20MB APK, Battery Drain, Tor-Blocking in einigen Ländern (Mitigation: obfs4/Snowflake)

### Option B — WebRTC Direct P2P Maximierung (SecureCall)

- Aggressives ICE-Kandidaten-Management: STUN priorisieren, multiple STUN-Server
- Ziel: TURN-Rate < 30%, P2P-Rate > 70%
- ICE-Monitoring in SecLog: `connection_type` (host/srflx/relay) protokollieren
- Erwartete P2P-Raten: Full Cone NAT >98%, Restricted >90%, Port-Restricted >75%, Symmetric 0%

### Option C — Internes 2-Hop Onion Routing (Briar-Ansatz)

- Eigenes leichtgewichtiges Onion-Routing statt externer Tor-Abhängigkeit
- 2-Hop (Pro) / 3-Hop (Elite): Jeder Node kennt nur Vorgänger und Nachfolger
- Basiert vollständig auf bestehendem lazysodium X25519/XChaCha20 Stack
- Node-Discovery via Kaspa BlockDAG: X25519 PubKey + .onion in OP_RETURN Tx
- Sybil-Schutz: KAS-Deposit als Stake-Anforderung
- Kritische Masse: Minimum 50+ aktive Relay-Nodes

### Option D — Nym Mixnet (Langfristig)

- Timing-Obfuskation über Tor hinaus (Cover Traffic, zeitliche Verzögerung)
- Aktuell zu unreif für Android-Produktion
- Roadmap-Kandidat für 2027+

## Kompatibilität mit bestehendem Stack

Beide Crypto-Stacks (SecureCall + SecureChat) sind vollständig transport-agnostisch.
XChaCha20-Poly1305, X25519, Double Ratchet funktionieren identisch über Tor, P2P oder Onion-Routing.
Keine Änderungen am Crypto-Kern erforderlich.

## Empfehlungen pro Produkt

### SecureCall — Sofort (v1.x, < 4 Wochen)

1. **ICE-Monitoring:** SecLog um `connection_type` erweitern (host/srflx/relay)
2. **STUN-Optimierung:** Multiple STUN-Server, Kandidaten-Priorisierung
3. **Tor-Signaling:** WebRTC Handshake über Tor/.onion (schützt IP beim Aufbau)

### SecureCall — Mittelfristig (v2.x, 1-3 Monate)

4. **Self-hosted TURN via .onion:** Eigene TURN-Server als Tor Hidden Services
5. **UnifiedPush Evaluation:** Dezentrale Push-Infrastruktur als FCM-Alternative

### SecureChat — Phase 2 (Q3 2026)

1. **Tor Hidden Services** für alle Relay Nodes (Standard)
2. **Kaspa Node-Registry on-chain:** X25519 + .onion via OP_RETURN
3. **2-Hop Onion Routing** für Pro-Tier

### SecureChat — Phase 3 (Q4 2026)

4. **3-Hop Onion Routing** (Elite-Tier)
5. **Cover Traffic:** Dummy-Pakete gegen Traffic-Analyse
6. **Pluggable Transports:** obfs4/Snowflake für zensierte Regionen

## Implementierungs-Roadmap

| Phase | Zeitraum | Produkt | Maßnahme | Aufwand | Priorität |
|-------|----------|---------|----------|---------|-----------|
| P0 | Sofort | SecureCall | ICE-Typ Monitoring in SecLog | 2 Tage | Hoch |
| P0 | Sofort | SecureCall | ICE-Konfiguration optimieren | 3 Tage | Hoch |
| P1 | < 4 Wo. | SecureCall | WebRTC-Signaling via Tor | 1-2 Wo. | Hoch |
| P1 | < 4 Wo. | SecureChat | tor-android Library Integration | 1 Wo. | Hoch |
| P2 | Q3 2026 | SecureChat | Relay Nodes als .onion Hidden Services | 3-4 Wo. | Mittel |
| P2 | Q3 2026 | SecureChat | Kaspa Node-Registry on-chain | 2-3 Wo. | Mittel |
| P2 | Q3 2026 | SecureChat | 2-Hop Onion-Paket-Implementierung | 4-6 Wo. | Mittel |
| P3 | Q4 2026 | SecureCall | Self-Hosted TURN via .onion | 2-3 Wo. | Niedrig |
| P3 | Q4 2026 | SecureChat | 3-Hop Onion (Elite-Tier) | 3-4 Wo. | Niedrig |
| P3 | Q4 2026 | SecureChat | Cover Traffic Implementation | 2-3 Wo. | Niedrig |
| P4 | 2027 | Beide | Nym Mixnet Evaluation | Forschung | Langfristig |
| P4 | 2027 | SecureCall | UnifiedPush als FCM-Ersatz | 3-4 Wo. | Langfristig |

## Security Audit Anforderungen

Folgende Änderungen erfordern vor Release einen externen Security Audit:

1. **Internes Onion-Routing (SecureChat Phase 3):** Verschachtelte Verschlüsselung, Nonce-Wiederverwendung, Padding
2. **Kaspa Node-Registry:** On-chain Daten-Format, Replay-Schutz, KAS-Deposit-Logik
3. **Tor-Integration (SecureCall):** DTLS/SRTP Kompatibilität, Leak-Szenarien bei Circuit-Bruch
4. **Cover Traffic:** Dummy-Pakete dürfen keine echten Paketgrößen-Muster durchscheinen lassen
