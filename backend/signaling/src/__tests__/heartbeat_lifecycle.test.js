"use strict";

const assert = require("assert");
const { EventEmitter } = require("events");
const HeartbeatManager = require("../heartbeat");
const { setup } = require("../ws");
const callHandlers = require("../ws/handlers/call");
const webrtcHandlers = require("../ws/handlers/webrtc");

// No server, providers, stores or real timers. Exercise the actual WS callbacks.
function fixture(run) {
  const originalNow = Date.now;
  const originalInterval = global.setInterval;
  const originalLog = console.log;
  let now = 1_000_000;
  let tick;
  const pendingCloses = [];
  const clients = new Map();
  const clientIds = new Map();
  const routingTable = new Map();
  const ipConnections = new Map();
  const cleared = [];
  const hb = new HeartbeatManager(routingTable, clients, clientIds);
  const ctx = {
    clients, clientIds, routingTable, ipConnections, hb,
    rateLimit: { registerEvent: () => true, registerBinaryEvent: () => true,
      clear: id => cleared.push(id) },
    getClientIp: () => "192.0.2.1",
    forwardBinaryToPeer: () => { throw new Error("media is outside this test"); },
    getClientId: id => clients.get(id)?.clientId,
    getSessionPeer: (id, peer) => {
      const session = routingTable.get(id);
      return session?.from === peer ? session.to : session?.to === peer ? session.from : null;
    },
    sendToClient: (id, message) => {
      const peer = clients.get(clientIds.get(id));
      if (!peer || peer.ws.readyState !== 1) return false;
      peer.ws.send(JSON.stringify(message));
      return true;
    },
  };
  ctx.handlers = { ...callHandlers(ctx), ...webrtcHandlers(ctx) };
  const wss = new EventEmitter();
  try {
    Date.now = () => now;
    global.setInterval = (callback, interval) => {
      assert.strictEqual(interval, 5000);
      assert.strictEqual(tick, undefined);
      tick = callback;
      return 1;
    };
    console.log = () => {};
    setup(wss, ctx);
    hb.start();
    function attach(id) {
      const ws = new EventEmitter();
      Object.assign(ws, {
        readyState: 1, messages: [], terminated: 0, pings: 0, pingFails: false,
        send(value) { this.messages.push(JSON.parse(value)); },
        ping() {
          this.pings++;
          if (this.pingFails) throw new Error("synthetic ping failure");
        },
        terminate() {
          this.terminated++;
          this.readyState = 2;
          pendingCloses.push(() => { this.readyState = 3; this.emit("close"); });
        },
      });
      wss.emit("connection", ws, {});
      const connId = [...clients].find(([, client]) => client.ws === ws)[0];
      Object.assign(clients.get(connId), { clientId: id, identityId: id, authVersion: 2,
        identityPublicKey: "synthetic-already-verified-registration" });
      clientIds.set(id, connId);
      return { ws, connId, id };
    }
    const alice = attach("synthetic-alice");
    const bob = attach("synthetic-bob");
    const message = (peer, type, fields = {}) => peer.ws.emit("message",
      Buffer.from(JSON.stringify({ type, ...fields })), false);
    function active(id = "synthetic-session") {
      // Seed only the already-verified accept state; use real key-confirm consumers.
      routingTable.set(id, { from: alice.id, to: bob.id, state: "KEY_CONFIRM",
        callerConfirmed: false, calleeConfirmed: false, created: now, updated: now });
      message(alice, "CALL_KEY_CONFIRM", { sessionId: id, protocol: 2,
        role: "caller", confirmation: "a".repeat(43) });
      message(bob, "CALL_KEY_CONFIRM", { sessionId: id, protocol: 2,
        role: "callee", confirmation: "b".repeat(43) });
      assert.strictEqual(routingTable.get(id).state, "ACTIVE");
      return id;
    }
    run({ ...ctx, alice, bob, active, attach, message, cleared,
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
  ["healthy ACTIVE survives old updated timestamp and still routes signaling", f => {
    const id = f.active();
    const updated = f.routingTable.get(id).updated;
    for (let i = 0; i < 120; i++) {
      f.elapse(5000);
      f.alice.ws.emit("pong");
      f.message(f.bob, "HEARTBEAT");
      f.advance(0);
      assert(f.routingTable.has(id), `healthy ACTIVE lost at ${(i + 1) * 5}s`);
    }
    assert.strictEqual(f.routingTable.get(id).updated, updated);
    f.message(f.alice, "WEBRTC_OFFER", { sessionId: id, sdp: "synthetic-sdp" });
    assert.strictEqual(f.bob.ws.messages.at(-1).type, "WEBRTC_OFFER");
    assert.strictEqual(f.alice.ws.messages.at(-1).type, "WEBRTC_OFFER_ACK");
    assert.strictEqual(f.clients.size, 2);
  }],
  ["pending INVITE, KEY_CONFIRM and legacy handshakes expire strictly after 60s", f => {
    for (const state of ["INVITE", "KEY_CONFIRM", "LEGACY_INVITE"]) {
      f.routingTable.set(state, { state, from: f.alice.id, to: f.bob.id, updated: Date.now() });
    }
    f.advance(60000);
    assert.strictEqual(f.routingTable.size, 3);
    f.advance(1);
    assert.strictEqual(f.routingTable.size, 0);
  }],
  ["ACTIVE idle grace remains 180s, not an exemption from dead-peer close cleanup", f => {
    const id = f.active();
    f.elapse(180000);
    // Isolate the client idle threshold from the independent old-session defect.
    f.hb.updateSession(id);
    f.advance(0);
    assert.strictEqual(f.alice.ws.terminated, 0);
    assert(f.clients.has(f.alice.connId));
    f.advance(1);
    assert.strictEqual(f.alice.ws.terminated, 1);
    assert.strictEqual(f.bob.ws.terminated, 1);
    assert(f.clients.has(f.alice.connId), "terminating socket must keep close metadata");
    assert(f.routingTable.has(id), "close must own terminating route cleanup");
    f.flushCloses();
    assert.strictEqual(f.routingTable.size, 0, "delete-before-close left a routing orphan");
    assert.strictEqual(f.clientIds.size, 0, "close lost current ownership metadata");
    assert.strictEqual(f.ipConnections.size, 0, "close lost IP accounting metadata");
    assert.strictEqual(f.cleared.length, 2);
  }],
  ["non-call client idle threshold remains strictly above 60s", f => {
    f.advance(60000);
    assert.strictEqual(f.alice.ws.terminated, 0);
    f.advance(1);
    assert.strictEqual(f.alice.ws.terminated, 1);
    f.flushCloses();
    assert.strictEqual(f.clients.size, 0);
    assert.strictEqual(f.clientIds.size, 0, "idle close must release current binding");
    assert.strictEqual(f.ipConnections.size, 0);
  }],
  ["ping failure terminates socket and lets real close release ownership", f => {
    const id = f.active();
    f.alice.ws.pingFails = true;
    f.elapse(65000);
    f.bob.ws.emit("pong");
    f.advance(0);
    assert.strictEqual(f.alice.ws.terminated, 1, "failed ping removed client without closing socket");
    assert(f.clients.has(f.alice.connId));
    assert(f.routingTable.has(id));
    f.flushCloses();
    assert.strictEqual(f.routingTable.size, 0);
    assert(!f.clientIds.has(f.alice.id));
    assert.strictEqual(f.ipConnections.get("192.0.2.1"), 1);
    assert.strictEqual(f.bob.ws.messages.at(-1).reason, "peer_disconnected");
  }],
  ["single timed-out owner retains old ACTIVE until close and notifies healthy peer", f => {
    const id = f.active();
    f.elapse(180001);
    f.bob.ws.emit("pong");
    f.advance(0);
    assert.strictEqual(f.alice.ws.terminated, 1);
    assert.strictEqual(f.bob.ws.terminated, 0);
    assert(f.routingTable.has(id));
    assert(f.clients.has(f.alice.connId));
    f.advance(5000);
    assert.strictEqual(f.alice.ws.terminated, 1, "terminating socket was terminated again");
    assert(f.routingTable.has(id));
    f.flushCloses();
    assert(!f.routingTable.has(id));
    assert(!f.clients.has(f.alice.connId));
    assert(!f.clientIds.has(f.alice.id));
    assert.strictEqual(f.clientIds.get(f.bob.id), f.bob.connId);
    assert.strictEqual(f.bob.ws.messages.at(-1).reason, "peer_disconnected");
    assert.strictEqual(f.ipConnections.get("192.0.2.1"), 1);
    assert.deepStrictEqual(f.cleared, [f.alice.connId]);
  }],
  ["current socket close cleans session and notifies peer", f => {
    f.active();
    f.alice.ws.readyState = 3;
    f.alice.ws.emit("close");
    assert.strictEqual(f.routingTable.size, 0);
    assert(!f.clientIds.has(f.alice.id));
    assert.strictEqual(f.bob.ws.messages.at(-1).reason, "peer_disconnected");
    assert.strictEqual(f.ipConnections.get("192.0.2.1"), 1);
  }],
  ["superseded socket close preserves replacement ownership and ACTIVE session", f => {
    const id = f.active();
    const replacement = f.attach(f.alice.id);
    f.alice.ws.readyState = 3;
    f.alice.ws.emit("close");
    assert.strictEqual(f.clientIds.get(f.alice.id), replacement.connId);
    assert(f.routingTable.has(id));
    assert.strictEqual(f.clients.size, 2);
    assert.strictEqual(f.ipConnections.get("192.0.2.1"), 2);
    f.elapse(65000);
    replacement.ws.emit("pong");
    f.bob.ws.emit("pong");
    f.advance(0);
    assert(f.routingTable.has(id), "current replacement owns long ACTIVE lifetime");
  }],
  ["unowned ACTIVE expires even with a healthy superseded socket", f => {
    const id = f.active();
    f.clientIds.delete(f.alice.id);
    f.elapse(60000);
    f.alice.ws.emit("pong");
    f.bob.ws.emit("pong");
    f.advance(0);
    assert(f.routingTable.has(id));
    f.advance(1);
    assert(!f.routingTable.has(id));
  }],
  ["missing or mismatched current client record cannot keep ACTIVE alive", f => {
    const id = f.active();
    f.clientIds.set(f.alice.id, "synthetic-missing-connection");
    f.routingTable.set("mismatched", { state: "ACTIVE", from: "synthetic-other",
      to: f.bob.id, updated: Date.now() });
    f.clientIds.set("synthetic-other", f.bob.connId);
    f.elapse(60001);
    f.alice.ws.emit("pong");
    f.bob.ws.emit("pong");
    f.advance(0);
    assert(!f.routingTable.has(id));
    assert(!f.routingTable.has("mismatched"));
  }],
  ["timeout close of superseded socket leaves current owner and route intact", f => {
    const id = f.active();
    const replacement = f.attach(f.alice.id);
    f.elapse(180001);
    replacement.ws.emit("pong");
    f.bob.ws.emit("pong");
    f.advance(0);
    assert.strictEqual(f.alice.ws.terminated, 1);
    assert(f.clients.has(f.alice.connId));
    assert(f.routingTable.has(id));
    f.flushCloses();
    assert.strictEqual(f.clientIds.get(f.alice.id), replacement.connId);
    assert(f.routingTable.has(id));
    assert.strictEqual(f.ipConnections.get("192.0.2.1"), 2);
    assert.strictEqual(f.bob.ws.messages.filter(m => m.type === "CALL_END").length, 0);
  }],
  ["explicit END and BUSY use actual participant cleanup", f => {
    const id = f.active();
    f.message(f.alice, "CALL_END", { sessionId: id });
    assert(!f.routingTable.has(id));
    assert.strictEqual(f.bob.ws.messages.at(-1).type, "CALL_END");
    assert.strictEqual(f.alice.ws.messages.at(-1).type, "CALL_END_ACK");
    f.routingTable.set("pending", { state: "INVITE", from: f.alice.id,
      to: f.bob.id, updated: Date.now() });
    f.message(f.bob, "CALL_BUSY", { sessionId: "pending" });
    assert.strictEqual(f.routingTable.size, 0);
    assert.strictEqual(f.alice.ws.messages.at(-1).type, "CALL_BUSY");
  }],
];

let failures = 0;
for (const [name, run] of cases) {
  try {
    fixture(run);
    console.log(`PASS ${name}`);
  } catch (error) {
    failures++;
    console.error(`FAIL ${name}: ${error.message}`);
  }
}
console.log(`heartbeat_lifecycle.test.js: ${cases.length - failures}/${cases.length} PASS`);
process.exitCode = failures ? 1 : 0;
