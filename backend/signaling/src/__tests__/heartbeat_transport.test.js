"use strict";

const assert = require("assert");
const http = require("http");
const WebSocket = require("ws");
const HeartbeatManager = require("../heartbeat");
const { setup } = require("../ws");

const CASE_MS = 4000;
const CLEANUP_MS = 2000;
const GLOBAL_MS = 20000;

// Every asynchronous wait is cancellable, including failed startup and cleanup.
function event(emitter, name, signal, select = (...args) => args, tolerateError = false) {
  const promise = new Promise((resolve, reject) => {
    const finish = (error, value) => {
      emitter.removeListener(name, onEvent);
      emitter.removeListener("error", onError);
      signal.removeEventListener("abort", onAbort);
      if (error) reject(error);
      else resolve(value);
    };
    const onEvent = (...args) => {
      try {
        const value = select(...args);
        if (value !== undefined) finish(null, value);
      } catch { finish(new Error("invalid transport event")); }
    };
    const onError = () => finish(new Error("transport event error"));
    const onAbort = () => finish(new Error("transport deadline"));
    emitter.on(name, onEvent);
    if (!tolerateError) emitter.on("error", onError);
    signal.addEventListener("abort", onAbort, { once: true });
    if (signal.aborted) onAbort();
  });
  // A synchronous assertion can fail before the caller awaits this observer.
  promise.catch(() => {});
  return promise;
}

function message(peer, type, signal) {
  return event(peer, "message", signal, data => {
    const payload = JSON.parse(data.toString());
    return payload.type === type ? payload : undefined;
  });
}

function closed(peer, signal) {
  return peer.readyState === WebSocket.CLOSED ? Promise.resolve()
    : event(peer, "close", signal, (...args) => args, true);
}

function tickAt(tick, now) {
  const originalNow = Date.now;
  try {
    Date.now = () => now;
    tick();
  } finally {
    Date.now = originalNow;
  }
}

async function fixture(run, globalSignal) {
  const controller = new AbortController();
  const signal = controller.signal;
  const abort = () => controller.abort();
  const caseTimer = setTimeout(abort, CASE_MS);
  globalSignal.addEventListener("abort", abort, { once: true });
  if (globalSignal.aborted) abort();
  const originalLog = console.log;
  const originalInterval = global.setInterval;
  const clients = new Map();
  const clientIds = new Map();
  const routingTable = new Map();
  const ipConnections = new Map();
  const limiterEntries = new Set();
  const cleared = new Set();
  const outbound = new Set();
  const inbound = new Set();
  const tcpSockets = new Set();
  const server = http.createServer((_req, res) => { res.writeHead(404); res.end(); });
  const wss = new WebSocket.Server({ server });
  server.on("error", () => {});
  wss.on("error", () => {});
  server.on("connection", socket => {
    tcpSockets.add(socket);
    socket.on("error", () => {});
    socket.once("close", () => tcpSockets.delete(socket));
  });
  wss.on("connection", peer => {
    inbound.add(peer);
    peer.on("error", () => {});
  });
  const hb = new HeartbeatManager(routingTable, clients, clientIds);
  const ctx = {
    clients, clientIds, routingTable, ipConnections, hb,
    getClientIp: () => "127.0.0.1",
    rateLimit: {
      registerEvent: id => { limiterEntries.add(id); return true; },
      registerBinaryEvent: () => { throw new Error("no binary transport in this test"); },
      clear: id => { limiterEntries.delete(id); cleared.add(id); },
    },
    handlers: {},
    forwardBinaryToPeer: () => { throw new Error("no media in this test"); },
    sendToClient: (id, payload) => {
      const record = clients.get(clientIds.get(id));
      if (!record || record.ws.readyState !== WebSocket.OPEN) return false;
      record.ws.send(JSON.stringify(payload));
      return true;
    },
  };
  let tick;
  try {
    // Suppress existing dispatcher/HB logs; report only fixed case labels.
    console.log = () => {};
    setup(wss, ctx);
    try {
      global.setInterval = (callback, interval) => {
        assert.strictEqual(interval, 5000);
        assert.strictEqual(tick, undefined);
        tick = callback;
        return 1;
      };
      hb.start();
    } finally {
      global.setInterval = originalInterval;
    }
    const listening = event(server, "listening", signal);
    server.listen({ host: "127.0.0.1", port: 0, signal });
    await listening;
    const address = server.address();
    assert(address && address.address === "127.0.0.1" && address.port > 0);

    async function connect(id) {
      const accepted = event(wss, "connection", signal);
      const peer = new WebSocket(`ws://127.0.0.1:${address.port}`, { handshakeTimeout: 1500 });
      outbound.add(peer);
      peer.on("error", () => {});
      const messages = [];
      peer.on("message", data => {
        try { messages.push(JSON.parse(data.toString())); }
        catch { controller.abort(); }
      });
      const [, [socket]] = await Promise.all([event(peer, "open", signal), accepted]);
      const entry = [...clients].find(([, record]) => record.ws === socket);
      assert(entry, "existing setup must own accepted socket record");
      const [connId, record] = entry;
      Object.assign(record, { clientId: id, authVersion: 2, identityId: id });
      clientIds.set(id, connId);
      const ack = message(peer, "HEARTBEAT_ACK", signal);
      peer.send(JSON.stringify({ type: "HEARTBEAT" }));
      await ack;
      assert(limiterEntries.has(connId), "actual dispatcher must enter the test limiter");
      return { peer, socket, connId, record, messages, id };
    }

    const alice = await connect("synthetic-alice");
    const bob = await connect("synthetic-bob");
    const updated = Date.now();
    const sessionId = "synthetic-active-session";
    const session = { from: alice.id, to: bob.id, state: "ACTIVE", updated };
    routingTable.set(sessionId, session);
    await run({ ...ctx, alice, bob, sessionId, session, updated, connect, signal,
      limiterEntries, cleared, tick: now => tickAt(tick, now) });
  } finally {
    global.setInterval = originalInterval;
    clearTimeout(caseTimer);
    globalSignal.removeEventListener("abort", abort);
    controller.abort();
    const cleanup = new AbortController();
    const cleanupTimer = setTimeout(() => cleanup.abort(), CLEANUP_MS);
    try {
      const peers = [...outbound, ...inbound];
      const peerCloses = peers.map(peer => closed(peer, cleanup.signal));
      for (const peer of peers) {
        if (peer.readyState !== WebSocket.CLOSED) peer.terminate();
      }
      const wssClosed = event(wss, "close", cleanup.signal);
      wss.close();
      // Own TCP sockets cover an interrupted upgrade/listen as well as WS peers.
      const tcpCloses = [...tcpSockets].map(socket => event(socket, "close", cleanup.signal));
      for (const socket of tcpSockets) socket.destroy();
      const serverClosed = server.listening ? event(server, "close", cleanup.signal) : Promise.resolve();
      server.close();
      await Promise.all([...peerCloses, ...tcpCloses, wssClosed, serverClosed]);
      assert(!server.listening && wss.clients.size === 0 && tcpSockets.size === 0,
        "test-owned transport handles must be released");
    } finally {
      clearTimeout(cleanupTimer);
      cleanup.abort();
      console.log = originalLog;
    }
  }
}

async function expectCurrentCleanup(f, failPing) {
  const now = f.updated + (failPing ? 65000 : 180001);
  f.bob.record.lastSeen = now;
  f.alice.record.lastSeen = failPing ? now : f.updated;
  const aliceClosed = closed(f.alice.peer, f.signal);
  const socketClosed = closed(f.alice.socket, f.signal);
  const ended = message(f.bob.peer, "CALL_END", f.signal);
  const originalPing = f.alice.socket.ping;
  let failures = 0;
  try {
    if (failPing) f.alice.socket.ping = () => { failures++; throw new Error("synthetic ping failure"); };
    f.tick(now);
    assert(f.clients.has(f.alice.connId), "close must retain its metadata until delivery");
    assert(f.routingTable.get(f.sessionId) === f.session, "close must own ACTIVE deletion");
  } finally {
    f.alice.socket.ping = originalPing;
  }
  const [, , payload] = await Promise.all([aliceClosed, socketClosed, ended]);
  assert.strictEqual(failures, failPing ? 1 : 0);
  assert.strictEqual(payload.sessionId, f.sessionId);
  assert.strictEqual(payload.reason, "peer_disconnected");
  assert.strictEqual(f.alice.peer.readyState, WebSocket.CLOSED);
  assert.strictEqual(f.alice.socket.readyState, WebSocket.CLOSED);
  assert(!f.clients.has(f.alice.connId) && !f.clientIds.has(f.alice.id));
  assert(!f.routingTable.has(f.sessionId));
  assert.strictEqual(f.clients.size, 1);
  assert.strictEqual(f.clientIds.get(f.bob.id), f.bob.connId);
  assert.strictEqual(f.bob.peer.readyState, WebSocket.OPEN);
  assert.strictEqual(f.ipConnections.get("127.0.0.1"), 1);
  assert(!f.limiterEntries.has(f.alice.connId) && f.limiterEntries.has(f.bob.connId));
  assert(f.cleared.has(f.alice.connId) && !f.cleared.has(f.bob.connId));
}

const cases = [
  ["timeout closes real current socket, cleans metadata and notifies peer", f => expectCurrentCleanup(f, false)],
  ["ping error terminates real current socket and closes through existing cleanup", f => expectCurrentCleanup(f, true)],
  ["superseded old socket termination preserves replacement route and binding", async f => {
    const replacement = await f.connect(f.alice.id);
    const now = f.updated + 180001;
    f.alice.record.lastSeen = f.updated;
    f.bob.record.lastSeen = now;
    replacement.record.lastSeen = now;
    const oldClosed = closed(f.alice.peer, f.signal);
    const socketClosed = closed(f.alice.socket, f.signal);
    f.tick(now);
    assert(f.clients.has(f.alice.connId), "old close must still own its metadata");
    await Promise.all([oldClosed, socketClosed]);
    // An ACK on the same peer stream observes any earlier incorrect CALL_END.
    const ack = message(f.bob.peer, "HEARTBEAT_ACK", f.signal);
    f.bob.peer.send(JSON.stringify({ type: "HEARTBEAT" }));
    await ack;
    assert(!f.clients.has(f.alice.connId));
    assert.strictEqual(f.clientIds.get(f.alice.id), replacement.connId);
    assert(f.clients.get(replacement.connId) === replacement.record);
    assert(f.routingTable.get(f.sessionId) === f.session);
    assert.strictEqual(f.clients.size, 2);
    assert.strictEqual(f.ipConnections.get("127.0.0.1"), 2);
    assert(!f.limiterEntries.has(f.alice.connId) && f.limiterEntries.has(replacement.connId));
    assert(f.cleared.has(f.alice.connId) && !f.cleared.has(replacement.connId));
    assert.strictEqual(replacement.peer.readyState, WebSocket.OPEN);
    assert.strictEqual(f.bob.messages.filter(payload => payload.type === "CALL_END").length, 0);
  }],
];

(async () => {
  const globalDeadline = new AbortController();
  const timer = setTimeout(() => globalDeadline.abort(), GLOBAL_MS);
  let failures = 0;
  try {
    for (const [label, run] of cases) {
      try {
        await fixture(run, globalDeadline.signal);
        console.log(`PASS ${label}`);
      } catch {
        failures++;
        console.error(`FAIL ${label}`);
      }
    }
  } finally {
    clearTimeout(timer);
    globalDeadline.abort();
  }
  console.log(`heartbeat_transport.test.js: ${cases.length - failures}/${cases.length} PASS`);
  process.exitCode = failures ? 1 : 0;
})().catch(() => {
  console.error("heartbeat_transport.test.js: FAIL (transport harness)");
  process.exitCode = 1;
});
