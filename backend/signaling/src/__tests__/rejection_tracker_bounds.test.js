"use strict";

// STX-17: rejectionTracker (REGISTER fork-protection rejects) must be bounded —
// oldest clientId evicted at the cap, existing entries keep counting.
// Run: node src/__tests__/rejection_tracker_bounds.test.js

process.env.REJECTION_TRACKER_MAX = "2";
process.env.ALLOWED_SIGNATURES = "good-sig";
process.env.FORK_PROTECTION_MODE = "enforce";

const assert = require("node:assert/strict");
const registerHandlers = require("../ws/handlers/register");

const rejectionTracker = new Map();
const ctx = {
  clients: new Map(),
  clientIds: new Map(),
  phoneNumbers: new Map(),
  phoneHashes: new Map(),
  fcmTokens: new Map(),
  rejectionTracker,
  getClientId: () => null,
  normalizePhone: () => "",
  hashPhone: () => "",
  saveFcmTokens: () => {},
  getIceServers: () => [],
  CLIENT_ID_REGEX: /^[a-zA-Z0-9_-]{1,64}$/,
  // Identity protocol v2 (#144): fork protection runs in IDENTITY_REGISTER_BEGIN.
  identityRegistry: { resolve: () => null, getIdentity: () => null },
  identityProtocolMode: "enforce",
};
const handlers = registerHandlers(ctx);

const idOf = name => `sc-${name.repeat(43).slice(0, 43)}`;

function rejectedRegister(clientId) {
  const ws = { sent: [], closed: false };
  ws.send = (data) => ws.sent.push(JSON.parse(data));
  ws.close = () => { ws.closed = true; };
  const id = idOf(clientId);
  handlers.IDENTITY_REGISTER_BEGIN(ws, "conn-test", {
    protocol: 2, clientId: id, identityId: id, publicKey: "synthetic", appSignature: "bad-sig",
  });
  return ws;
}

const a = rejectedRegister("aaa");
assert.equal(a.sent[0].error, "unauthorized_client", "bad signature still rejected");
assert.equal(rejectionTracker.get(idOf("aaa")).count, 1);

rejectedRegister("bbb");
assert.equal(rejectionTracker.size, 2, "tracker fills to the cap");

rejectedRegister("ccc");
assert.equal(rejectionTracker.size, 2, "cap holds under flood of unique clientIds");
assert.equal(rejectionTracker.has(idOf("aaa")), false, "oldest entry evicted first");
assert.ok(rejectionTracker.has(idOf("bbb")) && rejectionTracker.has(idOf("ccc")));

rejectedRegister("bbb");
assert.equal(rejectionTracker.get(idOf("bbb")).count, 2, "existing entries keep counting (no eviction on hit)");
assert.equal(rejectionTracker.size, 2);

delete process.env.REJECTION_TRACKER_MAX;
delete process.env.ALLOWED_SIGNATURES;
delete process.env.FORK_PROTECTION_MODE;

console.log("rejection_tracker_bounds.test.js: PASS");
