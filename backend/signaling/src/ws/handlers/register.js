"use strict";

const crypto = require("crypto");
const {
  CLIENT_ID,
  IDENTITY_ID,
  parseIdentityPublicKey,
  registrationTranscript,
  isLegacyTransitionActive,
  verifySignature,
} = require("../../security/identity_protocol");

module.exports = function registerHandlers(ctx) {
  const {
    clients, clientIds, phoneNumbers, phoneHashes, fcmTokens,
    rejectionTracker, getClientId, normalizePhone, hashPhone,
    saveFcmTokens, getIceServers, identityRegistry, identityMigrationRoutes, fcm,
  } = ctx;
  const protocolMode = ctx.identityProtocolMode || "enforce";
  const transitionDeadline = ctx.identityTransitionDeadline;
  const nowSeconds = ctx.nowSeconds || (() => Math.floor(Date.now() / 1000));
  const challenges = new Map();
  const maxChallengesPerIp = 4;

  function legacyTransitionActive() {
    return isLegacyTransitionActive(protocolMode, transitionDeadline, nowSeconds());
  }

  function respond(ws, fields) { ws.send(JSON.stringify(fields)); }
  function sendError(ws, code, message = code) {
    return respond(ws, { type: "ERROR", error: code, message });
  }

  function validateAppSignature(ws, clientId, appSignature) {
    const allowedSigs = process.env.ALLOWED_SIGNATURES;
    if (!allowedSigs || allowedSigs.trim().length === 0) return true;
    const allowed = allowedSigs.split(",").map(value => value.trim().toLowerCase());
    const clientSig = typeof appSignature === "string" ? appSignature.toLowerCase() : "";
    if (clientSig && allowed.includes(clientSig)) return true;
    const forkMode = (process.env.FORK_PROTECTION_MODE || "warn").toLowerCase();
    if (forkMode !== "enforce") {
      console.warn("[REGISTER] WARN - unknown signature for", clientId, "(warn mode)");
      return true;
    }
    const tracker = rejectionTracker.get(clientId) || { count: 0, firstSeen: Date.now(), lastLogged: 0 };
    tracker.count++;
    rejectionTracker.set(clientId, tracker);
    if (tracker.count === 1 || tracker.count % 50 === 0) {
      console.log("[REGISTER] REJECTED - unauthorized app signature for", clientId,
        `(attempt #${tracker.count})`);
    }
    sendError(ws, "unauthorized_client", "App signature not authorized");
    ws.close(4003, "Unauthorized client");
    return false;
  }

  function removePreviousConnectionState(client, nextClientId) {
    if (!client.clientId) return;
    if (clientIds.get(client.clientId) === client.connId) clientIds.delete(client.clientId);
    if (client.phoneNumber
        && (phoneNumbers.get(client.phoneNumber) === client.clientId
          || phoneNumbers.get(client.phoneNumber) === nextClientId)) {
      phoneNumbers.delete(client.phoneNumber);
      phoneHashes.delete(hashPhone(client.phoneNumber));
    }
  }

  function finishRegistration(ws, connId, {
    clientId, identityId = null, publicKey = null, phoneNumber = null, legacyAlias = null,
    authVersion = 2,
  }) {
    const client = clients.get(connId);
    if (!client) return sendError(ws, "connection_not_found");

    const existingConnId = clientIds.get(clientId);
    if (existingConnId && existingConnId !== connId) {
      const oldClient = clients.get(existingConnId);
      if (oldClient) {
        try { oldClient.ws.close(1000, "Authenticated session superseded"); } catch { /* ignored */ }
      }
      clientIds.delete(clientId);
    }

    client.connId = connId;
    removePreviousConnectionState(client, clientId);
    client.clientId = clientId;
    client.identityId = identityId;
    client.identityPublicKey = publicKey;
    client.authVersion = authVersion;
    clientIds.set(clientId, connId);

    const phone = normalizePhone(phoneNumber);
    if (phone.length >= 4) {
      for (const [existingPhone, mappedClientId] of phoneNumbers) {
        if (mappedClientId === clientId && existingPhone !== phone) {
          phoneNumbers.delete(existingPhone);
          phoneHashes.delete(hashPhone(existingPhone));
          break;
        }
      }
      phoneNumbers.set(phone, clientId);
      phoneHashes.set(hashPhone(phone), clientId);
      client.phoneNumber = phone;
    }

    console.log("[REGISTER] authenticated v" + authVersion, clientId, "->", connId);
    return respond(ws, {
      type: "REGISTERED",
      protocol: authVersion,
      clientId,
      identityId,
      ...(legacyAlias ? { legacyAlias } : {}),
      iceServers: getIceServers(clientId),
    });
  }

  function pruneChallenges() {
    const now = nowSeconds();
    for (const [id, pending] of challenges) {
      if (pending.expiresAt <= now) challenges.delete(id);
    }
  }

  function clearChallengesForConnection(connId) {
    for (const [id, pending] of challenges) {
      if (pending.connId === connId) challenges.delete(id);
    }
  }

  function createChallenge(connId, fields, migration) {
    pruneChallenges();
    const ipKey = clients.get(connId)?.ip || `connection:${connId}`;
    let pendingForIp = 0;
    for (const pending of challenges.values()) {
      if (pending.ipKey === ipKey) pendingForIp++;
    }
    if (challenges.size >= 1024 || pendingForIp >= maxChallengesPerIp) {
      const error = new Error("identity_challenge_rate_limited");
      error.code = "identity_challenge_rate_limited";
      throw error;
    }
    const challengeId = crypto.randomUUID();
    const nonce = crypto.randomBytes(32).toString("base64url");
    const expiresAt = nowSeconds() + 300;
    const challenge = registrationTranscript({
      challengeId,
      requestedClientId: fields.requestedClientId,
      identityId: fields.identityId,
      keyHash: fields.keyHash,
      expiresAt,
      nonce,
    });
    const pending = { ...fields, connId, ipKey, migration, challengeId, challenge, expiresAt };
    challenges.set(challengeId, pending);
    return pending;
  }

  function beginIdentityRegistration(ws, connId, msg) {
    if (!identityRegistry) return sendError(ws, "identity_service_unavailable");
    if (msg.protocol !== 2 || !CLIENT_ID.test(msg.clientId || "")
        || !IDENTITY_ID.test(msg.identityId || "") || typeof msg.publicKey !== "string") {
      return sendError(ws, "invalid_identity_registration");
    }
    if (!validateAppSignature(ws, msg.clientId, msg.appSignature)) return;

    let parsed;
    try { parsed = parseIdentityPublicKey(msg.publicKey); }
    catch { return sendError(ws, "invalid_identity_public_key"); }
    if (parsed.identityId !== msg.identityId) return sendError(ws, "identity_key_mismatch");

    let resolved;
    let registered;
    try {
      resolved = identityRegistry.resolve(msg.clientId);
      registered = identityRegistry.getIdentity(msg.identityId);
    } catch {
      return sendError(ws, "identity_service_unavailable");
    }
    if (resolved && resolved !== msg.identityId) return sendError(ws, "identity_alias_conflict");
    if (registered && registered.publicKey !== parsed.publicKeyBase64Url) {
      return sendError(ws, "identity_key_mismatch");
    }

    const fields = {
      requestedClientId: msg.clientId,
      identityId: msg.identityId,
      keyHash: parsed.keyHash,
      publicKey: parsed.publicKeyBase64Url,
      publicKeyObject: parsed.publicKey,
      phoneNumber: typeof msg.phoneNumber === "string" ? msg.phoneNumber : "",
    };
    // A connection gets one live proof at a time. Reissuing invalidates the
    // previous nonce and prevents a single socket from filling the challenge map.
    clearChallengesForConnection(connId);
    const needsMigration = msg.clientId !== msg.identityId && !resolved;
    if (!needsMigration) {
      let pending;
      try { pending = createChallenge(connId, fields, false); }
      catch (error) {
        return sendError(ws, error?.code === "identity_challenge_rate_limited"
          ? error.code : "identity_service_unavailable");
      }
      return respond(ws, {
        type: "IDENTITY_REGISTER_CHALLENGE",
        protocol: 2,
        challengeId: pending.challengeId,
        challenge: pending.challenge,
        expiresAt: pending.expiresAt,
      });
    }

    const token = identityMigrationRoutes?.get?.(msg.clientId);
    if (!token || !fcm?.isInitialized?.() || typeof fcm.sendIdentityMigrationChallenge !== "function") {
      return respond(ws, {
        type: "IDENTITY_MIGRATION_REQUIRED",
        protocol: 2,
        clientId: msg.clientId,
        canonicalClientId: msg.identityId,
        reason: "trusted_legacy_route_unavailable",
      });
    }

    let pending;
    try {
      pending = createChallenge(connId, {
        ...fields,
        fcmTokenHash: crypto.createHash("sha256").update(token).digest("base64url"),
      }, true);
    } catch (error) {
      return sendError(ws, error?.code === "identity_challenge_rate_limited"
        ? error.code : "identity_service_unavailable");
    }

    Promise.resolve(fcm.sendIdentityMigrationChallenge(token, {
      challengeId: pending.challengeId,
      challenge: pending.challenge,
      requestedClientId: pending.requestedClientId,
      identityId: pending.identityId,
      expiresAt: String(pending.expiresAt),
    })).then(delivered => {
      if (!challenges.has(pending.challengeId)) return;
      if (!delivered) {
        challenges.delete(pending.challengeId);
        return respond(ws, {
          type: "IDENTITY_MIGRATION_REQUIRED",
          protocol: 2,
          clientId: msg.clientId,
          canonicalClientId: msg.identityId,
          reason: "trusted_legacy_route_unavailable",
        });
      }
      return respond(ws, {
        type: "IDENTITY_MIGRATION_PENDING",
        protocol: 2,
        clientId: msg.clientId,
        identityId: msg.identityId,
        expiresAt: pending.expiresAt,
      });
    }).catch(() => {
      challenges.delete(pending.challengeId);
      sendError(ws, "identity_service_unavailable");
    });
  }

  function completeIdentityRegistration(ws, connId, msg) {
    const pending = challenges.get(msg.challengeId);
    if (pending) challenges.delete(msg.challengeId);
    if (!pending || pending.connId !== connId || pending.expiresAt <= nowSeconds()
        || !verifySignature(pending.publicKeyObject, pending.challenge, msg.signature)) {
      return sendError(ws, "invalid_identity_proof");
    }
    if (pending.migration) {
      const token = identityMigrationRoutes?.get?.(pending.requestedClientId);
      const tokenHash = token && crypto.createHash("sha256").update(token).digest("base64url");
      if (!tokenHash || tokenHash !== pending.fcmTokenHash) return sendError(ws, "invalid_identity_proof");
    }

    // Persist the canonical push route before the alias. A crash or storage
    // failure can therefore never leave a durable alias without a durable
    // authenticated route. The immutable legacy route remains available for a
    // retry and is never sourced from runtime REGISTER_FCM_TOKEN messages.
    const migrationToken = pending.migration
      ? identityMigrationRoutes?.get?.(pending.requestedClientId) : null;
    const previousCanonicalToken = pending.migration ? fcmTokens.get(pending.identityId) : null;
    if (pending.migration) {
      if (!migrationToken) return sendError(ws, "invalid_identity_proof");
      fcmTokens.set(pending.identityId, migrationToken);
      if (saveFcmTokens() === false) {
        if (previousCanonicalToken) fcmTokens.set(pending.identityId, previousCanonicalToken);
        else fcmTokens.delete(pending.identityId);
        return sendError(ws, "identity_service_unavailable");
      }
    }

    let binding;
    try {
      binding = identityRegistry.bind({
        requestedClientId: pending.requestedClientId,
        identityId: pending.identityId,
        publicKey: pending.publicKey,
        migrationAuthorized: pending.migration,
      });
    } catch (registryError) {
      const code = registryError?.code;
      if (["identity_key_mismatch", "identity_alias_conflict", "identity_migration_required"].includes(code)) {
        return sendError(ws, code);
      }
      return sendError(ws, "identity_service_unavailable");
    }

    if (pending.migration && pending.requestedClientId !== pending.identityId) {
      fcmTokens.delete(pending.requestedClientId);
      // The canonical token was already persisted before alias binding. If
      // cleanup fails, restart may temporarily retain both routes, but calls
      // resolve the alias to the authenticated canonical identity first.
      saveFcmTokens();
    }
    return finishRegistration(ws, connId, {
      clientId: pending.identityId,
      identityId: pending.identityId,
      publicKey: binding.publicKey,
      phoneNumber: pending.phoneNumber,
      legacyAlias: binding.migratedAlias,
      authVersion: 2,
    });
  }

  return {
    IDENTITY_REGISTER_BEGIN: beginIdentityRegistration,
    IDENTITY_REGISTER_COMPLETE: completeIdentityRegistration,

    REGISTER(ws, connId, msg) {
      if (!legacyTransitionActive()) {
        sendError(ws, "identity_protocol_required", "Authenticated registration protocol v2 is required");
        return ws.close(4003, "Identity protocol required");
      }
      if (!msg.clientId || typeof msg.clientId !== "string") return sendError(ws, "missing_client_id");
      if (!CLIENT_ID.test(msg.clientId)) return sendError(ws, "invalid_client_id");
      if (!validateAppSignature(ws, msg.clientId, msg.appSignature)) return;
      if (clientIds.has(msg.clientId) && clientIds.get(msg.clientId) !== connId) {
        return sendError(ws, "duplicate_client_id", "Legacy identity is already online");
      }
      return finishRegistration(ws, connId, {
        clientId: msg.clientId,
        phoneNumber: msg.phoneNumber,
        authVersion: 1,
      });
    },

    DEREGISTER(ws, connId) {
      const myClientId = getClientId(connId);
      if (!myClientId) return sendError(ws, "not_registered", "Must be registered to deregister");
      for (const [phone, clientId] of phoneNumbers) {
        if (clientId === myClientId) {
          phoneNumbers.delete(phone);
          phoneHashes.delete(hashPhone(phone));
        }
      }
      if (clientIds.get(myClientId) === connId) clientIds.delete(myClientId);
      const client = clients.get(connId);
      if (client) {
        client.clientId = null;
        client.identityId = null;
        client.identityPublicKey = null;
        client.authVersion = null;
        client.phoneNumber = null;
      }
      return respond(ws, { type: "DEREGISTER_ACK", ok: true });
    },

    REGISTER_FCM_TOKEN(ws, connId, msg) {
      const myClientId = getClientId(connId);
      const client = clients.get(connId);
      if (!myClientId || !client?.authVersion) {
        return sendError(ws, "not_authenticated", "Authenticated registration is required");
      }
      if (client.authVersion !== 2) {
        return sendError(ws, "identity_protocol_required",
          "Authenticated registration protocol v2 is required for FCM updates");
      }
      if (typeof msg.fcmToken !== "string" || msg.fcmToken.length < 20 || msg.fcmToken.length > 4096) {
        return sendError(ws, "invalid_fcm_token");
      }
      fcmTokens.set(myClientId, msg.fcmToken);
      saveFcmTokens();
      return respond(ws, { type: "REGISTER_FCM_TOKEN_ACK", ok: true });
    },
  };
};
