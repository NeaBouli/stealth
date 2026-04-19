SecureCall Signaling Server

BACKEND-19 – Session Cleanup (automatic deletion of expired sessions)

This update introduces a server-side cleanup module that automatically removes
completed calls from the Session Registry. This keeps memory usage stable and
prevents old, no longer relevant sessions from accumulating. The cleanup runs
periodically in the background and deletes completed or failed calls after
60 seconds.

-------------------------------------------------------------------------------
1. Purpose of the Session Cleanup Function
-------------------------------------------------------------------------------

Without cleanup, the following problems could arise:

- "empty" or aborted sessions remain stored indefinitely
- increasing memory usage
- inaccurate statistics
- faulty call history for later modules
- inconsistent logs

BACKEND-19 keeps the memory clean.

-------------------------------------------------------------------------------
2. How It Works (session_registry.js)
-------------------------------------------------------------------------------

Each session has:
- sessionId
- caller
- callee
- state
- createdAt
- updatedAt
- endedAt (only for ENDED/FAILED)

The new function cleanupSessions():

- iterates through all sessions
- looks for ENDED or FAILED
- checks whether endedAt is older than 60000 ms
- removes the session from the registry
- returns a list of deleted sessionIds

-------------------------------------------------------------------------------
3. Integration into server.js
-------------------------------------------------------------------------------

A new timer runs a session cleanup every 30 seconds:

setInterval(() => {
  const removed = registry.cleanupSessions();
  if (removed.length) {
    broadcast({ type: "SESSION_CLEANUP", removed });
  }
}, 30000);

Broadcast example:

{
  "type": "SESSION_CLEANUP",
  "removed": ["session-abc", "session-def"]
}

This informs all connected clients about deleted sessions.

-------------------------------------------------------------------------------
4. Benefits for the Overall System
-------------------------------------------------------------------------------

- Stable RAM usage
- No more stale sessions
- More accurate call database
- Prerequisite for future call history APIs
- Better debugging and analysis capabilities
- Compatible with all CALL_* events from BACKEND-18

-------------------------------------------------------------------------------
5. Status BACKEND-19
-------------------------------------------------------------------------------

- session_registry.js extended
- server.js cleanup loop integrated
- Broadcast message SESSION_CLEANUP implemented
- Logging kept consistent
- No breaking changes

-------------------------------------------------------------------------------
6. Next Step
-------------------------------------------------------------------------------

BACKEND-20: Call Routing Layer (Mapping of Session → GhostNet path)


---

# BACKEND-20 — Routing Layer (Sessions + Call States)

This module extends the signaling system with:
- Session tracking
- Routing table
- Call Lifecycle (INVITE → RINGING → ACCEPTED → ACTIVE → ENDED)
- Validation of incoming messages
- Simple WebSocket-based control

## 1. Routing Table (In-Memory)

The routing table resides in the signaling server's memory.

Structure (example):

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "from": "peer-A",
  "to": "peer-B",
  "state": "ACTIVE",
  "created": 1731931274000
}
Possible states:
State	Meaning
INVITE	A wants to call B
RINGING	B has been notified
ACCEPTED	B accepted, GhostNet is being established
ACTIVE	Call is in progress
ENDED	Call ended, session marked for deletion

2. Call Lifecycle (State Machine)
objectivec
Code kopieren
CALL_INVITE
    ↓
RINGING
    ↓ (CALL_ACCEPT)
ACCEPTED
    ↓ (GhostNet ready)
ACTIVE
    ↓ (CALL_END)
ENDED  → Session is deleted
3. WebSocket Commands (MVP)
CALL_INVITE
json
Code kopieren
{
  "type": "CALL_INVITE",
  "to": "peer-123"
}
CALL_ACCEPT
json
Code kopieren
{
  "type": "CALL_ACCEPT",
  "sessionId": "..."
}
CALL_END
json
Code kopieren
{
  "type": "CALL_END",
  "sessionId": "..."
}
The server validates all inputs and updates routing + state machine accordingly.

4. REST Endpoints (Debug)
List of all active routes
bash
Code kopieren
GET /routing/list
Example output:

json
Code kopieren
{
  "routes": [
    {
      "sessionId": "...",
      "from": "peerA",
      "to": "peerB",
      "state": "ACTIVE"
    }
  ]
}
5. Tools (for testing)
flooding-test.sh
Tests load on /signal.

burst.sh
Sends rapid push messages.

routing-test.sh (new, BACKEND-20)
Tests:

CALL_INVITE

CALL_ACCEPT

Routing list

CALL_END

6. Next Steps (BACKEND-21)
Heartbeats & WS-Ping

Idle timeout for sessions

Auto-cleanup of expired routes

Prepare GhostNet pre-handshake


---

# BACKEND-21 — Heartbeat, Ping-Pong & Session Timeout

This module extends the signaling system with:

- WebSocket Heartbeat (Ping/Pong)
- Automatic detection of dead clients
- Automatic cleanup of old sessions
- Updating timestamp fields for clients & sessions

## 1. Heartbeat (Ping/Pong)

The server sends a ping to all WebSocket clients every **5 seconds**.

Each client must respond with a **Pong**.

If a client does not respond for **> 30 seconds**:
- The connection is closed
- The client is removed from the registry

## 2. Client Tracking

A client map is maintained in `server.js`:

clients = {
"<client-id>": {
ws: <WebSocket>,
lastSeen: <timestamp>
}
}

markdown
Code kopieren

`lastSeen` is updated:
- on every message
- on every pong

## 3. Session Timeout

The routing table receives an `updated` field.

If no further action occurs, a session is deleted after **30 seconds**.

Session structure:

{
sessionId,
from,
to,
state,
created,
updated
}

makefile
Code kopieren

The cleanup logic resides in `heartbeat.js`.

## 4. Debug API

Endpoint:

GET /routing/list

markdown
Code kopieren

Lists all active sessions.

Sessions disappear automatically when they expire or are ended via `CALL_END`.

## 5. Test Script

The script `tools/test_routing.sh` can still be used.
It now additionally tests:

- Session expiry after timeout
- WS heartbeat (pings visible in log)

## 6. Next Steps (BACKEND-22)

- Implement heartbeat protocol in the client (Android)
- Provide KeepAlive for GhostNet
- Prepare multi-device session model
- Load balancing for signaling nodes
