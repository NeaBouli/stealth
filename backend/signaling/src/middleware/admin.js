"use strict";

const crypto = require("crypto");

const DEFAULT_WINDOW_MS = 5 * 60 * 1000;
const DEFAULT_MAX_FAILURES = 10;
const DEFAULT_MAX_BUCKETS = 10000;
const DEFAULT_BUCKET_TTL_MS = 30 * 60 * 1000;
const DEFAULT_MAX_CREDENTIAL_LENGTH = 512;

function parseStrictInt(value) {
  if (typeof value === "number") return Number.isInteger(value) ? value : null;
  if (typeof value !== "string" || !/^[+-]?\d+$/.test(value.trim())) return null;
  const parsed = Number(value.trim());
  return Number.isInteger(parsed) ? parsed : null;
}

function clampInt(value, fallback, minimum, maximum) {
  const parsed = parseStrictInt(value);
  const fallbackParsed = parseStrictInt(fallback);
  const candidate = parsed === null
    ? (fallbackParsed === null ? minimum : fallbackParsed)
    : parsed;
  return Math.min(Math.max(candidate, minimum), maximum);
}

function readAdminAuthConfig(env = process.env) {
  const windowMs = clampInt(env.ADMIN_AUTH_WINDOW_MS, DEFAULT_WINDOW_MS, 1000, 24 * 60 * 60 * 1000);
  return {
    windowMs,
    maxFailures: clampInt(env.ADMIN_AUTH_MAX_FAILURES, DEFAULT_MAX_FAILURES, 1, 1000),
    maxBuckets: clampInt(env.ADMIN_AUTH_MAX_BUCKETS, DEFAULT_MAX_BUCKETS, 1, 50000),
    bucketTtlMs: clampInt(
      env.ADMIN_AUTH_BUCKET_TTL_MS,
      DEFAULT_BUCKET_TTL_MS,
      windowMs,
      30 * 24 * 60 * 60 * 1000
    ),
    maxCredentialLength: clampInt(
      env.ADMIN_AUTH_MAX_CREDENTIAL_LENGTH,
      DEFAULT_MAX_CREDENTIAL_LENGTH,
      32,
      4096
    )
  };
}

function digest(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest();
}

function timingSafeCredentialMatch(provided, expectedDigest) {
  return crypto.timingSafeEqual(digest(provided), expectedDigest);
}

function createAdminFailureLimiter(options = {}) {
  const now = typeof options.now === "function" ? options.now : () => Date.now();
  const windowMs = clampInt(options.windowMs, DEFAULT_WINDOW_MS, 1, 24 * 60 * 60 * 1000);
  const maxFailures = clampInt(options.maxFailures, DEFAULT_MAX_FAILURES, 1, 1000);
  const maxBuckets = clampInt(options.maxBuckets, DEFAULT_MAX_BUCKETS, 1, 50000);
  const bucketTtlMs = clampInt(
    options.bucketTtlMs,
    DEFAULT_BUCKET_TTL_MS,
    windowMs,
    30 * 24 * 60 * 60 * 1000
  );
  const buckets = new Map();
  let lastObservedAt = Number.NEGATIVE_INFINITY;

  function currentTime() {
    const observed = now();
    if (!Number.isFinite(observed)) throw new TypeError("admin auth clock must return a finite number");
    lastObservedAt = Math.max(lastObservedAt, observed);
    return lastObservedAt;
  }

  function prune(at) {
    while (buckets.size > 0) {
      const oldest = buckets.entries().next().value;
      if (!oldest || at - oldest[1].lastSeen < bucketTtlMs) break;
      buckets.delete(oldest[0]);
    }
  }

  function recordFailure(clientKey) {
    const at = currentTime();
    prune(at);

    let bucket = buckets.get(clientKey);
    if (!bucket) {
      if (buckets.size >= maxBuckets) {
        const oldest = buckets.keys().next();
        if (!oldest.done) buckets.delete(oldest.value);
      }
      bucket = { windowStart: at, count: 0, lastSeen: at };
    } else {
      buckets.delete(clientKey);
    }
    buckets.set(clientKey, bucket);

    if (at - bucket.windowStart >= windowMs) {
      bucket.windowStart = at;
      bucket.count = 0;
    }
    bucket.lastSeen = at;

    if (bucket.count >= maxFailures) {
      return {
        limited: true,
        retryAfterSeconds: Math.max(1, Math.ceil((bucket.windowStart + windowMs - at) / 1000))
      };
    }
    bucket.count += 1;
    return { limited: false, retryAfterSeconds: 0 };
  }

  function stats() {
    let failures = 0;
    for (const bucket of buckets.values()) failures += bucket.count;
    return { buckets: buckets.size, failures };
  }

  return {
    recordFailure,
    stats,
    config: { windowMs, maxFailures, maxBuckets, bucketTtlMs }
  };
}

function makeRequireAdmin(adminApiKey, options = {}) {
  const envConfig = readAdminAuthConfig(options.env || process.env);
  const windowMs = clampInt(options.windowMs, envConfig.windowMs, 1, 24 * 60 * 60 * 1000);
  const config = {
    windowMs,
    maxFailures: clampInt(options.maxFailures, envConfig.maxFailures, 1, 1000),
    maxBuckets: clampInt(options.maxBuckets, envConfig.maxBuckets, 1, 50000),
    bucketTtlMs: clampInt(
      options.bucketTtlMs,
      envConfig.bucketTtlMs,
      windowMs,
      30 * 24 * 60 * 60 * 1000
    ),
    maxCredentialLength: clampInt(
      options.maxCredentialLength,
      envConfig.maxCredentialLength,
      32,
      4096
    )
  };
  const expectedDigest = typeof adminApiKey === "string" && adminApiKey.length > 0
    ? digest(adminApiKey)
    : null;
  const failureLimiter = options.failureLimiter || createAdminFailureLimiter({
    now: options.now,
    windowMs: config.windowMs,
    maxFailures: config.maxFailures,
    maxBuckets: config.maxBuckets,
    bucketTtlMs: config.bucketTtlMs
  });
  const resolveClientIp = typeof options.getClientIp === "function"
    ? options.getClientIp
    : req => req.ip || req.socket?.remoteAddress || req.connection?.remoteAddress || "unknown";

  function clientBucketKey(req) {
    let value = "unknown";
    try {
      value = String(resolveClientIp(req) || "unknown");
    } catch {
      value = "unknown";
    }
    return digest(value).toString("hex");
  }

  function requireAdmin(req, res, next) {
    if (!expectedDigest) {
      return res.status(403).json({ error: "admin_api_disabled" });
    }

    const provided = req.headers?.["x-admin-key"];
    const validShape = typeof provided === "string"
      && provided.length > 0
      && provided.length <= config.maxCredentialLength;
    if (validShape && timingSafeCredentialMatch(provided, expectedDigest)) {
      return next();
    }

    const failure = failureLimiter.recordFailure(clientBucketKey(req));
    if (failure.limited) {
      if (typeof res.setHeader === "function") {
        res.setHeader("Retry-After", String(failure.retryAfterSeconds));
      }
      return res.status(429).json({ error: "rate_limited" });
    }
    return res.status(401).json({ error: "unauthorized" });
  }

  requireAdmin.stats = () => failureLimiter.stats();
  return requireAdmin;
}

module.exports = {
  createAdminFailureLimiter,
  makeRequireAdmin,
  readAdminAuthConfig,
  timingSafeCredentialMatch
};
