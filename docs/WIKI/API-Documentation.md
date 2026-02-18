# API Documentation

## Signaling Server API

The SecureCall signaling server uses WebSocket for real-time communication and HTTP for health checks.

### HTTP Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/health` | None | Health check — returns `{"status":"ok"}` |

### WebSocket Protocol

**Connection:** `wss://signal.stealthx.app/signal`

All messages are JSON-encoded and follow this format:

```json
{
  "type": "MESSAGE_TYPE",
  "payload": { ... }
}
```

### Message Types

#### Client → Server

| Type | Description | Payload |
|------|-------------|---------|
| `REGISTER` | Register client ID | `{ "clientId": "abc123" }` |
| `CALL_INITIATE` | Start a call | `{ "targetId": "xyz789", "sdpOffer": "..." }` |
| `CALL_ACCEPT` | Accept incoming call | `{ "callId": "...", "sdpAnswer": "..." }` |
| `CALL_REJECT` | Reject incoming call | `{ "callId": "..." }` |
| `CALL_END` | End active call | `{ "callId": "..." }` |
| `ICE_CANDIDATE` | ICE candidate exchange | `{ "callId": "...", "candidate": "..." }` |
| `PKD_REGISTER` | Register public key | `{ "clientId": "...", "publicKey": "..." }` |
| `PKD_LOOKUP` | Lookup public key | `{ "clientId": "..." }` |

#### Server → Client

| Type | Description | Payload |
|------|-------------|---------|
| `REGISTERED` | Registration confirmed | `{ "clientId": "abc123" }` |
| `INCOMING_CALL` | Incoming call notification | `{ "callId": "...", "callerId": "...", "sdpOffer": "..." }` |
| `CALL_ACCEPTED` | Call was accepted | `{ "callId": "...", "sdpAnswer": "..." }` |
| `CALL_REJECTED` | Call was rejected | `{ "callId": "..." }` |
| `CALL_ENDED` | Call ended by peer | `{ "callId": "..." }` |
| `ICE_CANDIDATE` | ICE candidate from peer | `{ "callId": "...", "candidate": "..." }` |
| `PKD_RESULT` | Public key lookup result | `{ "clientId": "...", "publicKey": "..." }` |
| `ERROR` | Error message | `{ "code": "...", "message": "..." }` |

### Connection Limits

| Parameter | Value |
|-----------|-------|
| Max payload size | 64 KB |
| Max connections per IP | Rate-limited |
| Heartbeat interval | 30 seconds |
| Connection timeout | 60 seconds (no heartbeat) |
| Client ID format | `^[a-zA-Z0-9_-]{1,64}$` |

### TURN Credentials

TURN server credentials are provided by the signaling server during call setup.
The server generates time-limited credentials using the shared TURN secret.

---

[← Back to Home](Home.md)
