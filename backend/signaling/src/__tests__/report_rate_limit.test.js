"use strict";
const assert = require("node:assert/strict");

// /api/report must key its rate limit on the trusted client IP (middleware/ip),
// not on the client-controlled leftmost X-Forwarded-For entry.
process.env.GITHUB_TOKEN = "synthetic-test-token-not-a-secret";
delete process.env.TRUST_PROXY;
delete process.env.RAILWAY_ENVIRONMENT;

let handler;
const app = {
  use() {},
  post(route, fn) { if (route === "/api/report") handler = fn; },
};
require("../reportRoute")(app);
assert.equal(typeof handler, "function", "report route registered");

// Missing fields: a request that passes the limiter stops at validation (400)
// and never reaches GitHub, so no network is used.
async function report(xff, remote) {
  const req = { headers: { "x-forwarded-for": xff }, socket: { remoteAddress: remote }, body: {} };
  let status;
  const res = { status(code) { status = code; return this; }, json() { return this; } };
  await handler(req, res);
  return status;
}

(async () => {
  // Without TRUST_PROXY: rotating spoofed XFF values from one peer share one bucket.
  const peer = "192.0.2.10";
  for (let i = 0; i < 3; i++) assert.equal(await report(`203.0.113.${i}`, peer), 400);
  assert.equal(await report("203.0.113.99", peer), 429, "spoofed XFF must not reset the limit");

  // With TRUST_PROXY: the rightmost (proxy-appended) entry keys the bucket,
  // a client-chosen prefix does not.
  process.env.TRUST_PROXY = "1";
  const proxy = "10.0.0.2";
  for (let i = 0; i < 3; i++) assert.equal(await report(`spoof-${i}, 198.51.100.7`, proxy), 400);
  assert.equal(await report("spoof-new, 198.51.100.7", proxy), 429, "prefix must not choose the bucket");
  assert.equal(await report("198.51.100.7, 198.51.100.8", proxy), 400, "distinct real client has its own bucket");

  console.log("report_rate_limit.test.js: PASS");
  process.exit(0);
})().catch((err) => { console.error(err); process.exit(1); });
