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
  constructor(routingTable, clients) {
    this.routingTable = routingTable;
    this.clients = clients; // Map<clientId, { ws, lastSeen }>
  }

  start() {
    setInterval(() => {
      const now = Date.now();

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

      // --- WS Clients prüfen ---
      for (const [id, obj] of this.clients.entries()) {
        const ws = obj.ws;

        // Ping senden
        try {
          ws.ping();
        } catch (e) {
          console.log("[HB] WS error, removing:", id);
          this.clients.delete(id);
        }

        // Timeout check — extended window if the client is currently in a call.
        const timeoutMs = (obj.clientId && inActiveCall.has(obj.clientId))
          ? ACTIVE_CALL_TIMEOUT
          : SESSION_TIMEOUT;
        if (now - obj.lastSeen > timeoutMs) {
          console.log("[HB] Client timed out:", id, `(after ${Math.round(timeoutMs/1000)}s idle)`);
          try { ws.terminate(); } catch {}
          this.clients.delete(id);
        }
      }

      // --- Routing-Sessions prüfen ---
      for (const [sessionId, session] of this.routingTable.entries()) {
        if (now - session.updated > SESSION_TIMEOUT) {
          console.log("[HB] Session expired:", sessionId);
          this.routingTable.delete(sessionId);
        }
      }

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
