# IFR Ecosystem — Partner Products

*Vendetta Labs — April 2026*

---

## ORIGO — Conway's Game of Life MMO

**Typ:** Browser-basiertes MMO auf Conway's Game of Life
**Status:** In Entwicklung — Launch Q3 2026
**Entwickler:** Vendetta Labs
**Website:** TBA

### IFR Integration
- **Game-Haupt-Token (GHIFR):** 1 GHIFR = 1 IFR (1:1 Parität, fester Wechselkurs)
- **Voucher-System:** Spieler tauschen GHIFR gegen IFR-Voucher auf ifrunit.tech
- **Fee-Sharing:** 10% der Spielgebühren gehen an den IFR-Rückkauf-Pool
- **Kein Token-Handel im Spiel:** Nur externer Voucher-Tausch via ifrunit.tech

### Umsatzmodell
- Einstieg: €1 einmalig (permanente Fraktion, Startmuster)
- Layer-Aufstieg: €0.50–€2.00 pro Layer
- Cosmetics: €0.25–€1.00 (Zellfarben, Trails, Muster-Skins)

| Spielerbasis | Monatl. IFR-Rückkauf |
|---|---|
| 1.000 | ~€370 → ~46.000 IFR |
| 10.000 | ~€3.700 → ~462.000 IFR |
| 100.000 | ~€37.000 → ~4.6 Mio. IFR |

### Spielmechanik
- **Einstieg:** €1 einmalig → permanente Fraktion, Startmuster platzieren
- **Conway übernimmt:** Ab Generation 1 kontrolliert niemand irgendetwas
- **Layer-System:** Layer 0 (Erde) → Layer 1 (Orbit) → Layer 2+ (Universum)
- **Aufstieg:** Stabile Struktur über 50 Generationen — nicht kaufbar, nicht zeitbasiert
- **Fossil-Zerfall:** Verstorbene Zellen verblassen über 30 Tage

### Rechtliche Einordnung
- **Kein Glücksspiel:** Conways Regeln sind deterministisch — kein Zufall
- **Kein Token-Handel im Spiel:** Nur externer Voucher-Tausch via ifrunit.tech
- **DSGVO-konform:** Hetzner (EU), PostgreSQL, keine US-Datenübertragung

### Technische Infrastruktur
- Conway-Engine: Rust (1 CPU-Kern → 1.000x1.000 Grid @ 10 Ticks/s)
- WebSocket-Server: Node.js (Delta-Updates, kein volles Grid)
- Redis: Grid-State im RAM
- PostgreSQL: Nutzerkonten, GHIFR-Guthaben, Voucher-Log
- Visualisierung: Three.js / WebGL (client-seitig)
- Hosting: Hetzner CX21 (bis 1.000 User) → CX41 (bis 10.000 User)

---

## SecureCall — Encrypted Voice Calls

**Typ:** Android App — E2E verschlüsselte Sprachanrufe
**Status:** Alpha Testing (15/15 Tester) — Production Q2 2026
**Entwickler:** Vendetta Labs
**Website:** stealthx.tech

### IFR Integration
- **Pro Tier:** Lock >= 1.000 IFR → Lifetime Pro Zugang
- **Premium Tier:** Lock >= 5.000 IFR → Lifetime Premium Zugang
- **WalletConnect v2:** Wallet-Verifikation direkt in der App
- **Multi-Device:** WalletConnect-verifizierte Wallets = unbegrenzte Geraete

---

*Letzte Aktualisierung: April 2026 — Vendetta Labs*
