"use strict";

const crypto = require("crypto");

/**
 * Wire up WebSocket connection lifecycle and message dispatch.
 *
 * ctx must provide:
 *   clients, clientIds, routingTable, ipConnections  — state maps (from state.js)
 *   rateLimit        — { registerEvent, registerBinaryEvent, clear }
 *   hb               — { updateClient }
 *   getClientIp      — (req) => string
 *   sendToClient     — (clientId, msg) => void
 *   forwardBinaryToPeer — (connId, data) => bool
 *   handlers         — { [MSG_TYPE]: (ws, connId, msg, ctx) => void }
 */
function setup(wss, ctx) {
  const {
    clients,
    clientIds,
    routingTable,
    ipConnections,
    rateLimit,
    hb,
    getClientIp,
    sendToClient,
    forwardBinaryToPeer,
    handlers,
  } = ctx;

  wss.on("connection", (ws, req) => {
    const connId = crypto.randomUUID();
    const ip = getClientIp(req);
    console.log("[SIGNAL] connected:", connId, "ip:", ip);

    ipConnections.set(ip, (ipConnections.get(ip) || 0) + 1);
    clients.set(connId, { ws, lastSeen: Date.now(), clientId: null, ip });

    ws.on("pong", () => hb.updateClient(connId));

    ws.on("message", (data, isBinary) => {
      hb.updateClient(connId);

      // Binary audio relay — bypass JSON rate limit, validate inside forwardBinaryToPeer
      if (isBinary) {
        if (!rateLimit.registerBinaryEvent(connId)) return;
        forwardBinaryToPeer(connId, data);
        return;
      }

      if (!rateLimit.registerEvent(connId)) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "rate_limited" }));
      }

      let msg;
      try {
        msg = JSON.parse(data.toString());
      } catch {
        return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_json" }));
      }

      // BUG-074: strip prototype pollution keys unconditionally
      for (const key of ["__proto__", "constructor", "prototype"]) {
        if (Object.prototype.hasOwnProperty.call(msg, key)) delete msg[key];
      }

      if (msg.type === "HEARTBEAT") {
        return ws.send(JSON.stringify({ type: "HEARTBEAT_ACK" }));
      }

      const handler = handlers[msg.type];
      if (handler) {
        handler(ws, connId, msg, ctx);
      } else {
        ws.send(JSON.stringify({
          type: "ERROR",
          error: "unknown_message_type",
          provided: msg.type,
        }));
      }
    });

    ws.on("close", () => {
      const client = clients.get(connId);
      const clientId = client ? client.clientId : null;
      const clientIp = client ? client.ip : null;

      console.log("[SIGNAL] disconnected:", connId, clientId ? `(${clientId})` : "");

      if (clientIp) {
        const count = ipConnections.get(clientIp) || 1;
        if (count <= 1) ipConnections.delete(clientIp);
        else ipConnections.set(clientIp, count - 1);
      }

      rateLimit.clear(connId);

      // Bug #1 guard: only clean up if this connection is still the active one.
      // A superseded connection must not remove the new mapping or active sessions.
      const isActiveConnection = clientId && clientIds.get(clientId) === connId;

      if (isActiveConnection) {
        for (const [sessionId, session] of routingTable) {
          if (session.from === clientId || session.to === clientId) {
            const peerId = session.from === clientId ? session.to : session.from;
            sendToClient(peerId, { type: "CALL_END", sessionId, reason: "peer_disconnected" });
            routingTable.delete(sessionId);
            console.log("[ROUTING] Session cleaned up (disconnect):", sessionId);
          }
        }
        clientIds.delete(clientId);
      } else if (clientId) {
        console.log("[ROUTING] Skip session cleanup — client superseded:", clientId);
      }

      clients.delete(connId);
    });
  });
}

module.exports = { setup };
