"use strict";

// STX-17: /api/report rateLimitStore must be bounded — oldest-first eviction
// at the hard cap while the 3 reports/hour per-IP limit stays unchanged.
// Run: node src/__tests__/report_rate_limit_bounds.test.js

process.env.REPORT_RATE_LIMIT_MAX_KEYS = "3";
process.env.GITHUB_TOKEN = "synthetic-test-token-not-a-secret";
delete process.env.TRUST_PROXY;
delete process.env.RAILWAY_ENVIRONMENT;

const assert = require("node:assert/strict");

let handler;
const app = {
  use() {},
  post(route, fn) { if (route === "/api/report") handler = fn; },
};
require("../reportRoute")(app);
assert.equal(typeof handler, "function", "report route registered");

async function report(ip) {
  const req = { headers: {}, socket: { remoteAddress: ip }, body: {} };
  let status;
  const res = { status(code) { status = code; return this; }, json() { return this; } };
  await handler(req, res);
  return status;
}

(async () => {
  // IP A exhausts its limit (3/hour) — limit itself unchanged.
  for (let i = 0; i < 3; i++) assert.equal(await report("192.0.2.1"), 400);
  assert.equal(await report("192.0.2.1"), 429, "per-IP limit still enforced");

  // Fill the map to the cap (A, B, C), then D forces oldest-first eviction of A.
  assert.equal(await report("192.0.2.2"), 400);
  assert.equal(await report("192.0.2.3"), 400);
  assert.equal(await report("192.0.2.4"), 400, "new IP admitted by evicting the oldest entry");

  // A was evicted: its window restarted, so it passes the limiter again (400,
  // validation) instead of sitting at 429 as it would without eviction.
  assert.equal(await report("192.0.2.1"), 400, "evicted IP window resets");

  console.log("report_rate_limit_bounds.test.js: PASS");
  process.exit(0);
})().catch((err) => { console.error(err); process.exit(1); });
