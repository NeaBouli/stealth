/**
 * BACKEND-21 — Heartbeat / Timeout Manager
 *
 * This module tracks websocket clients and routing sessions.
 * - Sends periodic ping frames
 * - Detects broken connections
 * - Expires inactive sessions
 */

const HEARTBEAT_INTERVAL = 5000;       // alle 5 Sekunden Ping
const SESSION_TIMEOUT = 30000;         // Session läuft 30s ohne Aktivität ab

class HeartbeatManager {
  constructor(routingTable, clients) {
    this.routingTable = routingTable;
    this.clients = clients; // Map<clientId, { ws, lastSeen }>
  }

  start() {
    setInterval(() => {
      const now = Date.now();

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

        // Timeout check
        if (now - obj.lastSeen > SESSION_TIMEOUT) {
          console.log("[HB] Client timed out:", id);
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
