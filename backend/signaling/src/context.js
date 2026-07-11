"use strict";

/**
 * context.js — wires all extracted modules into a single injectable context.
 *
 * Usage (Step 8 wiring in server.js):
 *   const { buildContext, wireRoutes, wireWs } = require("./context");
 *   const ctx = buildContext();
 *   wireRoutes(app, ctx);
 *   wireWs(wss, ctx);
 */

const WebSocket = require("ws");

// State
const state = require("./state");

// Utils
const { normalizePhone, hashPhone } = require("./utils/phone");
const { sanitize } = require("./utils/sanitize");

// Middleware factories
const { getClientIp } = require("./middleware/ip");
const { makeCorsMiddleware } = require("./middleware/cors");
const { makeRequireAdmin } = require("./middleware/admin");

// Store services
const { fcmTokens, loadFcmTokens, saveFcmTokens } = require("./services/fcm_store");
const { activationCodes, loadActivationCodes, saveActivationCodes } = require("./services/activation_store");
const { walletMappings, loadWalletMappings, saveWalletMappings } = require("./services/wallet_store");
const { verifyIfrLock } = require("./services/ifr");

// Route modules
const healthRoutes = require("./routes/health");
const pkdRoutes = require("./routes/pkd");
const licensesRoutes = require("./routes/licenses");

// WS dispatcher + handlers
const wsDispatcher = require("./ws/index");
const { buildHandlers } = require("./ws/handlers/index");

// External service modules (still in server.js scope — passed in via buildContext)
// pkd, subscriptions, fcm, customIds, licenses, ICE_SERVERS are loaded separately

function buildHelpers(ctx) {
  const { clients, clientIds, routingTable } = ctx;

  function sendToClient(clientId, payload) {
    const connId = clientIds.get(clientId);
    if (!connId) return false;
    const client = clients.get(connId);
    if (!client || client.ws.readyState !== WebSocket.OPEN) return false;
    client.ws.send(JSON.stringify(payload));
    return true;
  }

  function getClientId(connId) {
    const client = clients.get(connId);
    return client ? client.clientId : null;
  }

  function getSessionPeer(sessionId, myClientId) {
    const session = routingTable.get(sessionId);
    if (!session) return null;
    if (session.from === myClientId) return session.to;
    if (session.to === myClientId) return session.from;
    return null;
  }

  function forwardBinaryToPeer(connId, data) {
    const myClientId = getClientId(connId);
    if (!myClientId) return false;
    for (const [, session] of routingTable) {
      if (session.state !== "ACTIVE") continue;
      let peerClientId = null;
      if (session.from === myClientId) peerClientId = session.to;
      else if (session.to === myClientId) peerClientId = session.from;
      else continue;
      const peerConnId = clientIds.get(peerClientId);
      if (!peerConnId) continue;
      const peer = clients.get(peerConnId);
      if (!peer || peer.ws.readyState !== WebSocket.OPEN) continue;
      peer.ws.send(data, { binary: true });
      return true;
    }
    return false;
  }

  return { sendToClient, getClientId, getSessionPeer, forwardBinaryToPeer };
}

/**
 * Build the full injectable context.
 * externalDeps must include: pkd, subscriptions, fcm, customIds, licenses,
 *   getIceServers, ADMIN_API_KEY, ALLOWED_ORIGINS, rateLimit, hb, giftCodes, saveGiftCodes
 * Optional overrides: saveActivationCodes, verifyIfrLock (for test isolation)
 */
function buildContext(externalDeps) {
  const {
    pkd, subscriptions, fcm, customIds, licenses,
    getIceServers, ADMIN_API_KEY, ALLOWED_ORIGINS,
    rateLimit, hb, giftCodes, saveGiftCodes,
    CLIENT_ID_REGEX,
    saveActivationCodes: saveActivationCodesOverride,
    verifyIfrLock: verifyIfrLockOverride,
    issueEntitlementToken,
    saveWalletMappings: saveWalletMappingsOverride,
  } = externalDeps;

  // Core state from state.js (Maps/Arrays are shared references)
  const {
    clients, clientIds, routingTable, phoneNumbers, phoneHashes,
    ipConnections, rejectionTracker, ipConnectionAttempts,
    codeUsageCount, siweChallenges, lastBroadcast,
  } = state;

  const requireAdmin = makeRequireAdmin(ADMIN_API_KEY);
  const corsMiddleware = makeCorsMiddleware(ALLOWED_ORIGINS);

  const ctx = {
    // State
    clients, clientIds, routingTable, phoneNumbers, phoneHashes,
    ipConnections, rejectionTracker, ipConnectionAttempts,
    codeUsageCount, siweChallenges, lastBroadcast,
    // Stores (live references — mutation via .splice() is safe)
    fcmTokens, activationCodes, walletMappings,
    giftCodes, saveGiftCodes,
    // Store ops
    loadFcmTokens, saveFcmTokens,
    loadActivationCodes,
    saveActivationCodes: saveActivationCodesOverride || saveActivationCodes,
    loadWalletMappings,
    saveWalletMappings: saveWalletMappingsOverride || saveWalletMappings,
    // Utils
    normalizePhone, hashPhone, sanitize,
    // Middleware
    getClientIp, requireAdmin, corsMiddleware,
    // Business logic
    verifyIfrLock: verifyIfrLockOverride || verifyIfrLock,
    issueEntitlementToken,
    // External services
    pkd, subscriptions, fcm, customIds, licenses,
    getIceServers, ADMIN_API_KEY, ALLOWED_ORIGINS,
    CLIENT_ID_REGEX,
    // Infra
    rateLimit, hb,
  };

  // Helpers need the state refs from ctx
  const helpers = buildHelpers(ctx);
  Object.assign(ctx, helpers);

  // Build WS handler-map
  ctx.handlers = buildHandlers(ctx);

  return ctx;
}

function wireRoutes(app, ctx) {
  healthRoutes.setup(app);
  pkdRoutes.setup(app, { pkd: ctx.pkd, requireAdmin: ctx.requireAdmin });
  licensesRoutes.setup(app, { licenses: ctx.licenses, requireAdmin: ctx.requireAdmin });
}

function wireWs(wss, ctx) {
  wsDispatcher.setup(wss, ctx);
}

module.exports = { buildContext, wireRoutes, wireWs };
