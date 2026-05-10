"use strict";

const crypto = require("crypto");
const WebSocket = require("ws");

module.exports = function registerHandlers(ctx) {
  const {
    clients, clientIds, phoneNumbers, phoneHashes, fcmTokens,
    rejectionTracker, getClientId, normalizePhone, hashPhone,
    saveFcmTokens, getIceServers, CLIENT_ID_REGEX,
  } = ctx;

  return {
    REGISTER(ws, connId, msg) {
      if (!msg.clientId || typeof msg.clientId !== "string") {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "missing_client_id",
          message: "Field 'clientId' is required"
        }));
      }

      if (!CLIENT_ID_REGEX.test(msg.clientId)) {
        return ws.send(JSON.stringify({
          type: "ERROR",
          error: "invalid_client_id",
          message: "clientId must be 1-64 alphanumeric characters, hyphens, or underscores"
        }));
      }

      const allowedSigs = process.env.ALLOWED_SIGNATURES;
      if (allowedSigs && allowedSigs.trim().length > 0) {
        const allowed = allowedSigs.split(",").map(s => s.trim().toLowerCase());
        const clientSig = (msg.appSignature || "").toLowerCase();
        const forkMode = (process.env.FORK_PROTECTION_MODE || "warn").toLowerCase();
        if (!clientSig || !allowed.includes(clientSig)) {
          if (forkMode === "enforce") {
            const tracker = rejectionTracker.get(msg.clientId) || { count: 0, firstSeen: Date.now(), lastLogged: 0 };
            tracker.count++;
            if (tracker.count === 1 || tracker.count % 50 === 0) {
              console.log("[REGISTER] REJECTED — unauthorized signature:", clientSig, "from", msg.clientId, `(attempt #${tracker.count})`);
              tracker.lastLogged = Date.now();
            }
            rejectionTracker.set(msg.clientId, tracker);
            ws.send(JSON.stringify({ type: "ERROR", error: "unauthorized_client", message: "App signature not authorized" }));
            return ws.close(4003, "Unauthorized client");
          } else {
            console.warn("[REGISTER] WARN — unknown signature:", clientSig, "from", msg.clientId, "(warn mode — allowed)");
          }
        }
      }

      if (clientIds.has(msg.clientId)) {
        const existingConnId = clientIds.get(msg.clientId);
        if (existingConnId !== connId) {
          const oldClient = clients.get(existingConnId);
          if (oldClient) {
            console.log("[REGISTER] Superseding old connection for", msg.clientId, "(old connId:", existingConnId, ")");
            try { oldClient.ws.close(1000, "Superseded"); } catch (e) {}
          }
          if (fcmTokens.has(msg.clientId)) {
            fcmTokens.delete(msg.clientId);
            saveFcmTokens();
            console.log("[REGISTER] Cleared FCM token on supersede for", msg.clientId);
          }
        }
        clientIds.delete(msg.clientId);
      }

      const client = clients.get(connId);
      if (client.clientId) {
        clientIds.delete(client.clientId);
        if (client.phoneNumber) {
          if (phoneNumbers.get(client.phoneNumber) === client.clientId || phoneNumbers.get(client.phoneNumber) === msg.clientId) {
            phoneNumbers.delete(client.phoneNumber);
            phoneHashes.delete(hashPhone(client.phoneNumber));
          }
        }
      }
      client.clientId = msg.clientId;
      clientIds.set(msg.clientId, connId);

      const phone = normalizePhone(msg.phoneNumber);
      if (phone.length >= 4) {
        for (const [existingPhone, existingClientId] of phoneNumbers) {
          if (existingClientId === msg.clientId && existingPhone !== phone) {
            phoneNumbers.delete(existingPhone);
            phoneHashes.delete(hashPhone(existingPhone));
            break;
          }
        }

        const oldClientId = phoneNumbers.get(phone);
        if (oldClientId && oldClientId !== msg.clientId) {
          console.log("[REGISTER] SecureID changed for phone-hash", hashPhone(phone), ":", oldClientId, "->", msg.clientId);
          const oldConnId = clientIds.get(oldClientId);
          const oldConn = oldConnId ? clients.get(oldConnId) : null;
          if (oldConn && oldConn.ws.readyState === WebSocket.OPEN) {
            try {
              oldConn.ws.send(JSON.stringify({
                type: "SECUREID_CHANGED",
                oldClientId: oldClientId,
                newClientId: msg.clientId
              }));
            } catch (e) {}
          }
          if (clientIds.has(oldClientId) && clientIds.get(oldClientId) !== connId) {
            const supersededConnId = clientIds.get(oldClientId);
            const supersededConn = clients.get(supersededConnId);
            if (supersededConn) {
              try { supersededConn.ws.close(1000, "SecureID replaced"); } catch (e) {}
            }
          }
        }

        phoneNumbers.set(phone, msg.clientId);
        phoneHashes.set(hashPhone(phone), msg.clientId);
        client.phoneNumber = phone;
        console.log("[REGISTER] Phone:", hashPhone(phone), "->", msg.clientId);
      }

      console.log("[REGISTER]", msg.clientId, "->", connId);
      return ws.send(JSON.stringify({
        type: "REGISTERED",
        clientId: msg.clientId,
        iceServers: getIceServers(msg.clientId)
      }));
    },

    DEREGISTER(ws, connId) {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        ws.send(JSON.stringify({ type: "ERROR", error: "not_registered", message: "Must be registered to deregister" }));
        return;
      }
      for (const [phone, cid] of phoneNumbers) {
        if (cid === myClientId) {
          phoneNumbers.delete(phone);
          phoneHashes.delete(hashPhone(phone));
          console.log("[DEREGISTER] Removed phone mapping:", hashPhone(phone), "->", myClientId);
          break;
        }
      }
      if (clientIds.get(myClientId) === connId) clientIds.delete(myClientId);
      fcmTokens.delete(myClientId);
      const client = clients.get(connId);
      if (client) { client.clientId = null; client.phoneNumber = null; }
      console.log("[DEREGISTER] Client removed:", myClientId);
      return ws.send(JSON.stringify({ type: "DEREGISTER_ACK", ok: true }));
    },

    REGISTER_FCM_TOKEN(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "not_registered", message: "You must REGISTER before sending REGISTER_FCM_TOKEN" }));
      }
      if (!msg.fcmToken || typeof msg.fcmToken !== "string") {
        return ws.send(JSON.stringify({ type: "ERROR", error: "missing_fcm_token" }));
      }
      fcmTokens.set(myClientId, msg.fcmToken);
      saveFcmTokens();
      console.log("[FCM] Token registered + persisted for:", myClientId);
      return ws.send(JSON.stringify({ type: "REGISTER_FCM_TOKEN_ACK", ok: true }));
    },
  };
};
