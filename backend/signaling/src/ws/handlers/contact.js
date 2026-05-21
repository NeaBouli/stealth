"use strict";

/**
 * contact.js — IDENTIFY + CONTACT_EXCHANGE handlers
 *
 * Protocol:
 *   Client → IDENTIFY  { type, sxId }
 *   Server → IDENTIFY_ACK { type, sxId }
 *
 *   Client A → CONTACT_EXCHANGE { type, to: sxId_B, bundle: "stealthx://add/..." }
 *   Server → B: CONTACT_EXCHANGE { type, from: sxId_A, bundle }
 *   Server → A: CONTACT_EXCHANGE_ACK { type, to, delivered }
 */

const SX_ID_REGEX = /^sx_[1-9A-HJ-NP-Za-km-z]{9}$/;
const BUNDLE_PREFIX = "stealthx://add/";
const MAX_BUNDLE_LEN = 2048;

module.exports = function contactHandlers(ctx) {
  const { clients, clientIds, getClientId, sendToClient } = ctx;

  return {
    /**
     * IDENTIFY — lightweight registration for SecureChat/Chameleon sx_ identities.
     * Reuses the shared clientIds Map so sendToClient() works for routing.
     */
    IDENTIFY(ws, connId, msg) {
      const sxId = (typeof msg.sxId === "string" ? msg.sxId : "").trim();
      if (!SX_ID_REGEX.test(sxId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "invalid_sx_id",
          message: "sxId must match sx_[1-9A-HJ-NP-Za-km-z]{9}"
        }));
      }

      // Supersede any existing connection for this sxId
      if (clientIds.has(sxId)) {
        const existingConnId = clientIds.get(sxId);
        if (existingConnId !== connId) {
          const oldClient = clients.get(existingConnId);
          if (oldClient) {
            try { oldClient.ws.close(1000, "Superseded by new IDENTIFY"); } catch (_) {}
          }
          clientIds.delete(sxId);
        }
      }

      const client = clients.get(connId);
      if (client && client.clientId && client.clientId !== sxId) {
        clientIds.delete(client.clientId);
      }
      if (client) client.clientId = sxId;
      clientIds.set(sxId, connId);

      console.log("[IDENTIFY]", sxId, "->", connId);
      ws.send(JSON.stringify({ type: "IDENTIFY_ACK", sxId }));
    },

    /**
     * CONTACT_EXCHANGE — route a signed QR bundle from sender A to recipient B.
     * Sender must have called IDENTIFY first.
     */
    CONTACT_EXCHANGE(ws, connId, msg) {
      const fromSxId = getClientId(connId);
      if (!fromSxId) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "not_identified",
          message: "Send IDENTIFY before CONTACT_EXCHANGE"
        }));
      }

      const to = (typeof msg.to === "string" ? msg.to : "").trim();
      if (!SX_ID_REGEX.test(to)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "invalid_recipient",
          message: "Field 'to' must be a valid sx_ID"
        }));
      }

      const bundle = (typeof msg.bundle === "string" ? msg.bundle : "").trim();
      if (!bundle.startsWith(BUNDLE_PREFIX) || bundle.length > MAX_BUNDLE_LEN) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "invalid_bundle",
          message: "Field 'bundle' must be a valid stealthx://add/... URI"
        }));
      }

      const delivered = sendToClient(to, {
        type: "CONTACT_EXCHANGE",
        from: fromSxId,
        bundle
      });

      console.log("[CONTACT_EXCHANGE]", fromSxId, "->", to, delivered ? "✓ delivered" : "offline");

      ws.send(JSON.stringify({
        type: "CONTACT_EXCHANGE_ACK",
        to,
        delivered
      }));
    },
  };
};
