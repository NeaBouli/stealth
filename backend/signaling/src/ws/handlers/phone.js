"use strict";

module.exports = function phoneHandlers(ctx) {
  const { clients, clientIds, phoneNumbers, phoneHashes, getClientId, normalizePhone } = ctx;

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

      if (Array.isArray(msg.hashes)) {
        const results = msg.hashes.slice(0, 200).map(hash => {
          const resolvedClientId = phoneHashes.get(hash) || null;
          const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;
          return { hash, clientId: resolvedClientId, online };
        });
        return ws.send(JSON.stringify({ type: "BATCH_PHONE_LOOKUP_RESULT", mode: "hashed", results }));
      }

      const phoneList = Array.isArray(msg.phoneNumbers) ? msg.phoneNumbers : [];
      const results = phoneList.slice(0, 200).map(phone => {
        const normalized = normalizePhone(phone);
        const resolvedClientId = phoneNumbers.get(normalized) || null;
        const online = resolvedClientId ? clientIds.has(resolvedClientId) : false;
        return { phoneNumber: phone, clientId: resolvedClientId, online };
      });
      return ws.send(JSON.stringify({ type: "BATCH_PHONE_LOOKUP_RESULT", results }));
    },
  };
};
