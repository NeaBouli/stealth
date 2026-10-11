"use strict";

// STX-17: the route-local IP limiters in stripe_handler.js (/stripe/create-checkout)
// and custom_ids.js (/custom-id/*) must be bounded — oldest-first eviction at the
// hard cap, per-IP limits unchanged.
// Run: node src/__tests__/route_limiter_bounds.test.js

const fs = require("fs");
const os = require("os");
const path = require("path");

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-route-limits-"));
process.env.STRIPE_PROCESSED_FILE = path.join(dir, "stripe_processed_events.json");
process.env.IDS_FILE = path.join(dir, "custom_ids.json");
process.env.PENDING_FILE = path.join(dir, "pending_activations.json");
process.env.STRIPE_SECRET_KEY = "sk_test_synthetic_not_a_secret";
process.env.STRIPE_CHECKOUT_RL_MAX_KEYS = "3";
process.env.CUSTOM_ID_RATE_LIMIT_MAX_KEYS = "3";

const assert = require("node:assert/strict");

function resStub() {
  return { code: null, status(c) { this.code = c; return this; }, json() { return this; } };
}

// --- stripe_handler checkoutRL ---
const stripeHandler = require("../payments/stripe_handler");
let checkoutMw;
const stripeApp = { post(route, mw) { if (route === "/stripe/create-checkout") checkoutMw = mw; } };
stripeHandler.setupRoutes(stripeApp, [], { requireAdmin: () => {}, stripe: {} });
assert.equal(typeof checkoutMw, "function", "stripe checkout limiter registered");

function stripeHit(ip) {
  let passed = false;
  const res = resStub();
  checkoutMw({ ip }, res, () => { passed = true; });
  return passed ? "next" : res.code;
}

// Per-IP limit unchanged: 5 per 10 minutes.
for (let i = 0; i < 5; i++) assert.equal(stripeHit("10.0.0.1"), "next");
assert.equal(stripeHit("10.0.0.1"), 429, "stripe per-IP limit still enforced");

// Fill to cap (ip1, ip2, ip3), then ip4 evicts ip1 (oldest).
assert.equal(stripeHit("10.0.0.2"), "next");
assert.equal(stripeHit("10.0.0.3"), "next");
assert.equal(stripeHit("10.0.0.4"), "next", "new IP admitted at cap via oldest-first eviction");
// ip1 was evicted at its limit: it passes again instead of staying at 429.
assert.equal(stripeHit("10.0.0.1"), "next", "evicted IP window resets");

// --- custom_ids customIdRateLimit ---
const customIds = require("../custom_ids");
let customIdMw;
const customApp = {
  get() {},
  post(route, mw) { if (route === "/custom-id/activate") customIdMw = mw; },
};
customIds.setupRoutes(customApp);
assert.equal(typeof customIdMw, "function", "custom-id limiter registered");

function customIdHit(ip) {
  let passed = false;
  const res = resStub();
  // Real request shape: the limiter resolves the client via getClientIp (socket address).
  customIdMw({ headers: {}, socket: { remoteAddress: ip } }, res, () => { passed = true; });
  return passed ? "next" : res.code;
}

// Per-IP limit unchanged: 5 per 15 minutes.
for (let i = 0; i < 5; i++) assert.equal(customIdHit("10.9.0.1"), "next");
assert.equal(customIdHit("10.9.0.1"), 429, "custom-id per-IP limit still enforced");

assert.equal(customIdHit("10.9.0.2"), "next");
assert.equal(customIdHit("10.9.0.3"), "next");
assert.equal(customIdHit("10.9.0.4"), "next", "new IP admitted at cap via oldest-first eviction");
assert.equal(customIdHit("10.9.0.1"), "next", "evicted IP window resets");

fs.rmSync(dir, { recursive: true, force: true });
console.log("route_limiter_bounds.test.js: PASS");
