"use strict";

const assert = require("assert");
const crypto = require("crypto");
const { EventEmitter } = require("events");
const HeartbeatManager = require("../heartbeat");
const { setup } = require("../ws");
const callHandlers = require("../ws/handlers/call");
const { callInviteTranscript, parseIdentityPublicKey } = require("../security/identity_protocol");

function identity(scalar) {
  const d = Buffer.alloc(32);
  d[31] = scalar;
  const ecdh = crypto.createECDH("prime256v1");
  ecdh.setPrivateKey(d);
  const point = ecdh.getPublicKey();
  const jwk = { kty: "EC", crv: "P-256", d: d.toString("base64url"),
    x: point.subarray(1, 33).toString("base64url"),
    y: point.subarray(33).toString("base64url") };
  const privateKey = crypto.createPrivateKey({ key: jwk, format: "jwk" });
  const publicKey = crypto.createPublicKey(privateKey).export({ type: "spki", format: "der" });
  return { privateKey, ...parseIdentityPublicKey(publicKey.toString("base64url")) };
}

const aliceIdentity = identity(1);
const bobIdentity = identity(2);
const states = ["INVITE", "KEY_CONFIRM", "ACTIVE", "LEGACY_INVITE"];

function fixture(protocol, run) {
  const originalNow = Date.now;
  const originalInterval = global.setInterval;
  const originalLog = console.log;
  let now = 2_000_000_000_000;
  let tick;
  let sequence = 0;
  let writes = 0;
  const pendingCloses = [];
  const forwards = [];
  const pushes = [];
  const clients = new Map();
  const clientIds = new Map();
  const routingTable = new Map();
  const originalSet = routingTable.set;
  routingTable.set = function(key, value) { writes++; return originalSet.call(this, key, value); };
  const hb = new HeartbeatManager(routingTable, clients, clientIds);
  const ctx = {
    clients, clientIds, routingTable, hb, ipConnections: new Map(),
    fcmTokens: new Map(), phoneNumbers: new Map(),
    identityProtocolMode: protocol === 2 ? "enforce" : "transition",
    identityTransitionDeadline: 2_000_086_400,
    nowSeconds: () => Math.floor(now / 1000),
    fcm: { isInitialized: () => true,
      sendAuthenticatedCallInvitePush: async (...args) => { pushes.push(args); return true; },
      sendCallInvitePush: (...args) => { pushes.push(args); return true; } },
    customIds: { resolve: () => null }, normalizePhone: value => value,
    sanitize: value => value,
    getClientIp: () => "192.0.2.2",
    rateLimit: { registerEvent: () => true, registerBinaryEvent: () => true, clear: () => {} },
    forwardBinaryToPeer: () => { throw new Error("no media"); },
    getClientId: id => clients.get(id)?.clientId,
    getSessionPeer: (id, peer) => {
      const route = routingTable.get(id);
      return route?.from === peer ? route.to : route?.to === peer ? route.from : null;
    },
    sendToClient: (id, payload) => {
      forwards.push({ id, payload });
      const peer = clients.get(clientIds.get(id));
      if (!peer || peer.ws.readyState !== 1) return false;
      peer.ws.send(JSON.stringify(payload));
      return true;
    },
  };
  ctx.handlers = callHandlers(ctx);
  try {
    Date.now = () => now;
    global.setInterval = (callback, interval) => {
      assert.strictEqual(interval, 5000);
      tick = callback;
      return 1;
    };
    console.log = () => {};
    const wss = new EventEmitter();
    setup(wss, ctx);
    hb.start();
    function attach(material, legacyId) {
      const ws = new EventEmitter();
      Object.assign(ws, { readyState: 1, messages: [],
        send(value) { this.messages.push(JSON.parse(value)); },
        ping() {},
        terminate() {
          this.readyState = 2;
          pendingCloses.push(() => { this.readyState = 3; this.emit("close"); });
        },
      });
      wss.emit("connection", ws, {});
      const connId = [...clients].find(([, record]) => record.ws === ws)[0];
      const id = protocol === 2 ? material.identityId : legacyId;
      Object.assign(clients.get(connId), { clientId: id, identityId: material.identityId,
        identityPublicKey: material.publicKeyBase64Url, authVersion: protocol });
      clientIds.set(id, connId);
      return { ws, connId, id, material };
    }
    const alice = attach(aliceIdentity, "synthetic-alice");
    const bob = attach(bobIdentity, "synthetic-bob");
    const message = (peer, type, fields) => peer.ws.emit("message",
      Buffer.from(JSON.stringify({ type, ...fields })), false);
    function invite(peer = alice, to = bob.id, extra = {}) {
      sequence++;
      const sessionId = `00000000-0000-4000-8000-${String(sequence).padStart(12, "0")}`;
      const fields = { sessionId, fromClientId: peer.id, fromIdentityId: peer.material.identityId,
        to, ephemeralPublicKey: "a".repeat(43), issuedAt: ctx.nowSeconds(), nonce: "b".repeat(22) };
      const signature = crypto.sign("sha256", Buffer.from(callInviteTranscript(fields)),
        peer.material.privateKey).toString("base64url");
      message(peer, "CALL_INVITE", { protocol, sessionId, to,
        ephemeralPublicKey: fields.ephemeralPublicKey, pubKey: "synthetic-legacy-key",
        issuedAt: fields.issuedAt, nonce: fields.nonce, signature, ...extra });
      return peer.ws.messages.at(-1);
    }
    function seed(count, from, to, prefix = "seed") {
      for (let i = 0; i < count; i++) routingTable.set(`${prefix}-${i}`,
        Object.freeze({ from, to, state: states[i % states.length], updated: now }));
    }
    function denied(peer = alice, to = bob.id, extra = {}) {
      const entries = [...routingTable];
      const before = { forwards: forwards.length, pushes: pushes.length, writes,
        bobMessages: bob.ws.messages.length };
      assert.deepStrictEqual(invite(peer, to, extra), { type: "ERROR", error: "rate_limited" });
      assert.strictEqual(forwards.length, before.forwards, "denial forwarded to a peer");
      assert.strictEqual(pushes.length, before.pushes, "denial invoked a provider");
      assert.strictEqual(writes, before.writes, "denial wrote the routing table");
      assert.strictEqual(bob.ws.messages.length, before.bobMessages);
      assert.deepStrictEqual([...routingTable], entries, "existing routes changed or were evicted");
    }
    run({ ...ctx, alice, bob, invite, denied, seed, message, attach, forwards, pushes,
      advance: ms => { now += ms; tick(); },
      elapse: ms => { now += ms; },
      flushCloses: () => { while (pendingCloses.length) pendingCloses.shift()(); },
    });
  } finally {
    Date.now = originalNow;
    global.setInterval = originalInterval;
    console.log = originalLog;
  }
}

const cases = [
  ["global 10000 rejects without forward/push/write or eviction", f => {
    f.seed(10000, "synthetic-other", f.bob.id);
    f.fcmTokens.set(f.bob.id, "synthetic-token");
    f.denied();
  }],
  ["global 9999 admits exactly one and released global slot is reusable", f => {
    f.seed(9999, "synthetic-other", f.bob.id);
    const reply = f.invite();
    assert.strictEqual(reply.type, "CALL_INVITE_ACK");
    assert.strictEqual(f.routingTable.size, 10000);
    f.denied();
    f.message(f.alice, "CALL_END", { sessionId: reply.sessionId });
    assert.strictEqual(f.invite().type, "CALL_INVITE_ACK");
    assert.strictEqual(f.routingTable.size, 10000);
  }],
  ["all states count toward originator 32; spoofed from cannot bypass", f => {
    f.seed(32, f.alice.id, f.bob.id);
    f.fcmTokens.set(f.bob.id, "synthetic-token");
    f.denied(f.alice, f.bob.id, { from: "synthetic-unlimited" });
    f.message(f.bob, "CALL_BUSY", { sessionId: "seed-0" });
    assert.strictEqual(f.invite().type, "CALL_INVITE_ACK");
    assert.strictEqual(f.routingTable.size, 32);
    f.denied();
  }],
  ["originator 31 admits exactly one; END releases quota", f => {
    f.seed(31, f.alice.id, f.bob.id);
    const reply = f.invite();
    assert.strictEqual(reply.type, "CALL_INVITE_ACK");
    f.denied();
    f.message(f.alice, "CALL_END", { sessionId: reply.sessionId });
    assert.strictEqual(f.invite().type, "CALL_INVITE_ACK");
    assert.strictEqual(f.routingTable.size, 32);
  }],
  ["incoming pending calls never reserve recipient or forged-from quota", f => {
    f.seed(64, "synthetic-sender", f.bob.id);
    f.seed(32, "synthetic-forged-from", f.bob.id, "forged");
    f.seed(32, f.bob.id, "synthetic-target", "bob-originating");
    const reply = f.invite(f.alice, f.bob.id, { from: "synthetic-forged-from" });
    assert.strictEqual(reply.type, "CALL_INVITE_ACK");
    assert.strictEqual(f.routingTable.get(reply.sessionId).from, f.alice.id);
  }],
  ["capacity denial of offline target does not invoke push or mutate routes", f => {
    f.seed(32, f.alice.id, f.bob.id);
    const target = "synthetic-offline-target";
    f.fcmTokens.set(target, "synthetic-offline-token");
    f.denied(f.alice, target);
  }],
  ["offline push still creates a route below capacity", f => {
    const target = "synthetic-offline-target";
    f.fcmTokens.set(target, "synthetic-offline-token");
    const reply = f.invite(f.alice, target);
    assert.strictEqual(reply.type, "CALL_INVITE_ACK");
    assert.strictEqual(f.pushes.length, 1);
    assert.strictEqual(f.routingTable.size, 1);
  }],
  ["pending expiry frees originator quota", f => {
    for (let i = 0; i < 32; i++) assert.strictEqual(f.invite().type, "CALL_INVITE_ACK");
    f.denied();
    f.elapse(60001);
    f.alice.ws.emit("pong");
    f.bob.ws.emit("pong");
    f.advance(0);
    assert.strictEqual(f.routingTable.size, 0);
    assert.strictEqual(f.invite().type, "CALL_INVITE_ACK");
  }],
  ["superseded caller cannot originate or trigger side effects", f => {
    f.attach(aliceIdentity, "synthetic-alice");
    f.fcmTokens.set(f.bob.id, "synthetic-token");
    const reply = f.invite();
    assert.strictEqual(reply.type, "ERROR");
    assert(["not_authenticated", "not_registered"].includes(reply.error));
    assert.strictEqual(f.forwards.length, 0);
    assert.strictEqual(f.pushes.length, 0);
    assert.strictEqual(f.routingTable.size, 0);
  }],
  ["close releases quota, superseded close cannot release current routes", f => {
    for (let i = 0; i < 32; i++) assert.strictEqual(f.invite().type, "CALL_INVITE_ACK");
    const replacement = f.attach(aliceIdentity, "synthetic-alice");
    f.alice.ws.readyState = 3;
    f.alice.ws.emit("close");
    assert.strictEqual(f.routingTable.size, 32);
    f.denied(replacement);
    replacement.ws.readyState = 3;
    replacement.ws.emit("close");
    assert.strictEqual(f.routingTable.size, 0);
    const next = f.attach(aliceIdentity, "synthetic-alice");
    assert.strictEqual(f.invite(next).type, "CALL_INVITE_ACK");
  }],
];

let failures = 0;
for (const protocol of [2, 1]) {
  for (const [name, run] of cases) {
    try {
      fixture(protocol, run);
      console.log(`PASS protocol ${protocol}: ${name}`);
    } catch (error) {
      failures++;
      console.error(`FAIL protocol ${protocol}: ${name}: ${error.message}`);
    }
  }
}
console.log(`call_capacity.test.js: ${cases.length * 2 - failures}/${cases.length * 2} PASS`);
process.exitCode = failures ? 1 : 0;
