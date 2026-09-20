"use strict";

const crypto = require("crypto");
const {
  callInviteTranscript,
  callAcceptTranscript,
  parseIdentityPublicKey,
  transcriptDigest,
  validateIssuedAt,
  verifySignature,
  isLegacyTransitionActive,
  UUID,
} = require("../../security/identity_protocol");

module.exports = function callHandlers(ctx) {
  const {
    clients, clientIds, routingTable, fcmTokens, phoneNumbers,
    getClientId, getSessionPeer, sendToClient, normalizePhone, hashPhone,
    sanitize, fcm, customIds, identityRegistry,
  } = ctx;
  const protocolMode = ctx.identityProtocolMode || "enforce";
  const transitionDeadline = ctx.identityTransitionDeadline;
  const nowSeconds = ctx.nowSeconds || (() => Math.floor(Date.now() / 1000));

  function legacyTransitionActive() {
    return isLegacyTransitionActive(protocolMode, transitionDeadline, nowSeconds());
  }

  function respond(ws, fields) { ws.send(JSON.stringify(fields)); }
  function sendError(ws, code, message = code) {
    return respond(ws, { type: "ERROR", error: code, message });
  }

  function resolveTarget(requested) {
    let candidate = requested;
    try {
      const directIdentity = identityRegistry?.resolve(candidate);
      if (directIdentity) candidate = directIdentity;
    } catch { return null; }
    if (clientIds.has(candidate) || fcmTokens.has(candidate)) return candidate;

    const customDeviceId = customIds.resolve(requested);
    if (customDeviceId) candidate = customDeviceId;
    else {
      const phoneLookup = phoneNumbers.get(normalizePhone(requested));
      if (phoneLookup) candidate = phoneLookup;
    }
    try { return identityRegistry?.resolve(candidate) || candidate; }
    catch { return null; }
  }

  function authenticatedClient(connId) {
    const client = clients.get(connId);
    return client?.authVersion === 2 && client.clientId && client.identityId
      && client.identityPublicKey ? client : null;
  }

  function sendAuthenticatedPush(targetClientId, payload) {
    let token = fcmTokens.get(targetClientId);
    if (!token && identityRegistry) {
      try {
        for (const alias of identityRegistry.aliasesFor(targetClientId)) {
          token = fcmTokens.get(alias);
          if (token) break;
        }
      } catch { return false; }
    }
    if (!token || !fcm?.isInitialized?.()
        || typeof fcm.sendAuthenticatedCallInvitePush !== "function") return false;
    Promise.resolve(fcm.sendAuthenticatedCallInvitePush(token, payload))
      .catch(error => console.warn("[ROUTING] Authenticated invite push error:", error.message));
    return true;
  }

  function authenticatedInvite(ws, connId, msg) {
    const client = authenticatedClient(connId);
    if (!client) return sendError(ws, "not_authenticated");
    if (msg.protocol !== 2 || !UUID.test(msg.sessionId || "") || routingTable.has(msg.sessionId)
        || typeof msg.to !== "string" || typeof msg.ephemeralPublicKey !== "string"
        || typeof msg.nonce !== "string" || typeof msg.signature !== "string") {
      return sendError(ws, "invalid_call_invite");
    }
    if (!validateIssuedAt(msg.issuedAt, nowSeconds())) return sendError(ws, "stale_call_invite");

    let identity;
    let transcript;
    try {
      identity = parseIdentityPublicKey(client.identityPublicKey);
      if (identity.identityId !== client.identityId) throw new Error("identity mismatch");
      transcript = callInviteTranscript({
        sessionId: msg.sessionId,
        fromClientId: client.clientId,
        fromIdentityId: client.identityId,
        to: msg.to,
        ephemeralPublicKey: msg.ephemeralPublicKey,
        issuedAt: msg.issuedAt,
        nonce: msg.nonce,
      });
    } catch { return sendError(ws, "invalid_call_invite"); }
    if (!verifySignature(identity.publicKey, transcript, msg.signature)) {
      return sendError(ws, "invalid_call_signature");
    }

    const targetClientId = resolveTarget(msg.to);
    if (!targetClientId) return sendError(ws, "identity_service_unavailable");
    const targetConnId = clientIds.get(targetClientId);
    const target = targetConnId ? clients.get(targetConnId) : null;
    if (target && target.authVersion !== 2) return sendError(ws, "peer_identity_upgrade_required");

    const inviteDigest = transcriptDigest(transcript);
    const callerPhone = typeof msg.callerPhone === "string" && msg.callerPhone.length <= 64
      ? msg.callerPhone : "";
    const payload = {
      type: "CALL_INVITE",
      protocol: 2,
      sessionId: msg.sessionId,
      from: client.clientId,
      fromIdentityId: client.identityId,
      to: targetClientId,
      requestedTo: msg.to,
      ephemeralPublicKey: msg.ephemeralPublicKey,
      identityPublicKey: identity.publicKeyBase64Url,
      issuedAt: msg.issuedAt,
      nonce: msg.nonce,
      signature: msg.signature,
      inviteDigest,
      callerPhone,
    };
    const wsDelivered = target?.authVersion === 2 ? sendToClient(targetClientId, payload) : false;
    const pushSent = sendAuthenticatedPush(targetClientId,
      Object.fromEntries(Object.entries(payload).map(([key, value]) => [key, String(value)])));
    if (!wsDelivered && !pushSent) {
      return sendError(ws, "peer_not_found", `Client '${sanitize(msg.to)}' is not online`);
    }

    routingTable.set(msg.sessionId, {
      sessionId: msg.sessionId,
      from: client.clientId,
      fromIdentityId: client.identityId,
      to: targetClientId,
      requestedTo: msg.to,
      state: "INVITE",
      inviteDigest,
      callerEphemeralPublicKey: msg.ephemeralPublicKey,
      callerConfirmed: false,
      calleeConfirmed: false,
      created: Date.now(),
      updated: Date.now(),
    });
    return respond(ws, {
      type: "CALL_INVITE_ACK",
      protocol: 2,
      ok: true,
      sessionId: msg.sessionId,
      from: client.clientId,
      to: targetClientId,
      pushSent,
    });
  }

  function authenticatedAccept(ws, connId, msg) {
    const client = authenticatedClient(connId);
    if (!client) return sendError(ws, "not_authenticated");
    const session = routingTable.get(msg.sessionId);
    if (!session) return sendError(ws, "session_not_found");
    if (session.to !== client.clientId) return sendError(ws, "not_callee");
    if (session.state !== "INVITE") return sendError(ws, "invalid_session_state");
    if (msg.protocol !== 2 || msg.inviteDigest !== session.inviteDigest
        || msg.toIdentityId !== session.fromIdentityId || typeof msg.ephemeralPublicKey !== "string"
        || typeof msg.nonce !== "string" || typeof msg.signature !== "string"
        || !validateIssuedAt(msg.issuedAt, nowSeconds())) return sendError(ws, "invalid_call_accept");

    let identity;
    let transcript;
    try {
      identity = parseIdentityPublicKey(client.identityPublicKey);
      transcript = callAcceptTranscript({
        sessionId: msg.sessionId,
        inviteDigest: msg.inviteDigest,
        fromClientId: client.clientId,
        fromIdentityId: client.identityId,
        toIdentityId: msg.toIdentityId,
        ephemeralPublicKey: msg.ephemeralPublicKey,
        issuedAt: msg.issuedAt,
        nonce: msg.nonce,
      });
    } catch { return sendError(ws, "invalid_call_accept"); }
    if (identity.identityId !== client.identityId
        || !verifySignature(identity.publicKey, transcript, msg.signature)) {
      return sendError(ws, "invalid_call_signature");
    }

    session.state = "KEY_CONFIRM";
    session.calleeIdentityId = client.identityId;
    session.calleeEphemeralPublicKey = msg.ephemeralPublicKey;
    session.acceptDigest = transcriptDigest(transcript);
    session.updated = Date.now();
    respond(ws, { type: "CALL_ACCEPT_ACK", protocol: 2, ok: true, sessionId: msg.sessionId });
    return sendToClient(session.from, {
      type: "CALL_ACCEPT",
      protocol: 2,
      sessionId: msg.sessionId,
      inviteDigest: msg.inviteDigest,
      acceptDigest: session.acceptDigest,
      from: client.clientId,
      fromIdentityId: client.identityId,
      toIdentityId: msg.toIdentityId,
      ephemeralPublicKey: msg.ephemeralPublicKey,
      identityPublicKey: identity.publicKeyBase64Url,
      issuedAt: msg.issuedAt,
      nonce: msg.nonce,
      signature: msg.signature,
    });
  }

  function keyConfirm(ws, connId, msg) {
    const client = authenticatedClient(connId);
    if (!client) return sendError(ws, "not_authenticated");
    const session = routingTable.get(msg.sessionId);
    if (!session) return sendError(ws, "session_not_found");
    if (session.state !== "KEY_CONFIRM" && session.state !== "ACTIVE") {
      return sendError(ws, "invalid_session_state");
    }
    const role = session.from === client.clientId ? "caller"
      : session.to === client.clientId ? "callee" : null;
    if (!role || msg.protocol !== 2 || msg.role !== role
        || typeof msg.confirmation !== "string" || !/^[A-Za-z0-9_-]{43}$/.test(msg.confirmation)) {
      return sendError(ws, "invalid_key_confirmation");
    }
    const field = role === "caller" ? "callerConfirmed" : "calleeConfirmed";
    if (session[field]) return sendError(ws, "key_confirmation_replayed");
    session[field] = true;
    session.updated = Date.now();
    const peer = getSessionPeer(msg.sessionId, client.clientId);
    if (peer) sendToClient(peer, {
      type: "CALL_KEY_CONFIRM",
      protocol: 2,
      sessionId: msg.sessionId,
      role,
      confirmation: msg.confirmation,
    });
    if (session.callerConfirmed && session.calleeConfirmed) session.state = "ACTIVE";
    return respond(ws, { type: "CALL_KEY_CONFIRM_ACK", protocol: 2, ok: true,
      sessionId: msg.sessionId, active: session.state === "ACTIVE" });
  }

  function legacyInvite(ws, connId, msg) {
    if (!legacyTransitionActive()) return sendError(ws, "identity_protocol_required");
    const myClientId = getClientId(connId);
    if (!myClientId) return sendError(ws, "not_registered");
    if (!msg.to) return sendError(ws, "missing_to");
    const sessionId = crypto.randomUUID();
    const targetClientId = resolveTarget(msg.to);
    if (!targetClientId) return sendError(ws, "identity_service_unavailable");
    const targetConnId = clientIds.get(targetClientId);
    const target = targetConnId ? clients.get(targetConnId) : null;
    if (target?.authVersion === 2 || identityRegistry?.resolve(msg.to)) {
      return sendError(ws, "peer_identity_upgrade_required");
    }
    const fcmToken = fcmTokens.get(targetClientId) || fcmTokens.get(msg.to);
    if (!target && !(fcmToken && fcm.isInitialized())) {
      return sendError(ws, "peer_not_found", `Client '${sanitize(msg.to)}' is not online`);
    }
    routingTable.set(sessionId, { sessionId, from: myClientId, to: targetClientId,
      state: "LEGACY_INVITE", created: Date.now(), updated: Date.now() });
    respond(ws, { type: "CALL_INVITE_ACK", ok: true, sessionId, from: myClientId, to: targetClientId });
    if (target) sendToClient(targetClientId, { type: "CALL_INVITE", sessionId, from: myClientId,
      to: targetClientId, pubKey: msg.pubKey, callerPhone: msg.callerPhone || "" });
    if (fcmToken && fcm.isInitialized()) fcm.sendCallInvitePush(fcmToken, sessionId, myClientId, msg.callerPhone || "");
  }

  function legacyAccept(ws, connId, msg) {
    if (!legacyTransitionActive()) return sendError(ws, "identity_protocol_required");
    const myClientId = getClientId(connId);
    const session = routingTable.get(msg.sessionId);
    if (!session) return sendError(ws, "session_not_found");
    if (session.to !== myClientId) return sendError(ws, "not_callee");
    if (session.state !== "LEGACY_INVITE") return sendError(ws, "invalid_session_state");
    session.state = "ACTIVE";
    session.updated = Date.now();
    respond(ws, { type: "CALL_ACCEPT_ACK", ok: true, sessionId: msg.sessionId });
    return sendToClient(session.from, { type: "CALL_ACCEPT", sessionId: msg.sessionId,
      from: myClientId, pubKey: msg.pubKey });
  }

  return {
    CALL_INVITE(ws, connId, msg) {
      return authenticatedClient(connId) ? authenticatedInvite(ws, connId, msg)
        : legacyInvite(ws, connId, msg);
    },
    CALL_ACCEPT(ws, connId, msg) {
      return authenticatedClient(connId) ? authenticatedAccept(ws, connId, msg)
        : legacyAccept(ws, connId, msg);
    },
    CALL_KEY_CONFIRM: keyConfirm,

    CALL_BUSY(ws, connId, msg) {
      const myClientId = getClientId(connId);
      const session = routingTable.get(msg.sessionId);
      if (session && session.to === myClientId) {
        sendToClient(session.from, { type: "CALL_BUSY", sessionId: msg.sessionId, from: myClientId });
        routingTable.delete(msg.sessionId);
      }
    },

    CALL_END(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (msg.sessionId && routingTable.has(msg.sessionId)) {
        const session = routingTable.get(msg.sessionId);
        if (session.from !== myClientId && session.to !== myClientId) {
          return sendError(ws, "not_participant");
        }
        const peerClientId = getSessionPeer(msg.sessionId, myClientId);
        if (peerClientId) sendToClient(peerClientId, { type: "CALL_END",
          sessionId: msg.sessionId, from: myClientId, reason: "user_hangup" });
        routingTable.delete(msg.sessionId);
      }
      return respond(ws, { type: "CALL_END_ACK", ok: true, sessionId: msg.sessionId });
    },
  };
};
