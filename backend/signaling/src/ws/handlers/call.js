"use strict";

const crypto = require("crypto");

module.exports = function callHandlers(ctx) {
  const {
    clients, clientIds, routingTable, fcmTokens,
    phoneNumbers, getClientId, getSessionPeer, sendToClient,
    normalizePhone, hashPhone, sanitize, fcm, customIds,
  } = ctx;

  return {
    CALL_INVITE(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "not_registered", message: "You must REGISTER before sending CALL_INVITE" }));
      }
      if (!msg.to) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "missing_to", message: "Field 'to' is required" }));
      }

      const sessionId = crypto.randomUUID();

      let targetClientId = msg.to;
      if (!clientIds.has(targetClientId)) {
        const customDeviceId = customIds.resolve(targetClientId);
        if (customDeviceId) {
          console.log("[ROUTING] Custom ID resolved:", targetClientId, "->", customDeviceId);
          targetClientId = customDeviceId;
        } else {
          const phoneLookup = phoneNumbers.get(normalizePhone(targetClientId));
          if (phoneLookup) {
            console.log("[ROUTING] Phone resolved: hash", hashPhone(targetClientId), "->", phoneLookup);
            targetClientId = phoneLookup;
          }
        }
      }

      if (clientIds.has(targetClientId)) {
        routingTable.set(sessionId, {
          sessionId,
          from: myClientId,
          to: targetClientId,
          state: "INVITE",
          created: Date.now(),
          updated: Date.now()
        });
        console.log("[ROUTING] INVITE:", myClientId, "->", targetClientId, "session:", sessionId);
        ws.send(JSON.stringify({ type: "CALL_INVITE_ACK", ok: true, sessionId, from: myClientId, to: targetClientId }));
        const wsDelivered = sendToClient(targetClientId, { type: "CALL_INVITE", sessionId, from: myClientId, to: targetClientId, pubKey: msg.pubKey, callerPhone: msg.callerPhone || "" });
        console.log("[ROUTING] INVITE WS delivery:", wsDelivered ? "sent" : "failed", "target:", targetClientId, "session:", sessionId);
        const fcmToken = fcmTokens.get(targetClientId) || fcmTokens.get(msg.to);
        if (fcmToken && fcm.isInitialized()) {
          fcm.sendCallInvitePush(fcmToken, sessionId, myClientId, msg.callerPhone || "")
            .then((ok) => console.log("[ROUTING] INVITE push backup:", ok ? "sent" : "failed", "target:", targetClientId, "session:", sessionId))
            .catch((err) => console.warn("[ROUTING] INVITE push backup error:", err.message));
        }
      } else {
        const fcmToken = fcmTokens.get(targetClientId) || fcmTokens.get(msg.to);
        if (fcmToken && fcm.isInitialized()) {
          routingTable.set(sessionId, {
            sessionId,
            from: myClientId,
            to: targetClientId,
            state: "INVITE_PENDING_PUSH",
            created: Date.now(),
            updated: Date.now()
          });
          console.log("[ROUTING] INVITE (offline, sending push):", myClientId, "->", targetClientId, "session:", sessionId);
          fcm.sendCallInvitePush(fcmToken, sessionId, myClientId);
          ws.send(JSON.stringify({ type: "CALL_INVITE_ACK", ok: true, sessionId, from: myClientId, to: targetClientId, pushSent: true }));
        } else {
          return ws.send(JSON.stringify({ type: "ERROR", error: "peer_not_found", message: `Client '${sanitize(msg.to)}' is not online` }));
        }
      }
    },

    CALL_ACCEPT(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "not_registered", message: "You must REGISTER before sending CALL_ACCEPT" }));
      }
      const session = routingTable.get(msg.sessionId);
      if (!session) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "session_not_found" }));
      }
      if (session.to !== myClientId) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "not_callee", message: "Only the intended callee can accept this call" }));
      }
      session.state = "ACTIVE";
      session.updated = Date.now();
      console.log("[ROUTING] ACCEPT:", msg.sessionId, "by", myClientId);
      ws.send(JSON.stringify({ type: "CALL_ACCEPT_ACK", ok: true, sessionId: msg.sessionId }));
      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, { type: "CALL_ACCEPT", sessionId: msg.sessionId, from: myClientId, pubKey: msg.pubKey });
      }
    },

    CALL_BUSY(ws, connId, msg) {
      const myClientId = getClientId(connId);
      const session = routingTable.get(msg.sessionId);
      if (session && session.to === myClientId) {
        const callerClientId = session.from;
        sendToClient(callerClientId, { type: "CALL_BUSY", sessionId: msg.sessionId, from: myClientId });
        routingTable.delete(msg.sessionId);
        console.log("[ROUTING] BUSY:", myClientId, "-> caller:", callerClientId, "session:", msg.sessionId);
      }
    },

    CALL_END(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (msg.sessionId && routingTable.has(msg.sessionId)) {
        const session = routingTable.get(msg.sessionId);
        if (session.from !== myClientId && session.to !== myClientId) {
          return ws.send(JSON.stringify({ type: "ERROR", error: "not_participant", message: "Only call participants can end this call" }));
        }
        const peerClientId = getSessionPeer(msg.sessionId, myClientId);
        if (peerClientId) {
          // Recovery-sensitive disconnect reasons are reserved for server-owned paths.
          const reason = "user_hangup";
          sendToClient(peerClientId, {
            type: "CALL_END",
            sessionId: msg.sessionId,
            from: myClientId,
            reason,
          });
        }
        routingTable.delete(msg.sessionId);
        console.log("[ROUTING] END:", msg.sessionId, "by", myClientId);
      }
      return ws.send(JSON.stringify({ type: "CALL_END_ACK", ok: true, sessionId: msg.sessionId }));
    },
  };
};
