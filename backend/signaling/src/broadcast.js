/**
 * BACKEND-12 — Broadcast Framework (MVP)
 *
 * Aufgaben:
 *  - Nachricht an mehrere WebSocket-Clients senden
 *  - Fehler ignoren, damit ein defekter Client den Broadcast nicht blockiert
 *  - spaeter: Filter, Gruppen, Rollen, Organisations-Scopes
 *
 * Integration folgt in Patch 89.
 */

const { safeSend } = require("./safeSend");
const { logJSON } = require("./logger");

class Broadcaster {
  constructor() {}

  // sendet eine Nachricht an *alle* Clients in einer Map
  broadcastAll(clients, msg) {
    for (const [connId, ws] of clients.entries()) {
      try {
        safeSend(ws, msg);
        logJSON("BROADCAST_SENT", { connId });
      } catch (e) {
        logJSON("BROADCAST_ERROR", {
          connId,
          error: e.message
        });
      }
    }
  }

  // spaeter: broadcast an bestimmte Gruppe
  broadcastFiltered(clients, filterFn, msg) {
    for (const [connId, ws] of clients.entries()) {
      if (!filterFn(connId)) continue;
      try {
        safeSend(ws, msg);
        logJSON("BROADCAST_SENT", { connId });
      } catch (e) {
        logJSON("BROADCAST_ERROR", {
          connId,
          error: e.message
        });
      }
    }
  }
}

module.exports = {
  Broadcaster
};
