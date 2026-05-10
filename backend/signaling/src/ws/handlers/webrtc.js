"use strict";

const crypto = require("crypto");

module.exports = function webrtcHandlers(ctx) {
  const { routingTable, getClientId, getSessionPeer, sendToClient, getIceServers } = ctx;

  return {
    WEBRTC_OFFER(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) return ws.send(JSON.stringify({ type: "ERROR", error: "not_registered" }));
      if (!msg.sessionId || !routingTable.has(msg.sessionId)) return ws.send(JSON.stringify({ type: "ERROR", error: "session_not_found" }));
      if (!msg.sdp) return ws.send(JSON.stringify({ type: "ERROR", error: "missing_sdp", message: "Field 'sdp' is required for WEBRTC_OFFER" }));
      if (typeof msg.sdp !== "string" || msg.sdp.length > 10000) return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_sdp" }));

      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, { type: "WEBRTC_OFFER", sessionId: msg.sessionId, from: myClientId, sdp: msg.sdp });
        console.log("[WEBRTC] OFFER:", myClientId, "->", peerClientId);
      }
      return ws.send(JSON.stringify({ type: "WEBRTC_OFFER_ACK", ok: true, sessionId: msg.sessionId }));
    },

    WEBRTC_ANSWER(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) return ws.send(JSON.stringify({ type: "ERROR", error: "not_registered" }));
      if (!msg.sessionId || !routingTable.has(msg.sessionId)) return ws.send(JSON.stringify({ type: "ERROR", error: "session_not_found" }));
      if (!msg.sdp) return ws.send(JSON.stringify({ type: "ERROR", error: "missing_sdp", message: "Field 'sdp' is required for WEBRTC_ANSWER" }));
      if (typeof msg.sdp !== "string" || msg.sdp.length > 10000) return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_sdp" }));

      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, { type: "WEBRTC_ANSWER", sessionId: msg.sessionId, from: myClientId, sdp: msg.sdp });
        console.log("[WEBRTC] ANSWER:", myClientId, "->", peerClientId);
      }
      return ws.send(JSON.stringify({ type: "WEBRTC_ANSWER_ACK", ok: true, sessionId: msg.sessionId }));
    },

    ICE_CANDIDATE(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) return ws.send(JSON.stringify({ type: "ERROR", error: "not_registered" }));
      if (!msg.sessionId || !routingTable.has(msg.sessionId)) return ws.send(JSON.stringify({ type: "ERROR", error: "session_not_found" }));
      if (!msg.candidate) return ws.send(JSON.stringify({ type: "ERROR", error: "missing_candidate", message: "Field 'candidate' is required for ICE_CANDIDATE" }));
      if (typeof msg.candidate !== "object" && typeof msg.candidate !== "string") return ws.send(JSON.stringify({ type: "ERROR", error: "invalid_candidate" }));

      const peerClientId = getSessionPeer(msg.sessionId, myClientId);
      if (peerClientId) {
        sendToClient(peerClientId, { type: "ICE_CANDIDATE", sessionId: msg.sessionId, from: myClientId, candidate: msg.candidate });
      }
      return ws.send(JSON.stringify({ type: "ICE_CANDIDATE_ACK", ok: true, sessionId: msg.sessionId }));
    },

    GHOST_PREPARE(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "not_registered", message: "You must REGISTER before sending GHOST_PREPARE" }));
      }
      if (!msg.sessionId || !routingTable.has(msg.sessionId)) {
        return ws.send(JSON.stringify({ type: "ERROR", error: "session_not_found" }));
      }
      console.log("[GHOST] PREPARE received for session:", msg.sessionId);
      const ghostNetId = crypto.randomUUID();
      ws.send(JSON.stringify({
        type: "GHOST_ACK",
        sessionId: msg.sessionId,
        ghostNetId,
        iceServers: getIceServers(myClientId),
        relayHints: [
          { host: "relay1.securecall.local", port: 443 },
          { host: "relay2.securecall.local", port: 8443 }
        ]
      }));
      console.log("[GHOST] ACK sent:", ghostNetId);
    },
  };
};
