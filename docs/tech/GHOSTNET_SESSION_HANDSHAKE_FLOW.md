# GhostNet Session ↔ Handshake (PATCH 200)

## 1. Ziele
- SessionState korrekt abhängig vom Handshake-Status setzen
- Outgoing Handshake → CONNECTING → ACTIVE
- Incoming Handshake → CONNECTING → ACTIVE
- Fehler → DEAD
- Debug-Hooks zur einfacheren Validierung

## 2. Ablauf

### Outgoing
1. `startOutgoingHandshake(remotePub)`
2. state = CONNECTING
3. HandshakeController.startOutgoing(remotePub)
4. Wenn ESTABLISHED → state = ACTIVE  
   sonst → state = DEAD

### Incoming
1. `acceptIncomingHandshake(remotePub)`
2. state = CONNECTING
3. HandshakeController.acceptIncoming(remotePub)
4. gleiche Logik

## 3. TODO (später)
- echte Public-Key-Aushandlung via Signaling
- Integration echter X25519-/X3DH-/Noise-Implementierung
- Retry-Mechanismen
- Secure-Wipe
