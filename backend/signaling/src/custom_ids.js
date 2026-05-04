/**
 * Custom Call ID system with HMAC-SHA256 privacy protection.
 *
 * IDs are stored as HMAC-SHA256(pepper, id) keys — never in cleartext.
 * Pepper = secret server key from env var ID_HASH_PEPPER (set in Railway).
 * DeviceIds remain cleartext (random strings, needed for call routing).
 * Passwords use PBKDF2-SHA512 with per-record salt (100k iterations).
 *
 * Pricing: 10+ chars = $1, 5-9 chars = $2, 3-4 chars = $5, 1-2 chars = reserved
 */

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const IDS_FILE = path.join(__dirname, "..", "data", "custom_ids.json");
const PENDING_FILE = path.join(__dirname, "..", "data", "pending_activations.json");
const PENDING_TTL_MS = 60 * 60 * 1000; // 1 hour — Stripe redirect happens immediately
const ID_REGEX = /^[a-z0-9][a-z0-9-]{0,28}[a-z0-9]$|^[a-z0-9]$/;

let customIds = {};
// token -> { customId, passwordHash, passwordSalt, createdAt }
const pendingActivations = new Map();

// ─── HMAC / Pepper ──────────────────────────────────────────

function getPepper() {
  return process.env.ID_HASH_PEPPER || null;
}

/**
 * Convert a cleartext custom ID to a storage key.
 * With pepper: HMAC-SHA256(pepper, normalizedId) → 64-char hex.
 * Without pepper (local dev): passthrough cleartext.
 */
function idToKey(normalizedId) {
  const pepper = getPepper();
  if (!pepper) return normalizedId;
  return crypto.createHmac("sha256", pepper).update(normalizedId).digest("hex");
}

/**
 * Check if a storage key looks like an HMAC (64-char hex).
 */
function isHmacKey(key) {
  return /^[0-9a-f]{64}$/.test(key);
}

// ─── Persistence ────────────────────────────────────────────

function loadIds() {
  try {
    customIds = JSON.parse(fs.readFileSync(IDS_FILE, "utf8"));
    console.log(`[CUSTOM-ID] Loaded ${Object.keys(customIds).length} custom IDs`);
  } catch (e) {
    customIds = {};
  }
  migrateToHmac();
}

function saveIds() {
  try {
    fs.mkdirSync(path.dirname(IDS_FILE), { recursive: true });
    // Atomic write to prevent corruption on concurrent saves (Fix CRIT-003).
    const tmp = IDS_FILE + ".tmp";
    fs.writeFileSync(tmp, JSON.stringify(customIds, null, 2));
    fs.renameSync(tmp, IDS_FILE);
  } catch (e) {
    console.error("[CUSTOM-ID] Save failed:", e.message);
  }
}

// ─── Pending activation storage (token-based) ─────────────────
// Replaces the broken flow that put password_hash in Stripe metadata.
// Tokens are opaque random bytes; the password hash stays server-side.
function loadPendingActivations() {
  try {
    if (!fs.existsSync(PENDING_FILE)) return;
    const raw = JSON.parse(fs.readFileSync(PENDING_FILE, "utf8"));
    const now = Date.now();
    let pruned = 0;
    for (const [token, entry] of Object.entries(raw || {})) {
      if (entry && typeof entry.createdAt === "number" && now - entry.createdAt < PENDING_TTL_MS) {
        pendingActivations.set(token, entry);
      } else {
        pruned++;
      }
    }
    console.log(`[CUSTOM-ID] Loaded ${pendingActivations.size} pending activations (pruned ${pruned} stale)`);
  } catch (e) {
    console.warn("[CUSTOM-ID] Could not load pending activations:", e.message);
  }
}

function savePendingActivations() {
  try {
    fs.mkdirSync(path.dirname(PENDING_FILE), { recursive: true });
    const obj = {};
    const now = Date.now();
    for (const [token, entry] of pendingActivations) {
      if (now - entry.createdAt < PENDING_TTL_MS) obj[token] = entry;
    }
    const tmp = PENDING_FILE + ".tmp";
    fs.writeFileSync(tmp, JSON.stringify(obj, null, 2));
    fs.renameSync(tmp, PENDING_FILE);
  } catch (e) {
    console.error("[CUSTOM-ID] Failed to persist pending activations:", e.message);
  }
}

/**
 * Auto-migrate cleartext keys to HMAC keys on startup.
 * Cleartext keys match ID_REGEX; HMAC keys are 64-char hex.
 * Only runs when pepper is available.
 */
function migrateToHmac() {
  const pepper = getPepper();
  if (!pepper) {
    console.warn("[CUSTOM-ID] ID_HASH_PEPPER not set — IDs stored in cleartext (set env var for production)");
    return;
  }

  const keys = Object.keys(customIds);
  let migrated = 0;
  for (const key of keys) {
    if (!isHmacKey(key) && ID_REGEX.test(key)) {
      const hmacKey = idToKey(key);
      const data = customIds[key];
      data.idLength = key.length; // preserve length for pricing
      customIds[hmacKey] = data;
      delete customIds[key];
      migrated++;
    }
  }
  if (migrated > 0) {
    saveIds();
    console.log(`[CUSTOM-ID] Migrated ${migrated} cleartext IDs to HMAC-SHA256 storage`);
  } else if (keys.length > 0) {
    console.log("[CUSTOM-ID] All IDs already HMAC-protected");
  }
}

// ─── Password hashing (PBKDF2-SHA512) ──────────────────────

function hashPassword(password, salt) {
  if (!salt) salt = crypto.randomBytes(16).toString("hex");
  const hash = crypto.pbkdf2Sync(password, salt, 100000, 64, "sha512").toString("hex");
  return { hash, salt };
}

function verifyPassword(password, storedHash, storedSalt) {
  const { hash } = hashPassword(password, storedSalt);
  return hash === storedHash;
}

// ─── Pricing ────────────────────────────────────────────────

function getPrice(id) {
  const len = id.length;
  if (len <= 2) return null; // reserved
  if (len <= 4) return 500;  // $5.00
  if (len <= 9) return 200;  // $2.00
  return 100;                // $1.00
}

// ─── Core functions ─────────────────────────────────────────

function isAvailable(id) {
  const normalized = id.toLowerCase().trim();
  if (!ID_REGEX.test(normalized)) return { available: false, error: "invalid_format" };
  if (normalized.length <= 2) return { available: false, error: "reserved" };
  const key = idToKey(normalized);
  if (customIds[key]) return { available: false, error: "taken" };
  const price = getPrice(normalized);
  return { available: true, price, id: normalized };
}

function activate(id, deviceId, password) {
  const normalized = id.toLowerCase().trim();
  if (!ID_REGEX.test(normalized)) return { success: false, error: "invalid_format" };
  if (normalized.length <= 2) return { success: false, error: "reserved" };

  const key = idToKey(normalized);
  const existing = customIds[key];
  if (existing) {
    // Already owned — check password for re-activation or transfer
    if (!verifyPassword(password, existing.passwordHash, existing.passwordSalt)) {
      return { success: false, error: "wrong_password" };
    }
    // Transfer to new device
    existing.deviceId = deviceId;
    existing.lastTransfer = new Date().toISOString();
    saveIds();
    console.log(`[CUSTOM-ID] Transfer: ${key.substring(0, 12)}... -> ${deviceId}`);
    return { success: true, transferred: true };
  }

  // New registration
  const { hash, salt } = hashPassword(password);
  customIds[key] = {
    deviceId,
    passwordHash: hash,
    passwordSalt: salt,
    idLength: normalized.length,
    purchasedAt: new Date().toISOString(),
    tier: "premium_only"
  };
  saveIds();
  console.log(`[CUSTOM-ID] Registered: ${key.substring(0, 12)}... -> ${deviceId}`);
  return { success: true, transferred: false };
}

/**
 * Resolve a custom ID to a deviceId (used by call routing).
 * Input: cleartext ID → HMAC lookup → return deviceId.
 */
function resolve(id) {
  if (!id) return null;
  const normalized = id.toLowerCase().trim();
  const key = idToKey(normalized);
  const entry = customIds[key];
  return (entry && entry.deviceId) ? entry.deviceId : null;
}

// ─── HTTP Routes ────────────────────────────────────────────

function setupRoutes(app, requireAdmin) {
  // Rate limit: 5 attempts per IP per 15 minutes for activate/transfer/reclaim
  const customIdLimits = new Map();
  function customIdRateLimit(req, res, next) {
    const ip = req.ip || req.connection.remoteAddress;
    const now = Date.now();
    if (!customIdLimits.has(ip)) customIdLimits.set(ip, []);
    const attempts = customIdLimits.get(ip);
    while (attempts.length > 0 && now - attempts[0] > 900000) attempts.shift(); // 15min window
    if (attempts.length >= 5) {
      return res.status(429).json({ error: "rate_limited", retry_after_seconds: 900 });
    }
    attempts.push(now);
    next();
  }

  // Check availability (public, no rate limit needed — read-only)
  app.get("/custom-id/check", (req, res) => {
    const id = (req.query.id || "").toLowerCase().trim();
    if (!id) return res.status(400).json({ error: "missing_id" });
    res.json(isAvailable(id));
  });

  // Activate (from app via WS or direct API) — rate limited
  app.post("/custom-id/activate", customIdRateLimit, (req, res) => {
    const { id, deviceId, password } = req.body;
    if (!id || !deviceId || !password) {
      return res.status(400).json({ error: "missing_fields" });
    }
    if (password.length < 8) {
      return res.status(400).json({ error: "password_too_short" });
    }
    res.json(activate(id, deviceId, password));
  });

  // Purchase via Stripe Checkout — LIVE account acct_1QJAg3BtrTFeYCjz
  const PRICE_IDS = {
    100: "price_1TLU2wBtrTFeYCjzHdkjKxHQ",  // 10+ chars = €1
    200: "price_1TLU34BtrTFeYCjzt86MqEZq",  // 5-9 chars = €2
    500: "price_1TLU35BtrTFeYCjzXs6Z3QyP"   // 3-4 chars = €5
  };

  app.post("/custom-id/purchase", customIdRateLimit, async (req, res) => {
    const secretKey = process.env.STRIPE_SECRET_KEY;
    if (!secretKey) return res.status(503).json({ error: "payments_disabled" });

    const { id, password } = req.body;
    if (!id || !password) return res.status(400).json({ error: "missing_fields" });
    if (password.length < 8) return res.status(400).json({ error: "password_too_short" });

    const check = isAvailable(id);
    if (!check.available) return res.status(400).json(check);

    const priceId = PRICE_IDS[check.price];
    if (!priceId) return res.status(400).json({ error: "invalid_price" });

    try {
      const stripe = require("stripe")(secretKey);

      // Fix HIGH-006 + token-validation follow-up (2026-04-16):
      // - Password hash is kept server-side (not in Stripe metadata).
      // - Token is a cryptographically random 32-byte value persisted
      //   to pending_activations.json with a 1h TTL.
      // - activate-token now validates the token against this store and
      //   copies passwordHash/passwordSalt into customIds on activation,
      //   so transfer-by-password actually works.
      const pendingToken = crypto.randomBytes(32).toString("hex");
      const { hash: passwordHash, salt: passwordSalt } = hashPassword(password);
      pendingActivations.set(pendingToken, {
        customId: check.id,
        passwordHash,
        passwordSalt,
        createdAt: Date.now()
      });
      savePendingActivations();

      const session = await stripe.checkout.sessions.create({
        line_items: [{ price: priceId, quantity: 1 }],
        mode: "payment",
        success_url: `https://stealthx.tech/payment-success.html?custom_id=${encodeURIComponent(check.id)}&token=${pendingToken}`,
        cancel_url: "https://stealthx.tech/wiki/custom-id.html",
        metadata: {
          type: "custom_id",
          custom_id: check.id,
          pending_token: pendingToken
        },
        payment_method_types: ["card", "klarna", "link"]
      });

      console.log(`[CUSTOM-ID] Purchase session created ($${check.price / 100})`);
      res.json({ url: session.url, sessionId: session.id });
    } catch (err) {
      console.error("[CUSTOM-ID] Stripe error:", err.message);
      res.status(500).json({ error: "checkout_failed" });
    }
  });

  // Activate via token (from deep link after Stripe payment)
  // Fix (2026-04-16): validate token against pending_activations + copy the
  // password hash into the ID record so subsequent transfers via /custom-id/activate
  // can verify ownership. Previously any caller could mint any free custom ID by
  // supplying an arbitrary token string.
  app.post("/custom-id/activate-token", (req, res) => {
    const { id, deviceId, token } = req.body;
    if (!id || !deviceId || !token) {
      return res.status(400).json({ error: "missing_fields" });
    }

    const normalized = id.toLowerCase().trim();
    const key = idToKey(normalized);

    // Check if this ID was already activated (token already used)
    if (customIds[key] && customIds[key].deviceId) {
      // Allow re-activation on same device
      if (customIds[key].deviceId === deviceId) {
        return res.json({ success: true, transferred: false, message: "already_active" });
      }
      return res.status(400).json({ error: "already_activated_on_other_device" });
    }

    if (!ID_REGEX.test(normalized) || normalized.length <= 2) {
      return res.status(400).json({ error: "invalid_id" });
    }

    // Validate the token: must exist in pending_activations, match the requested
    // custom_id, and still be within the 1h TTL window.
    const pending = pendingActivations.get(token);
    if (!pending) {
      console.warn("[CUSTOM-ID] activate-token rejected: unknown token (possibly replayed or expired)");
      return res.status(400).json({ error: "invalid_or_expired_token" });
    }
    if (pending.customId !== normalized) {
      console.warn("[CUSTOM-ID] activate-token rejected: token/id mismatch");
      return res.status(400).json({ error: "token_id_mismatch" });
    }
    if (Date.now() - pending.createdAt > PENDING_TTL_MS) {
      pendingActivations.delete(token);
      savePendingActivations();
      return res.status(400).json({ error: "token_expired" });
    }

    // Register the ID with the device + persist the password hash so the owner
    // can later transfer / re-claim via /custom-id/activate (password-based flow).
    if (!customIds[key]) {
      customIds[key] = {
        deviceId,
        idLength: normalized.length,
        purchasedAt: new Date().toISOString(),
        tier: "premium_only",
        activationToken: token,
        passwordHash: pending.passwordHash,
        passwordSalt: pending.passwordSalt
      };
    } else {
      customIds[key].deviceId = deviceId;
      customIds[key].passwordHash = pending.passwordHash;
      customIds[key].passwordSalt = pending.passwordSalt;
    }
    saveIds();

    // Token is single-use — prevent replay.
    pendingActivations.delete(token);
    savePendingActivations();

    console.log(`[CUSTOM-ID] Token activation: ${key.substring(0, 12)}... -> ${deviceId}`);
    res.json({ success: true, transferred: false });
  });

  // List all IDs (admin only) — shows HMAC keys, not cleartext
  if (requireAdmin) {
    app.get("/admin/custom-ids", requireAdmin, (req, res) => {
      const ids = Object.entries(customIds).map(([key, data]) => ({
        hmacKey: key.substring(0, 16) + "...",
        deviceId: data.deviceId,
        purchasedAt: data.purchasedAt,
        idLength: data.idLength || null
      }));
      res.json({ count: ids.length, hmacProtected: !!getPepper(), ids });
    });
  }

  // Resolve custom ID to deviceId (public — used by call routing)
  app.get("/custom-id/resolve", (req, res) => {
    const id = (req.query.id || "").toLowerCase().trim();
    if (!id) return res.status(400).json({ found: false, error: "missing_id" });
    const deviceId = resolve(id);
    if (deviceId) {
      res.json({ found: true, deviceId });
    } else {
      res.json({ found: false });
    }
  });

  console.log(`[CUSTOM-ID] Routes ready | HMAC: ${getPepper() ? "ENABLED" : "DISABLED (set ID_HASH_PEPPER)"}`);
}

loadIds();
loadPendingActivations();

module.exports = { isAvailable, activate, getPrice, resolve, setupRoutes };
