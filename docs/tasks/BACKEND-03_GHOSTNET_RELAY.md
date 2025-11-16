# BACKEND-03 – GhostNet Relay Node (Single-Hop)

## Ziel
Erstellung eines Relay-Knotens für GhostNet, der Audioframes anonym weiterleitet.

Später in BACKEND-04 wird daraus Multi-Hop.

## Funktionsumfang

### 1. Ein-/Ausgangs-Streams
- empfängt verschlüsselte Frames von Client A
- leitet Frames an Client B weiter
- Relay kennt niemals Schlüssel oder Klartext

### 2. Keine Persistenz
- keine Logs mit ID/IP
- keine Session-Speicherung außer In-Memory
- automatische Löschung nach Session-Ende

### 3. Transport
- WebRTC DataChannel oder QUIC-Stream
- Heartbeat für liveness

### 4. Sicherheit
- strikte Frame-Längenprüfung
- Anti-Flood Limit (pps)
- automatische Trennung bei ungewöhnlichem Traffic

## Tests
- A ↔ Relay ↔ B funktioniert stabil
- Relay darf keine Daten entschlüsseln können
- Flooding löst Trennung aus

