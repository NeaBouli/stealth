"use strict";

/**
 * pkd_bounds.test.js - STX-08 (audit issue #84) regression: the in-memory
 * public-key directory and its registration limiter must stay bounded.
 *
 * Covers: entry cap with oldest-first eviction, TTL expiry and deterministic
 * pruning on every access path (including list/delete), rotate/delete
 * compatibility, bounded limiter buckets with constant-size fixed-window
 * state (hard max + TTL prune + LRU eviction), per-client window limits,
 * HTTP 201/400/404/429 contract via the modular route, trusted client-IP
 * usage (no raw X-Forwarded-For), strict integer env parsing, and the
 * preserved singleton API.
 *
 * Run: node src/__tests__/pkd_bounds.test.js
 */

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const { createPkd, readPkdConfig } = require("../pkd");
const {
  createPkdRegistrationLimiter,
  readLimiterConfig,
  pkdRegistrationRateLimit,
} = require("../security/pkd_registration_limiter");
const { getClientIp } = require("../middleware/ip");
const pkdRoutes = require("../routes/pkd");

// Representative documentation-range addresses (RFC 5737).
const IP_A = "203.0.113.10";
const IP_B = "203.0.113.11";

// Helpers: deterministic clock, mock express app, mock req/res.

function makeClock(start) {
  let t = start;
  return {
    now: () => t,
    advance(ms) { t += ms; },
  };
}

function createMockApp() {
  const routes = { post: {}, get: {}, put: {}, delete: {} };
  const app = {};
  for (const method of Object.keys(routes)) {
    app[method] = (routePath, ...handlers) => { routes[method][routePath] = handlers; };
  }
  return { app, routes };
}

function createMockRes() {
  return {
    statusCode: 200,
    body: undefined,
    status(code) { this.statusCode = code; return this; },
    json(payload) { this.body = payload; return this; },
  };
}

// Runs an express-style handler chain (middleware ..., handler) synchronously.
function runChain(handlers, req) {
  const res = createMockRes();
  let index = 0;
  const next = () => {
    index += 1;
    if (index < handlers.length) handlers[index](req, res, next);
  };
  handlers[0](req, res, next);
  return res;
}

function mockReq({ body, id, ip } = {}) {
  return {
    body,
    params: id ? { id } : {},
    headers: {},
    socket: { remoteAddress: ip || IP_A },
  };
}

// Mirrors the production middleware shape, but over an injected factory limiter.
function toMiddleware(limiter) {
  return (req, res, next) => {
    if (!limiter.allow(getClientIp(req) || "unknown")) {
      return res.status(429).json({ error: "rate_limited" });
    }
    next();
  };
}

const passAdmin = (req, res, next) => next();
const KEY_ID_REGEX = /^[0-9a-f]{32}$/; // 128-bit hex

// 1. Entry cap: oldest live entry is evicted before overflow.

{
  const clock = makeClock(1_000_000);
  const store = createPkd({ maxEntries: 3, ttlMs: 10_000, now: clock.now });

  const e1 = store.registerKey("key-one");
  const e2 = store.registerKey("key-two");
  const e3 = store.registerKey("key-three");
  assert.strictEqual(store.listKeys().length, 3, "store holds exactly 3 entries at cap");

  const e4 = store.registerKey("key-four");
  assert.strictEqual(store.listKeys().length, 3, "store must never exceed maxEntries");
  assert.strictEqual(store.getKey(e1.keyId), null, "oldest entry must be evicted first");
  assert.ok(store.getKey(e2.keyId), "second entry survives");
  assert.ok(store.getKey(e3.keyId), "third entry survives");
  assert.ok(store.getKey(e4.keyId), "new entry is stored");

  assert.ok(KEY_ID_REGEX.test(e4.keyId), "key IDs remain 128-bit random hex");
  assert.strictEqual(store.getKey(e4.keyId).publicKey, "key-four", "entry payload intact");
}

// 2. TTL: expired entries are pruned deterministically.

{
  const clock = makeClock(1_000_000);
  const store = createPkd({ maxEntries: 10, ttlMs: 1000, now: clock.now });

  const e1 = store.registerKey("key-one");
  clock.advance(999);
  assert.ok(store.getKey(e1.keyId), "entry still live one ms before TTL");

  clock.advance(1); // exactly at TTL boundary
  assert.strictEqual(store.getKey(e1.keyId), null, "entry expired at TTL boundary");
  assert.deepStrictEqual(store.listKeys(), [], "expired entry removed on access");

  // Pruning also runs on register: expired entries never count against the cap.
  const e2 = store.registerKey("key-two");
  clock.advance(1000);
  const e3 = store.registerKey("key-three");
  assert.deepStrictEqual(store.listKeys(), [e3.keyId], "register prunes expired entries");
  assert.strictEqual(store.getKey(e2.keyId), null, "expired entry stays gone");
}

// 2b. listKeys prunes expired entries before returning IDs.

{
  const clock = makeClock(1_000_000);
  const store = createPkd({ maxEntries: 5, ttlMs: 1000, now: clock.now });

  const first = store.registerKey("key-first");
  clock.advance(600);
  const second = store.registerKey("key-second");
  clock.advance(500); // t=+1100: first entry expired (1100 >= 1000), second live (500)

  assert.ok(first.keyId !== second.keyId, "distinct key ids");
  assert.deepStrictEqual(store.listKeys(), [second.keyId],
    "listKeys omits expired entries using the injected clock");
  assert.deepStrictEqual(store.listKeys(), [second.keyId],
    "listKeys stays stable after pruning");

  clock.advance(500); // t=+1600: both entries expired now
  assert.deepStrictEqual(store.listKeys(), [], "all expired entries pruned on list");
}

// 3. Rotate/delete compatibility with bounded store.

{
  const clock = makeClock(1_000_000);
  const store = createPkd({ maxEntries: 5, ttlMs: 1000, now: clock.now });

  const entry = store.registerKey("key-v1");
  clock.advance(100);
  const rotated = store.rotateKey(entry.keyId, "key-v2");
  assert.ok(rotated, "rotate works on a live entry");
  assert.strictEqual(rotated.publicKey, "key-v2");
  assert.strictEqual(rotated.created, entry.created, "rotate keeps created timestamp");
  assert.strictEqual(rotated.updated, 1_000_100, "rotate refreshes updated timestamp");

  assert.strictEqual(store.rotateKey("missing-key-id", "x"), null, "rotate unknown id -> null");

  clock.advance(1000); // entry now expired
  assert.strictEqual(store.rotateKey(entry.keyId, "key-v3"), null, "rotate expired entry -> null");
  assert.deepStrictEqual(store.listKeys(), [], "expired entry removed on rotate");

  const victim = store.registerKey("key-del");
  assert.strictEqual(store.deleteKey(victim.keyId), true, "delete existing -> true");
  assert.strictEqual(store.deleteKey(victim.keyId), false, "delete missing -> false");
}

// 3b. deleteKey treats expired entries as logically absent.

{
  const clock = makeClock(1_000_000);
  const store = createPkd({ maxEntries: 5, ttlMs: 1000, now: clock.now });

  const live = store.registerKey("key-live");
  clock.advance(999);
  assert.strictEqual(store.deleteKey(live.keyId), true,
    "delete live entry one ms before TTL -> true (live semantics unchanged)");

  const expired = store.registerKey("key-expired");
  clock.advance(1000); // exactly at TTL boundary -> expired
  assert.strictEqual(store.deleteKey(expired.keyId), false,
    "delete expired entry -> false (logically absent)");
  assert.deepStrictEqual(store.listKeys(), [], "expired entry removed by deleteKey");
}

// 4. Bounded limiter buckets: hard max, TTL prune, LRU eviction.

{
  const clock = makeClock(1_000_000);
  const limiter = createPkdRegistrationLimiter({
    windowMs: 10_000,
    maxPerWindow: 1,
    maxBuckets: 3,
    bucketTtlMs: 60_000,
    now: clock.now,
  });

  assert.strictEqual(limiter.allow(IP_A), true);
  assert.strictEqual(limiter.allow(IP_B), true);
  assert.strictEqual(limiter.allow("203.0.113.12"), true);
  assert.strictEqual(limiter.bucketCount(), 3, "bucket map at hard maximum");

  assert.strictEqual(limiter.allow(IP_A), false, "IP_A quota spent within window");
  // LRU order is now B, C, A - a new client evicts B, the oldest bucket.
  assert.strictEqual(limiter.allow("203.0.113.13"), true, "new client admitted via eviction");
  assert.strictEqual(limiter.bucketCount(), 3, "bucket map stays at hard maximum");
  assert.strictEqual(limiter.allow("203.0.113.12"), false, "C bucket still present");
  assert.strictEqual(limiter.allow(IP_B), true, "evicted client starts with a fresh bucket");
  assert.strictEqual(limiter.bucketCount(), 3, "bucket map bounded under churn");

  // Sustained flood of unique clients: the map can never grow past the cap.
  for (let i = 0; i < 1000; i++) limiter.allow(`198.51.100.${i % 256}:${i}`);
  assert.strictEqual(limiter.bucketCount(), 3, "bucket map bounded under sustained unique input");
}

{
  const clock = makeClock(1_000_000);
  const limiter = createPkdRegistrationLimiter({
    windowMs: 10,
    maxPerWindow: 5,
    maxBuckets: 100,
    bucketTtlMs: 50,
    now: clock.now,
  });

  limiter.allow(IP_A);
  limiter.allow(IP_B);
  assert.strictEqual(limiter.bucketCount(), 2);
  clock.advance(50); // both buckets idle past their TTL
  limiter.allow("203.0.113.12"); // triggers prune
  assert.strictEqual(limiter.bucketCount(), 1, "idle buckets pruned by TTL");
}

// 5. Per-client window limit.

{
  const clock = makeClock(1_000_000);
  const limiter = createPkdRegistrationLimiter({
    windowMs: 100,
    maxPerWindow: 2,
    maxBuckets: 10,
    bucketTtlMs: 1000,
    now: clock.now,
  });

  assert.strictEqual(limiter.allow(IP_A), true, "first registration allowed");
  assert.strictEqual(limiter.allow(IP_A), true, "second registration allowed");
  assert.strictEqual(limiter.allow(IP_A), false, "third registration within window throttled");
  assert.strictEqual(limiter.allow(IP_B), true, "other clients are not affected");

  clock.advance(100); // fixed window resets exactly at the boundary
  assert.strictEqual(limiter.allow(IP_A), true, "client allowed again after window reset");
}

// 6. HTTP contract via modular route (201 / 400 / 404).

{
  const clock = makeClock(1_000_000);
  const store = createPkd({ maxEntries: 2, ttlMs: 1000, now: clock.now });
  // Generous quota here: the limiter runs before validation, so the 400
  // checks below must not trip the throttle - 429 behavior is covered in section 7.
  const limiter = createPkdRegistrationLimiter({
    windowMs: 10_000,
    maxPerWindow: 50,
    maxBuckets: 10,
    bucketTtlMs: 60_000,
    now: clock.now,
  });

  const { app, routes } = createMockApp();
  pkdRoutes.setup(app, { pkd: store, requireAdmin: passAdmin, registerRateLimit: toMiddleware(limiter) });

  const registerChain = routes.post["/key/register"];
  assert.ok(Array.isArray(registerChain) && registerChain.length === 2,
    "register route must carry the limiter middleware before the handler");

  // valid registration -> 201, exact response shape
  const created = runChain(registerChain, mockReq({ body: { publicKey: "pub-one" } }));
  assert.strictEqual(created.statusCode, 201, "valid registration returns 201");
  assert.deepStrictEqual(Object.keys(created.body).sort(), ["created", "keyId", "publicKey"],
    "201 response shape preserved");
  assert.ok(KEY_ID_REGEX.test(created.body.keyId), "201 keyId is 128-bit hex");
  assert.strictEqual(created.body.publicKey, "pub-one");

  // lookup -> 200 with same public shape
  const found = runChain(routes.get["/key/:id"], mockReq({ id: created.body.keyId }));
  assert.strictEqual(found.statusCode, 200);
  assert.deepStrictEqual(Object.keys(found.body).sort(), ["created", "keyId", "publicKey"]);

  // validation -> stable 400s
  for (const body of [{}, { publicKey: 42 }, { publicKey: "x".repeat(257) }]) {
    const rejected = runChain(registerChain, mockReq({ body }));
    assert.strictEqual(rejected.statusCode, 400, `invalid body must return 400: ${JSON.stringify(body).slice(0, 40)}`);
  }
  assert.strictEqual(
    runChain(registerChain, mockReq({ body: { publicKey: 42 } })).body.error,
    "missing_public_key");
  assert.strictEqual(
    runChain(registerChain, mockReq({ body: { publicKey: "x".repeat(257) } })).body.error,
    "public_key_too_large");

  // rotate (admin) -> 200, response shape preserved
  const rotated = runChain(routes.put["/key/:id"],
    mockReq({ id: created.body.keyId, body: { publicKey: "pub-one-v2" } }));
  assert.strictEqual(rotated.statusCode, 200, "admin rotate works");
  assert.deepStrictEqual(Object.keys(rotated.body).sort(), ["keyId", "publicKey", "updated"]);
  assert.strictEqual(
    runChain(routes.put["/key/:id"], mockReq({ id: "missing", body: { publicKey: "k" } })).statusCode,
    404, "rotate unknown id -> 404");
  assert.strictEqual(
    runChain(routes.put["/key/:id"], mockReq({ id: created.body.keyId, body: {} })).statusCode,
    400, "rotate without key -> 400");

  // delete (admin) -> 200, then lookup 404
  const deleted = runChain(routes.delete["/key/:id"], mockReq({ id: created.body.keyId }));
  assert.strictEqual(deleted.statusCode, 200);
  assert.deepStrictEqual(deleted.body, { ok: true });
  assert.strictEqual(
    runChain(routes.get["/key/:id"], mockReq({ id: created.body.keyId })).statusCode,
    404, "deleted key lookup -> 404");
  assert.strictEqual(
    runChain(routes.delete["/key/:id"], mockReq({ id: created.body.keyId })).statusCode,
    404, "re-delete -> 404");

  // second registration for expiry/eviction lookups below
  const second = runChain(registerChain, mockReq({ body: { publicKey: "pub-two" } }));
  assert.strictEqual(second.statusCode, 201);

  // lookup after TTL expiry -> 404
  clock.advance(1000);
  const expired = runChain(routes.get["/key/:id"], mockReq({ id: second.body.keyId }));
  assert.strictEqual(expired.statusCode, 404, "expired key lookup -> 404");
  assert.strictEqual(expired.body.error, "key_not_found");

  // lookup after eviction -> 404 (bypass limiter by registering directly)
  const k1 = store.registerKey("evict-one");
  store.registerKey("evict-two");
  store.registerKey("evict-three"); // cap 2 -> evicts k1
  assert.strictEqual(
    runChain(routes.get["/key/:id"], mockReq({ id: k1.keyId })).statusCode,
    404, "evicted key lookup -> 404");
}

// 7. HTTP throttle: exhausted window -> stable 429, no store write.

{
  const clock = makeClock(1_000_000);
  const store = createPkd({ maxEntries: 10, ttlMs: 1000, now: clock.now });
  const limiter = createPkdRegistrationLimiter({
    windowMs: 10_000,
    maxPerWindow: 2,
    maxBuckets: 10,
    bucketTtlMs: 60_000,
    now: clock.now,
  });

  const { app, routes } = createMockApp();
  pkdRoutes.setup(app, { pkd: store, requireAdmin: passAdmin, registerRateLimit: toMiddleware(limiter) });
  const registerChain = routes.post["/key/register"];

  assert.strictEqual(runChain(registerChain, mockReq({ body: { publicKey: "pub-one" } })).statusCode, 201);
  assert.strictEqual(runChain(registerChain, mockReq({ body: { publicKey: "pub-two" } })).statusCode, 201);

  const throttled = runChain(registerChain, mockReq({ body: { publicKey: "pub-three" } }));
  assert.strictEqual(throttled.statusCode, 429, "throttled registration returns 429");
  assert.deepStrictEqual(throttled.body, { error: "rate_limited" }, "429 shape stable");
  assert.strictEqual(store.listKeys().length, 2, "throttled request must not write to the store");

  // other clients remain unaffected; window recovery is covered in section 5.
  assert.strictEqual(
    runChain(registerChain, mockReq({ body: { publicKey: "pub-other" }, ip: IP_B })).statusCode,
    201, "other client not throttled");
}

// 8. Modular route falls back to the shared default limiter.

{
  const store = createPkd({ maxEntries: 2, ttlMs: 1000, now: makeClock(0).now });
  const { app, routes } = createMockApp();
  pkdRoutes.setup(app, { pkd: store, requireAdmin: passAdmin });
  const chain = routes.post["/key/register"];
  assert.strictEqual(chain.length, 2, "default wiring keeps limiter middleware");
  assert.strictEqual(chain[0], pkdRegistrationRateLimit,
    "default register limiter is the shared production middleware");
}

// 9. Default middleware: trusted IP only, stable 429, env config.
// Runs last: the lazy default limiter reads env at first invocation.

{
  const saved = {
    TRUST_PROXY: process.env.TRUST_PROXY,
    PKD_REGISTER_WINDOW_MS: process.env.PKD_REGISTER_WINDOW_MS,
    PKD_REGISTER_MAX_PER_WINDOW: process.env.PKD_REGISTER_MAX_PER_WINDOW,
    PKD_REGISTER_MAX_BUCKETS: process.env.PKD_REGISTER_MAX_BUCKETS,
    PKD_REGISTER_BUCKET_TTL_MS: process.env.PKD_REGISTER_BUCKET_TTL_MS,
  };
  const restore = () => {
    for (const [name, value] of Object.entries(saved)) {
      if (value === undefined) delete process.env[name];
      else process.env[name] = value;
    }
  };

  try {
    delete process.env.TRUST_PROXY; // no trusted proxy: XFF must be ignored
    process.env.PKD_REGISTER_WINDOW_MS = "60000";
    process.env.PKD_REGISTER_MAX_PER_WINDOW = "1";
    process.env.PKD_REGISTER_MAX_BUCKETS = "100";
    process.env.PKD_REGISTER_BUCKET_TTL_MS = "120000";

    const first = { headers: { "x-forwarded-for": "192.0.2.1" }, socket: { remoteAddress: IP_A } };
    let nextCalled = false;
    pkdRegistrationRateLimit(first, createMockRes(), () => { nextCalled = true; });
    assert.strictEqual(nextCalled, true, "first request passes the default limiter");

    // Same socket IP, different spoofed XFF prefix: must hit the same bucket.
    const spoofed = { headers: { "x-forwarded-for": "192.0.2.99" }, socket: { remoteAddress: IP_A } };
    const res = createMockRes();
    pkdRegistrationRateLimit(spoofed, res, () => { throw new Error("must not call next"); });
    assert.strictEqual(res.statusCode, 429, "client-supplied XFF must not bypass the limiter");
    assert.deepStrictEqual(res.body, { error: "rate_limited" }, "default 429 shape stable");

    const other = { headers: {}, socket: { remoteAddress: IP_B } };
    let otherPassed = false;
    pkdRegistrationRateLimit(other, createMockRes(), () => { otherPassed = true; });
    assert.strictEqual(otherPassed, true, "distinct real client unaffected");
  } finally {
    restore();
  }
}

// 10. Env parsing fails safe - strict integers, never zero or infinite.

{
  const pkdDefaults = readPkdConfig({});
  assert.deepStrictEqual(pkdDefaults, { maxEntries: 10000, ttlMs: 2592000000 },
    "PKD env defaults");

  assert.strictEqual(readPkdConfig({ PKD_MAX_ENTRIES: "abc" }).maxEntries, 10000,
    "unparseable max entries -> default");
  assert.strictEqual(readPkdConfig({ PKD_MAX_ENTRIES: "10junk" }).maxEntries, 10000,
    "partially numeric max entries -> default (strict parsing)");
  assert.strictEqual(readPkdConfig({ PKD_MAX_ENTRIES: "10.5" }).maxEntries, 10000,
    "decimal max entries string -> default (strict parsing)");
  assert.strictEqual(readPkdConfig({ PKD_MAX_ENTRIES: 10.5 }).maxEntries, 10000,
    "numeric decimal max entries -> default (strict parsing)");
  assert.strictEqual(readPkdConfig({ PKD_MAX_ENTRIES: "" }).maxEntries, 10000,
    "empty max entries -> default");
  assert.strictEqual(readPkdConfig({ PKD_TTL_MS: "NaN" }).ttlMs, 2592000000,
    "NaN TTL string -> default");
  assert.strictEqual(readPkdConfig({ PKD_TTL_MS: "Infinity" }).ttlMs, 2592000000,
    "Infinity TTL string -> default");
  assert.strictEqual(readPkdConfig({ PKD_TTL_MS: "3600000.9" }).ttlMs, 2592000000,
    "decimal TTL string -> default (strict parsing)");
  assert.strictEqual(readPkdConfig({ PKD_MAX_ENTRIES: "0" }).maxEntries, 1,
    "zero max entries clamped to bound, never zero");
  assert.strictEqual(readPkdConfig({ PKD_MAX_ENTRIES: "-50" }).maxEntries, 1,
    "negative max entries clamped to bound");
  assert.strictEqual(readPkdConfig({ PKD_MAX_ENTRIES: "99999999999" }).maxEntries, 50000,
    "absurd max entries clamped to hard ceiling");
  assert.strictEqual(readPkdConfig({ PKD_TTL_MS: "0" }).ttlMs, 60000,
    "zero TTL clamped to minimum, never zero");
  assert.strictEqual(readPkdConfig({ PKD_TTL_MS: "99999999999999" }).ttlMs, 31536000000,
    "absurd TTL clamped to maximum");
  assert.strictEqual(readPkdConfig({ PKD_TTL_MS: "3600000" }).ttlMs, 3600000,
    "valid TTL honored");

  const limiterDefaults = readLimiterConfig({});
  assert.deepStrictEqual(limiterDefaults, {
    windowMs: 3600000, maxPerWindow: 30, maxBuckets: 10000, bucketTtlMs: 86400000,
  }, "limiter env defaults");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_MAX_PER_WINDOW: "0" }).maxPerWindow, 1,
    "zero per-window clamped, never zero (would disable registration)");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_MAX_PER_WINDOW: "30junk" }).maxPerWindow, 30,
    "partially numeric per-window -> default (strict parsing)");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_MAX_PER_WINDOW: "30.5" }).maxPerWindow, 30,
    "decimal per-window string -> default (strict parsing)");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_MAX_PER_WINDOW: 30.5 }).maxPerWindow, 30,
    "numeric decimal per-window -> default (strict parsing)");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_MAX_PER_WINDOW: "99999" }).maxPerWindow, 1000,
    "absurd per-window clamped to hard ceiling");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_MAX_BUCKETS: "-1" }).maxBuckets, 1,
    "negative bucket cap clamped, never unbounded");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_MAX_BUCKETS: "" }).maxBuckets, 10000,
    "empty bucket cap -> default");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_MAX_BUCKETS: "99999999" }).maxBuckets, 50000,
    "absurd bucket cap clamped to hard ceiling");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_WINDOW_MS: "junk" }).windowMs, 3600000,
    "unparseable window -> default");
  assert.strictEqual(readLimiterConfig({ PKD_REGISTER_WINDOW_MS: "60000x" }).windowMs, 3600000,
    "partially numeric window -> default (strict parsing)");
  assert.strictEqual(
    readLimiterConfig({ PKD_REGISTER_WINDOW_MS: "60000", PKD_REGISTER_BUCKET_TTL_MS: "1000" }).bucketTtlMs,
    60000, "bucket TTL never below the window");
}

// 11. Singleton module API preserved for production callers.

{
  const singleton = require("../pkd");
  for (const fn of ["registerKey", "getKey", "rotateKey", "deleteKey", "listKeys"]) {
    assert.strictEqual(typeof singleton[fn], "function", `singleton ${fn} preserved`);
  }
  const entry = singleton.registerKey("singleton-probe");
  assert.ok(KEY_ID_REGEX.test(entry.keyId), "singleton keeps 128-bit key IDs");
  assert.strictEqual(singleton.getKey(entry.keyId).publicKey, "singleton-probe");
  singleton.deleteKey(entry.keyId);
}

// 12. Inline server.js route carries the same limiter (wiring guard).

{
  const source = fs.readFileSync(path.join(__dirname, "..", "server.js"), "utf8");
  assert.ok(
    source.includes('require("./security/pkd_registration_limiter")'),
    "server.js must import the shared limiter");
  assert.ok(
    /app\.post\("\/key\/register",\s*pkdRegistrationRateLimit,/.test(source),
    "inline POST /key/register must apply pkdRegistrationRateLimit");
}

// 13. Constant-memory limiter: denied floods cannot grow client state.

{
  const clock = makeClock(1_000_000);
  const limiter = createPkdRegistrationLimiter({
    windowMs: 10_000,
    maxPerWindow: 2,
    maxBuckets: 10,
    bucketTtlMs: 60_000,
    now: clock.now,
  });

  assert.strictEqual(limiter.allow(IP_A), true);
  assert.strictEqual(limiter.allow(IP_A), true);
  assert.deepStrictEqual(limiter.stats(), { buckets: 1, tracked: 2 },
    "bucket holds only the fixed-window counter once the quota is spent");

  for (let i = 0; i < 5000; i++) {
    assert.strictEqual(limiter.allow(IP_A), false, "over-quota request denied");
  }
  assert.deepStrictEqual(limiter.stats(), { buckets: 1, tracked: 2 },
    "5000 denied requests leave per-client state unchanged (constant size)");
  assert.strictEqual(limiter.bucketCount(), 1, "denied flood creates no extra buckets");

  clock.advance(10_000); // fixed window resets exactly at the boundary
  assert.strictEqual(limiter.allow(IP_A), true, "client allowed again after window reset");
  assert.deepStrictEqual(limiter.stats(), { buckets: 1, tracked: 1 },
    "window reset restarts the counter deterministically");
}

console.log(
  "PASS pkd_bounds - entry cap/eviction, TTL-consistent list/delete, " +
  "rotate/delete, constant-memory fixed-window limiter buckets, per-client " +
  "429, 201/400/404 contract, strict env parsing"
);
