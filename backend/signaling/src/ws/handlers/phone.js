"use strict";

module.exports = function phoneHandlers(ctx) {
  const { clients, clientIds, phoneNumbers, phoneHashes, getClientId, normalizePhone } = ctx;
  // STX-04: the per-connection minute limits reset on reconnect, so contact discovery could
  // enumerate the number space. A daily budget per client id and per peer address survives
  // reconnects; it is sized for a large address book sync, not for enumeration.
  const dayMs = 24 * 60 * 60 * 1000;
  const maxNumbersPerClientPerDay = 5000;
  const maxNumbersPerAddressPerDay = 10000;
  const maxTrackedKeys = 65536;
  const lookupBudget = new Map();

  function budgetKeys(connId) {
    const keys = [[`client:${getClientId(connId)}`, maxNumbersPerClientPerDay]];
    const ip = clients.get(connId)?.ip;
    if (typeof ip === "string" && ip) keys.push([`ip:${ip}`, maxNumbersPerAddressPerDay]);
    return keys;
  }

  // Reserves `count` lookups for this connection or returns false without reserving any.
  function reserveLookups(connId, count) {
    const now = Date.now();
    for (const [key, entry] of lookupBudget) {
      if (now - entry.windowStart >= dayMs) lookupBudget.delete(key);
    }
    const keys = budgetKeys(connId);
    for (const [key, limit] of keys) {
      const used = lookupBudget.get(key)?.count || 0;
      if (used + count > limit) return false;
      if (!lookupBudget.has(key) && lookupBudget.size >= maxTrackedKeys) return false;
    }
    for (const [key] of keys) {
      const entry = lookupBudget.get(key);
      if (entry) entry.count += count;
      else lookupBudget.set(key, { windowStart: now, count });
    }
    return true;
  }

  return {
    PHONE_LOOKUP(ws, connId, msg) {
      if (!getClientId(connId)) {
        return ws.send(JSON.stringify({ type: "PHONE_LOOKUP_RESULT", error: "not_registered" }));
      }
      if (!clients.get(connId)._phoneLookups) clients.get(connId)._phoneLookups = [];
      const lookups = clients.get(connId)._phoneLookups;
      const now = Date.now();
      while (lookups.length > 0 && now - lookups[0] > 60000) lookups.shift();
      if (lookups.length >= 10) {
        return ws.send(JSON.stringify({ type: "PHONE_LOOKUP_RESULT", phoneNumber: msg.phoneNumber || "", clientId: null, online: false, error: "rate_limited" }));
      }
      lookups.push(now);

      if (!msg.phoneNumber || typeof msg.phoneNumber !== "string") {
        return ws.send(JSON.stringify({ type: "PHONE_LOOKUP_RESULT", phoneNumber: "", clientId: null, online: false }));
      }

      if (!reserveLookups(connId, 1)) {
        return ws.send(JSON.stringify({ type: "PHONE_LOOKUP_RESULT", phoneNumber: msg.phoneNumber, clientId: null, online: false, error: "rate_limited" }));
      }
      const normalized = normalizePhone(msg.phoneNumber);
      const resolvedClientId = phoneNumbers.get(normalized) || null;
      const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;
      return ws.send(JSON.stringify({ type: "PHONE_LOOKUP_RESULT", phoneNumber: msg.phoneNumber, clientId: resolvedClientId, online }));
    },

    BATCH_PHONE_LOOKUP(ws, connId, msg) {
      if (!getClientId(connId)) {
        return ws.send(JSON.stringify({ type: "BATCH_PHONE_LOOKUP_RESULT", results: [], error: "not_registered" }));
      }
      if (!clients.get(connId)._batchLookups) clients.get(connId)._batchLookups = [];
      const batchLookups = clients.get(connId)._batchLookups;
      const bNow = Date.now();
      while (batchLookups.length > 0 && bNow - batchLookups[0] > 60000) batchLookups.shift();
      if (batchLookups.length >= 5) {
        return ws.send(JSON.stringify({ type: "BATCH_PHONE_LOOKUP_RESULT", results: [], error: "rate_limited" }));
      }
      batchLookups.push(bNow);

      const requested = Array.isArray(msg.hashes) ? msg.hashes.slice(0, 200)
        : (Array.isArray(msg.phoneNumbers) ? msg.phoneNumbers.slice(0, 200) : []);
      if (!reserveLookups(connId, requested.length)) {
        return ws.send(JSON.stringify({ type: "BATCH_PHONE_LOOKUP_RESULT", results: [], error: "rate_limited" }));
      }

      if (Array.isArray(msg.hashes)) {
        const results = requested.map(hash => {
          const resolvedClientId = phoneHashes.get(hash) || null;
          const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;
          return { hash, clientId: resolvedClientId, online };
        });
        return ws.send(JSON.stringify({ type: "BATCH_PHONE_LOOKUP_RESULT", mode: "hashed", results }));
      }

      const results = requested.map(phone => {
        const normalized = normalizePhone(phone);
        const resolvedClientId = phoneNumbers.get(normalized) || null;
        const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;
        return { phoneNumber: phone, clientId: resolvedClientId, online };
      });
      return ws.send(JSON.stringify({ type: "BATCH_PHONE_LOOKUP_RESULT", results }));
    },
  };
};
