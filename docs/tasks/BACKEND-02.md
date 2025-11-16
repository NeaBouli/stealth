# BACKEND-02 – Stable Signaling & TURN/STUN Integration

## Ziel
Den MVP-Signaling-Server (BACKEND-01) stabil erweitern, sodass Android-Clients zuverlässig Verbindungen herstellen können – auch hinter NAT und Firewalls.

## Aufgaben
### 1. STUN/TURN Infrastruktur
- Coturn-Server aufsetzen (lokal oder Cloud)
- Konfiguration:
  - no-auth oder static-auth für Entwicklung
  - keine Logs sensibler Daten
  - TCP/UDP Relaying aktivieren
  - TLS optional für spätere Produktionsphase
- Integration der TURN/STUN URLs in den Signaling-Server

### 2. Verbesserte Signaling-Logik
- Session-IDs härten (zufällig, 128 Bit)
- Basic Validation einführen:
  - kein mehrfacher INVITE
  - Timeouts für Sessions
  - Cleanup-Logik nach Disconnect
- Fehlercodes definieren (z. B. SESSION_NOT_FOUND, USER_BUSY)

### 3. WebSocket Stabilität
- Heartbeat / Ping-Pong implementieren
- automatisches Entfernen toter Verbindungen
- Wiederverbindungsmechanismus

## Akzeptanzkriterien
- stabile P2P-Verbindung in >90% der NAT-Szenarien
- TURN-Fallback funktioniert
- keine unkontrollierten Session-Leaks im RAM
- Server hält 50 parallele Session-Requests ohne Crash

## Deliverables
- aktualisierter Code im Ordner `backend/signaling/`
- Testnotizen in docs/backend/ (optional)
- Datei docs/tasks/BACKEND-02.md
