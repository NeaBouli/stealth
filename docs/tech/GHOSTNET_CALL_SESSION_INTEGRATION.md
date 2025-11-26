# PATCH 205 — Integration CallController ↔ GhostNetSession

## Ziele
- GhostNetSession wird erst aktiv, wenn der Call aktiv ist.
- Bei `CALL_BYE` oder `endCall()` wird Session beendet.

## Flow

### Outgoing Call
CALL_INIT → OUTGOING  
CALL_ANSWER (später) → ACTIVE  
→ GhostNetSession.onCallActive()  
→ SessionState = ACTIVE

### Incoming Call
CALL_INIT → RINGING  
User accepts → ACTIVE  
→ GhostNetSession.onCallActive()  
→ Medienpfad aktiviert

### Call End
CALL_BYE → ENDED  
→ GhostNetSession.onCallEnded()  
→ SessionState = DEAD  
→ Keys + Pipeline später wiped

## Status
Nur Struktur, keine Medien-, Crypto- oder Transport-Aktivierung.
