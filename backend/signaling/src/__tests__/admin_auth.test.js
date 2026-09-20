"use strict";

const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const {
  createAdminFailureLimiter,
  makeRequireAdmin,
  readAdminAuthConfig,
  timingSafeCredentialMatch
} = require("../middleware/admin");

function response() {
  return {
    statusCode: 200,
    body: null,
    headers: {},
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; },
    setHeader(name, value) { this.headers[name] = value; }
  };
}

function request(key, ip = "192.0.2.10") {
  const headers = {};
  if (key !== undefined) headers["x-admin-key"] = key;
  return { headers, syntheticIp: ip };
}

function invoke(middleware, req) {
  const res = response();
  let nextCalled = false;
  middleware(req, res, () => { nextCalled = true; });
  return { res, nextCalled };
}

const syntheticKey = "synthetic-admin-key-for-tests";
const expectedDigest = crypto.createHash("sha256").update(syntheticKey, "utf8").digest();
assert.strictEqual(
  timingSafeCredentialMatch(syntheticKey, expectedDigest),
  true,
  "matching credential digest is accepted"
);
assert.strictEqual(
  timingSafeCredentialMatch("wrong-synthetic-key", expectedDigest),
  false,
  "different fixed-length credential digest is rejected"
);

let result = invoke(makeRequireAdmin(null), request(undefined));
assert.strictEqual(result.res.statusCode, 403, "missing configured key disables the admin API");
assert.deepStrictEqual(result.res.body, { error: "admin_api_disabled" });

let now = 1000;
const requireAdmin = makeRequireAdmin(syntheticKey, {
  getClientIp: req => req.syntheticIp,
  now: () => now,
  windowMs: 1000,
  maxFailures: 2,
  maxBuckets: 8,
  bucketTtlMs: 1000,
  maxCredentialLength: 64,
  env: {}
});

result = invoke(requireAdmin, request(undefined));
assert.strictEqual(result.res.statusCode, 401, "missing request key is unauthorized");
assert.deepStrictEqual(result.res.body, { error: "unauthorized" });

result = invoke(requireAdmin, request([syntheticKey], "192.0.2.11"));
assert.strictEqual(result.res.statusCode, 401, "non-string request key is unauthorized");

result = invoke(requireAdmin, request("x".repeat(65), "192.0.2.12"));
assert.strictEqual(result.res.statusCode, 401, "oversized request key is unauthorized without hashing it");

result = invoke(requireAdmin, request("wrong-one"));
assert.strictEqual(result.res.statusCode, 401, "second failed attempt remains unauthorized");
result = invoke(requireAdmin, request("wrong-two"));
assert.strictEqual(result.res.statusCode, 429, "attempts beyond the bounded failure budget are throttled");
assert.strictEqual(result.res.headers["Retry-After"], "1", "throttled response includes retry guidance");

result = invoke(requireAdmin, request(syntheticKey));
assert.strictEqual(result.nextCalled, true, "valid key is never locked out by failures from the same IP");
assert.strictEqual(result.res.statusCode, 200);

now += 1000;
result = invoke(requireAdmin, request("wrong-after-reset"));
assert.strictEqual(result.res.statusCode, 401, "fixed window resets exactly at its boundary");

const limiter = createAdminFailureLimiter({
  now: () => now,
  windowMs: 1000,
  maxFailures: 1,
  maxBuckets: 2,
  bucketTtlMs: 1000
});
limiter.recordFailure("client-a");
limiter.recordFailure("client-b");
limiter.recordFailure("client-c");
assert.deepStrictEqual(limiter.stats(), { buckets: 2, failures: 2 }, "failure buckets stay hard bounded");

let isolationNow = 0;
const isolatedAdmin = makeRequireAdmin(syntheticKey, {
  getClientIp: req => req.syntheticIp,
  now: () => isolationNow,
  windowMs: 1000,
  maxFailures: 1,
  bucketTtlMs: 1000,
  env: {}
});
assert.strictEqual(
  invoke(isolatedAdmin, request("wrong-a", "192.0.2.20")).res.statusCode,
  401,
  "first client receives its own failure budget"
);
assert.strictEqual(
  invoke(isolatedAdmin, request("wrong-a-again", "192.0.2.20")).res.statusCode,
  429,
  "first client exhausts only its own failure budget"
);
assert.strictEqual(
  invoke(isolatedAdmin, request("wrong-b", "192.0.2.21")).res.statusCode,
  401,
  "second client retains an independent failure budget"
);

let pruneNow = 0;
const pruningLimiter = createAdminFailureLimiter({
  now: () => pruneNow,
  windowMs: 1000,
  maxFailures: 2,
  maxBuckets: 4,
  bucketTtlMs: 1000
});
pruningLimiter.recordFailure("stale-client");
pruneNow = 1000;
pruningLimiter.recordFailure("fresh-client");
assert.deepStrictEqual(
  pruningLimiter.stats(),
  { buckets: 1, failures: 1 },
  "TTL pruning removes stale buckets at the exact boundary"
);

let monotonicNow = 5000;
const monotonicLimiter = createAdminFailureLimiter({
  now: () => monotonicNow,
  windowMs: 1000,
  maxFailures: 1,
  bucketTtlMs: 1000
});
assert.strictEqual(monotonicLimiter.recordFailure("clock-client").limited, false);
monotonicNow = 4000;
assert.strictEqual(
  monotonicLimiter.recordFailure("clock-client").limited,
  true,
  "backwards clock movement cannot reset the active failure window"
);

const invalidClockLimiter = createAdminFailureLimiter({ now: () => Number.NaN });
assert.throws(
  () => invalidClockLimiter.recordFailure("clock-client"),
  /clock must return a finite number/,
  "invalid clocks fail closed instead of corrupting limiter state"
);

const config = readAdminAuthConfig({
  ADMIN_AUTH_WINDOW_MS: "junk",
  ADMIN_AUTH_MAX_FAILURES: "0",
  ADMIN_AUTH_MAX_BUCKETS: "999999",
  ADMIN_AUTH_BUCKET_TTL_MS: "10.5",
  ADMIN_AUTH_MAX_CREDENTIAL_LENGTH: "-1"
});
assert.strictEqual(config.windowMs, 300000, "invalid window falls back safely");
assert.strictEqual(config.maxFailures, 1, "valid integer below minimum is clamped");
assert.strictEqual(config.maxBuckets, 50000, "bucket count is clamped to its hard maximum");
assert.strictEqual(config.bucketTtlMs, 1800000, "invalid bucket TTL falls back safely");
assert.strictEqual(config.maxCredentialLength, 32, "credential length is clamped to its minimum");

const longWindowConfig = readAdminAuthConfig({
  ADMIN_AUTH_WINDOW_MS: String(2 * 60 * 60 * 1000)
});
assert.strictEqual(
  longWindowConfig.bucketTtlMs,
  longWindowConfig.windowMs,
  "default bucket TTL is raised to preserve the window invariant"
);

const longWindowLimiter = createAdminFailureLimiter({
  windowMs: 2 * 60 * 60 * 1000
});
assert.strictEqual(
  longWindowLimiter.config.bucketTtlMs,
  longWindowLimiter.config.windowMs,
  "limiter fallback TTL cannot undercut an overridden window"
);

const longWindowMiddleware = makeRequireAdmin(syntheticKey, {
  windowMs: 2 * 60 * 60 * 1000,
  env: {}
});
assert.doesNotThrow(
  () => invoke(longWindowMiddleware, request("wrong-long-window", "192.0.2.13")),
  "middleware accepts a long bounded window with a safe fallback TTL"
);

const serverSource = fs.readFileSync(path.join(__dirname, "..", "server.js"), "utf8");
assert.match(serverSource, /const \{ makeRequireAdmin \}\s+=\s+require\("\.\/middleware\/admin"\)/);
assert.match(serverSource, /const requireAdmin = makeRequireAdmin\(ADMIN_API_KEY, \{ getClientIp \}\)/);
assert.match(serverSource, /app\.get\("\/api\/subscription\/:clientId", requireAdmin,/);
assert.match(serverSource, /stripeHandler\.setupRoutes\(app, activationCodes, \{ requireAdmin \}\)/);
assert.doesNotMatch(serverSource, /function requireAdmin\(/, "active server has no duplicate admin middleware");

const stripeSource = fs.readFileSync(path.join(__dirname, "..", "payments", "stripe_handler.js"), "utf8");
assert.doesNotMatch(stripeSource, /adminKey\s*!==\s*process\.env\.ADMIN_API_KEY/);
assert.match(stripeSource, /app\.post\("\/stripe\/test-email", deps\.requireAdmin,/);

const previousStripeKey = process.env.STRIPE_SECRET_KEY;
process.env.STRIPE_SECRET_KEY = "sk_test_synthetic_admin_wiring";
try {
  const { setupRoutes } = require("../payments/stripe_handler");
  const registered = [];
  const app = {
    post(routePath, ...handlers) { registered.push({ routePath, handlers }); }
  };
  const sharedMiddleware = () => {};
  const syntheticStripe = {};

  assert.throws(
    () => setupRoutes(app, [], { stripe: syntheticStripe }),
    /requireAdmin middleware is required/,
    "Stripe routes fail closed without the shared admin middleware"
  );
  assert.strictEqual(registered.length, 0, "missing middleware registers no Stripe route");

  setupRoutes(app, [], { requireAdmin: sharedMiddleware, stripe: syntheticStripe });
  const testEmailRoute = registered.find(route => route.routePath === "/stripe/test-email");
  assert.ok(testEmailRoute, "Stripe test-email route is registered");
  assert.strictEqual(testEmailRoute.handlers[0], sharedMiddleware, "test-email uses shared admin middleware");
  assert.strictEqual(testEmailRoute.handlers.length, 2, "test-email handler is not executed during wiring test");
} finally {
  if (previousStripeKey === undefined) delete process.env.STRIPE_SECRET_KEY;
  else process.env.STRIPE_SECRET_KEY = previousStripeKey;
}

console.log("admin_auth.test.js ok");
