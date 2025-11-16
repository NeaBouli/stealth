# SecureCall Backend – Signaling & GhostNet Infrastruktur

Dieser Ordner enthält alle serverseitigen Komponenten:

## Komponenten
### 1. Signaling Server
- REST API (Register, Invite, Answer, Cancel)
- WebSocket für Live-Signalisierung
- keine Persistenz von Metadaten
- pseudonyme Identität (Public Keys only)

### 2. GhostNet Relays
- STUN/TURN für NAT-Traversal
- optional eigene QUIC-basierte Relays
- Multi-Hop Routing für Premium/OS

### 3. Public-Key Directory
- speichert nur öffentliche Schlüssel
- keine Zuordnung zu Klarnamen
- kurze TTL für Einträge

### 4. Management API (Premium/OS)
- Device Owner Provisioning
- Policy Distribution
- Whitelist-Verwaltung
- Remote-Konfigurationslöschung

## Relevante Dokumente
- docs/ARCHITECTURE_OVERVIEW.md
- docs/PROJECT_PAPER.md
- docs/tasks/BACKEND-01.md
