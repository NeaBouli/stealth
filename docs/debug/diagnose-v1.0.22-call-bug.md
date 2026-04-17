# Call-Bug Diagnose — v1.0.22
**Datum:** 18. April 2026
**Bugs:** #1a/#1b Call-Crash/Disconnect am Start
**Status:** ROOT CAUSE BESTÄTIGT — Fix bereit

---

## Beobachtetes Verhalten

Call 1 (S10→S7): Verbindet sich (ICE CONNECTED, DataChannel OPEN, Audio fließt). Nach ~8s bricht S7-seitig ab mit `CALL_END reason=peer_disconnected`. S10 zeigt Call weiter bis S7-Seite endet.

## Log-Befunde

### Railway Server-Logs (Session 4a45ba7c)
```
21:31:54  [REGISTER] c5dfc682 -> connId 350c86f8   (S10 registriert)
21:33:57  [ROUTING] INVITE: c5dfc682 -> 339e72c7     (Call startet)
21:34:37  [REGISTER] SUPERSEDE: c5dfc682 -> connId 3faee147 (NEW)
          alte Connection 350c86f8 mit "Superseded" geschlossen
21:34:57  [WEBRTC] OFFER + ANSWER (auf NEUER Connection)
21:34:58  [SIGNAL] disconnected: 350c86f8 (c5dfc682)  ← ALTE Connection
21:34:58  [ROUTING] Session cleaned up (disconnect): 4a45ba7c  ← SESSION GELÖSCHT!
```

### S7 Logs
```
00:34:49  ICE connection state: CONNECTED
00:34:50  DataChannel state: OPEN — P2P audio transport active
00:34:57  CALL_END received, reason=peer_disconnected
00:34:57  BUG-011: peer_disconnected but WebRTC active — delaying 15s
```

### S10 Logs
```
00:35:25  CALL_END sent for session 4a45ba7c (33s Gesprächsdauer)
00:35:25  Closing WebRTC, signaling state: CLOSED
```

## Root Cause

**`server.js:1510-1524`** — der `ws.on("close")`-Handler bereinigt ALLE Sessions eines Clients bei Disconnect, OHNE zu prüfen ob der Client sich bereits auf einer neuen Connection re-registriert hat.

Zeile 1505 prüft korrekt: `clientIds.get(clientId) === connId` bevor das clientId-Mapping gelöscht wird. Aber der Session-Cleanup-Block (1511-1524) hat diese Prüfung NICHT.

**Ablauf:**
1. S10 WebSocket dropped kurz (Netzwerk-Flicker, Samsung WiFi-PM)
2. HeartbeatClient reconnecte sofort → REGISTER supersede (neuer connId)
3. Server akzeptiert neue Connection, Call läuft weiter
4. Alte Connection (350c86f8) wird geschlossen → `ws.on("close")` feuert
5. Handler findet: `clientId = c5dfc682` hat Session `4a45ba7c` → **löscht Session + schickt CALL_END an Peer**
6. Peer (S7) erhält `peer_disconnected` → Call-Screen schließt nach 15s Grace

**Warum inkonsistent:** Nur wenn WS während des Calls dropped. Bei stabiler Connection kein Bug.

## Fix

**1 Zeile:** Zeile 1511 erweitern um den supersede-Check.

```javascript
// VORHER:
if (clientId) {
  for (const [sessionId, session] of routingTable) {

// NACHHER:
if (clientId && clientIds.get(clientId) === connId) {
  // Only clean up sessions if THIS connection is still the active one.
  // If client superseded to a new connection, the sessions belong to the new conn.
  for (const [sessionId, session] of routingTable) {
```

**Risiko:** Minimal. Die Bedingung `clientIds.get(clientId) === connId` ist dieselbe die 6 Zeilen darüber bereits für clientId-Cleanup verwendet wird. Kein neues Pattern.

**§3:** Nur 1 Zeile geändert (Bedingung erweitert). Keine anderen Module betroffen. Zahlende User nicht betroffen (Server-only Fix).

## Bug #2 (Lautsprecher-Routing)

Noch nicht reproduziert — Call 1 wurde für Diagnose von Bug #1 verwendet. Wird nach dem Fix in Call 2+ getestet.

---

*Diagnose erstellt: 18. April 2026, 00:40 EEST*
