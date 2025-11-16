# BACKEND-04 – Multi-Hop GhostNet Routing

## Ziel
Mehrere Relays in Kette verbinden, um Metadaten zu verschleiern.

## Architektur

Client A  
→ Relay 1  
→ Relay 2  
→ Relay 3  
→ Client B

## Anforderungen

### 1. Hop-Kette generieren
- zufällig
- konfigurierbare Länge (2–4 Hops)
- Relays kennen immer nur ihren Vorgänger/Nachfolger

### 2. Onion-Payload (leichtgewichtig)
- jede Hop-Ebene verschlüsselt einen kleinen Header
- optional vollwertiges Onion-Routing später

### 3. Session-Verhalten
- Hop-Liste wird im Signaling übertragen (verschlüsselt)
- Clients bauen Kette selbst auf

### 4. Sicherheit
- kein Relay kennt die gesamte Route
- kein Relay kann Traffic entschlüsseln
- Timing/Size Obfuscation optional

## Tests
- 2-Hop, 3-Hop, 4-Hop funktionieren
- Relay-Ausfall → automatischer Re-Routing Versuch
- kein Relay sieht beide Endpunkte

