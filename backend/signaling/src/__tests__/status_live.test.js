"use strict";

/**
 * status_live.test.js — STX-02 (audit issue #84) regression: the public
 * GET /status/live response must never contain client IP addresses, a per-IP
 * list, or any IP-bearing key, while non-identifying service/count/limit
 * fields stay exact and per-IP reconciliation still runs server-side.
 *
 * Run: node src/__tests__/status_live.test.js
 */

const assert = require("assert");
const WebSocket = require("ws");

const statusRoutes = require("../routes/status");

// Representative documentation-range addresses (RFC 5737 / RFC 3849).
const IPV4 = "203.0.113.10";
const IPV6 = "2001:db8::42";
const IPV4_CLOSED = "198.51.100.99";

// ── State: clients on representative IPv4 and IPv6 addresses ─────────────────

const clients = new Map([
  ["conn-v4-a", { ip: IPV4, ws: { readyState: WebSocket.OPEN } }],
  ["conn-v4-b", { ip: IPV4, ws: { readyState: WebSocket.OPEN } }],
  ["conn-v6",   { ip: IPV6, ws: { readyState: WebSocket.OPEN } }],
  ["conn-dead", { ip: IPV4_CLOSED, ws: { readyState: WebSocket.CLOSED } }],
]);
const clientIds = new Map([["alice", "conn-v4-a"], ["bob", "conn-v6"]]);
const fcmTokens = new Map([["alice", "fcm-token-1"]]);
const ipConnections = new Map();
const wsLimits = { maxConnectionsPerIp: 80, maxAttemptsPerIp: 2000, attemptWindowMs: 60000 };

// ── Wire the route module against a mock express app ──────────────────────────

let handler = null;
const app = {
  get: (routePath, fn) => { if (routePath === "/status/live") handler = fn; },
};
statusRoutes.setup(app, { clients, clientIds, fcmTokens, ipConnections, wsLimits });
assert.strictEqual(typeof handler, "function", "GET /status/live must be registered");

let payload = null;
handler({}, { json: (body) => { payload = body; } });
assert.ok(payload && typeof payload === "object", "handler must respond with a JSON object");

// ── Reconciliation still runs (server-side per-IP state for WS limiting) ──────

assert.strictEqual(ipConnections.get(IPV4), 2, "reconcile must count both open IPv4 clients");
assert.strictEqual(ipConnections.get(IPV6), 1, "reconcile must count the open IPv6 client");
assert.strictEqual(ipConnections.size, 2, "reconcile must skip the closed socket");
assert.ok(!ipConnections.has(IPV4_CLOSED), "closed socket must not enter the per-IP map");

// ── Non-identifying service/count/limit fields remain exact ───────────────────

assert.strictEqual(payload.server, "online");
assert.strictEqual(payload.connectedClients, 4);
assert.strictEqual(payload.registeredIds, 2);
assert.strictEqual(payload.fcmTokens, 1);
assert.deepStrictEqual(payload.wsLimits, {
  maxConnectionsPerIp: 80,
  maxAttemptsPerIp: 2000,
  attemptWindowMs: 60000,
});
assert.ok(Number.isInteger(payload.uptime) && payload.uptime >= 0, "uptime must be a non-negative integer");
assert.ok(!Number.isNaN(Date.parse(payload.timestamp)), "timestamp must be an ISO date string");

// ── Privacy: no per-IP field, no IP-bearing key, neither address anywhere ──────

assert.ok(!("ipConnectionBuckets" in payload), "ipConnectionBuckets must be removed");
assert.ok(!("ipConnections" in payload), "no per-IP map may be exposed under any name");

const serialized = JSON.stringify(payload);
assert.ok(!serialized.includes(IPV4), `response must not contain ${IPV4}`);
assert.ok(!serialized.includes(IPV6), `response must not contain ${IPV6}`);
assert.ok(!serialized.includes(IPV4_CLOSED), `response must not contain ${IPV4_CLOSED}`);

(function assertNoIpBearingKeysOrValues(node, path) {
  if (Array.isArray(node)) {
    node.forEach((item, i) => assertNoIpBearingKeysOrValues(item, `${path}[${i}]`));
    return;
  }
  if (node && typeof node === "object") {
    for (const [key, value] of Object.entries(node)) {
      for (const ip of [IPV4, IPV6, IPV4_CLOSED]) {
        assert.ok(!key.includes(ip), `key at ${path} must not bear a client IP`);
        assert.ok(
          !(typeof value === "string" && value.includes(ip)),
          `value at ${path}.${key} must not bear a client IP`,
        );
      }
      assertNoIpBearingKeysOrValues(value, `${path}.${key}`);
    }
  }
})(payload, "$");

console.log("status_live.test.js: PASS");
