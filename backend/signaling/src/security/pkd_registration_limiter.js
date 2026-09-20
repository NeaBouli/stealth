"use strict";

/**
 * STX-08 - Bounded per-client registration limiter for POST /key/register.
 *
 * Shared by the effective inline route in server.js and the modular route in
 * routes/pkd.js, so both registration paths enforce the same buckets.
 *
 * The limiter itself is bounded: the per-client bucket map has a hard maximum
 * (PKD_REGISTER_MAX_BUCKETS) and prunes idle buckets after a TTL
 * (PKD_REGISTER_BUCKET_TTL_MS), so the protection cannot grow into an
 * unbounded map. Each bucket is constant-size fixed-window state
 * ({ windowStart, count, lastSeen }) - three numbers per client, independent
 * of how many requests that client makes. All values have safe defaults and
 * use strict integer parsing with clamping into valid ranges - invalid env
 * input never creates zero or infinite limits.
 *
 *   PKD_REGISTER_WINDOW_MS     fixed window (Default 1h, gueltig 1000..86400000)
 *   PKD_REGISTER_MAX_PER_WINDOW Registrierungen je Client im Fenster (Default 30, 1..1000)
 *   PKD_REGISTER_MAX_BUCKETS   harte Obergrenze der Bucket-Map (Default 10000, 1..50000)
 *   PKD_REGISTER_BUCKET_TTL_MS Leerlauf-TTL eines Buckets (Default 24h, >= windowMs)
 *
 * Tests nutzen die injizierbare Factory:
 *   const { createPkdRegistrationLimiter } = require("./pkd_registration_limiter");
 *   const limiter = createPkdRegistrationLimiter({ maxPerWindow: 2, now: () => fakeNow });
 */

const { getClientIp } = require("../middleware/ip");

const DEFAULT_WINDOW_MS = 60 * 60 * 1000; // 1 Stunde
const MIN_WINDOW_MS = 1000;
const MAX_WINDOW_MS = 24 * 60 * 60 * 1000; // 1 Tag

const DEFAULT_MAX_PER_WINDOW = 30;
const MIN_PER_WINDOW = 1;
const MAX_PER_WINDOW = 1000;

const DEFAULT_MAX_BUCKETS = 10000;
const MIN_MAX_BUCKETS = 1;
const HARD_MAX_BUCKETS = 50000;

const DEFAULT_BUCKET_TTL_MS = 24 * 60 * 60 * 1000; // 24 Stunden
const MAX_BUCKET_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 Tage

/**
 * Strikte Ganzzahl-Parsung: nur vollstaendig numerische Strings (optional
 * mit Vorzeichen) und endliche Ganzzahlen werden akzeptiert. Teilweise numerische
 * Strings ("10junk"), Dezimal-Strings ("10.5"), leere Strings, NaN und
 * Unendlich liefern null und fallen damit auf den Fallback zurueck.
 */
function parseStrictInt(value) {
  if (typeof value === "number") {
    return Number.isInteger(value) ? value : null;
  }
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!/^[+-]?\d+$/.test(trimmed)) return null;
  const parsed = Number(trimmed);
  return Number.isInteger(parsed) ? parsed : null;
}

/**
 * Klemmt einen konfigurierbaren Wert in [min, max]. Ungueltige Eingaben
 * (undefined, nicht ganzzahlig parsebar, NaN, nicht endlich) fallen auf
 * fallback zurueck; gueltige Ganzzahlen werden geklemmt.
 */
function clampBound(value, fallback, min, max) {
  const parsed = parseStrictInt(value);
  if (parsed === null) return fallback;
  return Math.min(Math.max(parsed, min), max);
}

function readLimiterConfig(env) {
  const source = env || process.env;
  const windowMs = clampBound(source.PKD_REGISTER_WINDOW_MS, DEFAULT_WINDOW_MS, MIN_WINDOW_MS, MAX_WINDOW_MS);
  return {
    windowMs,
    maxPerWindow: clampBound(source.PKD_REGISTER_MAX_PER_WINDOW, DEFAULT_MAX_PER_WINDOW, MIN_PER_WINDOW, MAX_PER_WINDOW),
    maxBuckets: clampBound(source.PKD_REGISTER_MAX_BUCKETS, DEFAULT_MAX_BUCKETS, MIN_MAX_BUCKETS, HARD_MAX_BUCKETS),
    bucketTtlMs: clampBound(source.PKD_REGISTER_BUCKET_TTL_MS, DEFAULT_BUCKET_TTL_MS, windowMs, MAX_BUCKET_TTL_MS)
  };
}

function createPkdRegistrationLimiter(options) {
  const opts = options || {};
  const now = typeof opts.now === "function" ? opts.now : () => Date.now();
  // Factory-Schranken sichern nur gegen 0/unendlich ab, damit Tests kurze
  // Fenster injizieren koennen; die Env-Konfiguration (readLimiterConfig)
  // zieht zusaetzlich strengere Betriebsgrenzen (MIN_WINDOW_MS u.a.).
  const windowMs = clampBound(opts.windowMs, DEFAULT_WINDOW_MS, 1, MAX_WINDOW_MS);
  const maxPerWindow = clampBound(opts.maxPerWindow, DEFAULT_MAX_PER_WINDOW, 1, MAX_PER_WINDOW);
  const maxBuckets = clampBound(opts.maxBuckets, DEFAULT_MAX_BUCKETS, 1, HARD_MAX_BUCKETS);
  const bucketTtlMs = clampBound(opts.bucketTtlMs, DEFAULT_BUCKET_TTL_MS, windowMs, MAX_BUCKET_TTL_MS);

  // ip -> konstantes Fixed-Window-State { windowStart, count, lastSeen }.
  // Drei Zahlen pro Client, unabhaengig von der Request-Anzahl. Die
  // Insertionsreihenfolge wird bei jedem Zugriff erneuert, die Verdraengung
  // trifft den aeltesten Bucket (LRU).
  const buckets = new Map();
  let lastObservedAt = Number.NEGATIVE_INFINITY;

  function currentTime() {
    const observed = now();
    if (!Number.isFinite(observed)) throw new TypeError("PKD limiter clock must return a finite number");
    lastObservedAt = Math.max(lastObservedAt, observed);
    return lastObservedAt;
  }

  function prune(at) {
    // Map order is LRU order and timestamps are monotonic. Idle buckets form a
    // prefix, so pruning is amortized O(1) rather than a full-map scan per
    // unauthenticated request.
    while (buckets.size > 0) {
      const oldest = buckets.entries().next().value;
      if (!oldest || at - oldest[1].lastSeen < bucketTtlMs) break;
      buckets.delete(oldest[0]);
    }
  }

  /**
   * Gibt true zurueck, wenn der Client eine weitere Registrierung im Fenster
   * darf; false, wenn das Kontingent erreicht ist (HTTP 429). Das Fenster
   * beginnt mit dem ersten Request des Clients und wird exakt an der
   * Fenstergrenze (at - windowStart >= windowMs) zurueckgesetzt.
   */
  function allow(ip) {
    const at = currentTime();
    prune(at);

    let bucket = buckets.get(ip);
    if (!bucket) {
      if (buckets.size >= maxBuckets) {
        const oldest = buckets.keys().next();
        if (!oldest.done) buckets.delete(oldest.value);
      }
      bucket = { windowStart: at, count: 0, lastSeen: at };
    } else {
      buckets.delete(ip); // LRU: als zuletzt genutzt neu einfuegen
    }
    buckets.set(ip, bucket);

    if (at - bucket.windowStart >= windowMs) {
      bucket.windowStart = at;
      bucket.count = 0;
    }
    bucket.lastSeen = at;
    if (bucket.count >= maxPerWindow) return false;

    bucket.count += 1;
    return true;
  }

  function bucketCount() {
    return buckets.size;
  }

  /**
   * Schmale Introspektion fuer Tests und Debugging: liefert nur aggregate
   * Zahlen (Bucket-Anzahl, Summe der Fenster-Zaehler) - keine IPs oder
   * andere Client-Daten.
   */
  function stats() {
    let tracked = 0;
    for (const bucket of buckets.values()) tracked += bucket.count;
    return { buckets: buckets.size, tracked };
  }

  return {
    allow,
    bucketCount,
    stats,
    config: { windowMs, maxPerWindow, maxBuckets, bucketTtlMs }
  };
}

// Produktions-Singleton, lazy aus Env erstellt - ein gemeinsamer Bucket-
// Speicher fuer die Inline-Route (server.js) und die modulare Route
// (routes/pkd.js).
let defaultLimiter = null;

function getDefaultLimiter() {
  if (!defaultLimiter) defaultLimiter = createPkdRegistrationLimiter(readLimiterConfig());
  return defaultLimiter;
}

/**
 * Express-Middleware: vertrauenswuerdige Client-IP aus middleware/ip (kein
 * rohes client-seitiges X-Forwarded-For), stabile 429-JSON-Antwort.
 */
function pkdRegistrationRateLimit(req, res, next) {
  const ip = getClientIp(req) || "unknown";
  if (!getDefaultLimiter().allow(ip)) {
    return res.status(429).json({ error: "rate_limited" });
  }
  next();
}

module.exports = {
  createPkdRegistrationLimiter,
  readLimiterConfig,
  getDefaultLimiter,
  pkdRegistrationRateLimit
};
