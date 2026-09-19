"use strict";

/**
 * routes/status.js — public service-status endpoint.
 *
 * STX-02 (audit issue #84): the unauthenticated GET /status/live response
 * previously serialized ipConnectionBuckets with real client IPs (IPv4 and
 * IPv6) of everyone connected. Client addresses are personal data and must
 * not leave the server on a public endpoint. The payload keeps only
 * non-identifying availability/count/limit facts. Per-IP state still exists
 * server-side for WebSocket connection limiting — it is just never exposed.
 */

const WebSocket = require("ws");

// Rebuild the per-IP connection map from live clients. Used by WS verifyClient
// limiting and refreshed on /status/live; the rebuilt data stays server-side.
function reconcileIpConnections(clients, ipConnections) {
  const rebuilt = new Map();
  for (const [, client] of clients) {
    if (!client || !client.ip) continue;
    if (client.ws && client.ws.readyState !== WebSocket.OPEN) continue;
    rebuilt.set(client.ip, (rebuilt.get(client.ip) || 0) + 1);
  }
  ipConnections.clear();
  for (const [ip, count] of rebuilt) ipConnections.set(ip, count);
}

// Public payload — deliberately receives no per-IP state, so it cannot leak it.
function buildLiveStatus({ clients, clientIds, fcmTokens, wsLimits }) {
  return {
    server: "online",
    uptime: Math.floor(process.uptime()),
    connectedClients: clients ? clients.size : 0,
    registeredIds: clientIds ? clientIds.size : 0,
    fcmTokens: fcmTokens ? fcmTokens.size : 0,
    wsLimits: {
      maxConnectionsPerIp: wsLimits.maxConnectionsPerIp,
      maxAttemptsPerIp: wsLimits.maxAttemptsPerIp,
      attemptWindowMs: wsLimits.attemptWindowMs
    },
    timestamp: new Date().toISOString()
  };
}

function setup(app, { clients, clientIds, fcmTokens, ipConnections, wsLimits }) {
  app.get("/status/live", (req, res) => {
    reconcileIpConnections(clients, ipConnections);
    res.json(buildLiveStatus({ clients, clientIds, fcmTokens, wsLimits }));
  });
}

module.exports = { setup, buildLiveStatus, reconcileIpConnections };
