/**
 * BACKEND-21 — Heartbeat / Timeout Manager
 *
 * This module tracks websocket clients and routing sessions.
 * - Sends periodic ping frames
 * - Detects broken connections
 * - Expires inactive sessions
 */

const HEARTBEAT_INTERVAL = 5000;       // alle 5 Sekunden Ping
const SESSION_TIMEOUT = 60000;         // Session + Client idle timeout (60s)
const ACTIVE_CALL_TIMEOUT = 180000;    // 3 minutes — grace during media stalls

class HeartbeatManager {
  constructor(routingTable, clients, clientIds) {
    this.routingTable = routingTable;
    this.clients = clients; // Map<connId, { ws, lastSeen, clientId }>
    this.clientIds = clientIds; // Map<clientId, current connId>
  }

  start() {
    setInterval(() => {
      const now = Date.now();
      const hasCurrentClient = (clientId) => {
        const connId = this.clientIds.get(clientId);
        return Boolean(connId && this.clients.get(connId)?.clientId === clientId);
      };

      // Fix HIGH-005 (2026-04-16): compute which clients are in an active call.
      // Those get a longer timeout so a brief media stall (tunnel, network hop)
      // doesn't rip down the signaling socket and force the peer to re-INVITE.
      const inActiveCall = new Set();
      for (const [, session] of this.routingTable) {
        if (session.state === "ACTIVE") {
          if (session.from) inActiveCall.add(session.from);
          if (session.to) inActiveCall.add(session.to);
        }
      }

      // Close owns client metadata, binding, peer notification and IP cleanup.
      for (const [id, obj] of this.clients.entries()) {
        const ws = obj.ws;
        if (ws.readyState !== 1) continue;

        // Ping senden
        try {
          ws.ping();
        } catch (e) {
          console.log("[HB] WS error, terminating:", id);
          try { ws.terminate(); } catch {}
          continue;
        }

        // Timeout check — extended window if the client is currently in a call.
        const timeoutMs = (obj.clientId && inActiveCall.has(obj.clientId))
          ? ACTIVE_CALL_TIMEOUT
          : SESSION_TIMEOUT;
        if (now - obj.lastSeen > timeoutMs) {
          console.log("[HB] Client timed out:", id, `(after ${Math.round(timeoutMs/1000)}s idle)`);
          try { ws.terminate(); } catch {}
        }
      }

      // --- Routing-Sessions prüfen (collect first, delete after) ---
      const sessionsToRemove = [];
      for (const [sessionId, session] of this.routingTable.entries()) {
        // Ordinary pong/message liveness does not refresh session.updated.
        // Keep terminating owners until their close callback releases the route.
        if (session.state === "ACTIVE"
            && hasCurrentClient(session.from) && hasCurrentClient(session.to)) continue;
        if (now - session.updated > SESSION_TIMEOUT) {
          console.log("[HB] Session expired:", sessionId);
          sessionsToRemove.push(sessionId);
        }
      }
      for (const sid of sessionsToRemove) this.routingTable.delete(sid);

    }, HEARTBEAT_INTERVAL);
  }

  updateClient(id) {
    if (this.clients.has(id)) {
      this.clients.get(id).lastSeen = Date.now();
    }
  }

  updateSession(sessionId) {
    if (this.routingTable.has(sessionId)) {
      this.routingTable.get(sessionId).updated = Date.now();
    }
  }
}

module.exports = HeartbeatManager;
