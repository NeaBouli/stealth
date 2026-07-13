/**
 * Custom Call ID system with HMAC-SHA256 privacy protection.
 *
 * IDs are stored as HMAC-SHA256(pepper, id) keys — never in cleartext.
 * Pepper = secret server key from env var ID_HASH_PEPPER (set in Railway).
 * DeviceIds remain cleartext (random strings, needed for call routing).
 * Passwords use PBKDF2-SHA512 with per-record salt (100k iterations).
 *
 * Pricing: 10+ chars = EUR 1, 5-9 chars = EUR 2, 3-4 chars = EUR 5, 1-2 chars = reserved
 */

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { writeJsonAtomic } = require("./utils/json_store");

const IDS_FILE     = process.env.IDS_FILE     || path.join(__dirname, "..", "data", "custom_ids.json");
const PENDING_FILE = process.env.PENDING_FILE || path.join(__dirname, "..", "data", "pending_activations.json");
const PENDING_TTL_MS = 60 * 60 * 1000; // 1 hour — Stripe redirect happens immediately
const ID_REGEX = /^[a-z0-9][a-z0-9-]{0,28}[a-z0-9]$|^[a-z0-9]$/;

let customIds = {};
// token -> { customId, passwordHash, passwordSalt, createdAt, stripeSessionId, paidAt }
const pendingActivations = new Map();

// ─── HMAC / Pepper ──────────────────────────────────────────

function getPepper() {
  const pepper = process.env.ID_HASH_PEPPER;
  return typeof pepper === "string" && pepper.length >= 32 ? pepper : null;
}

/**
 * Convert a cleartext custom ID to a storage key.
 * With pepper: HMAC-SHA256(pepper, normalizedId) → 64-char hex.
 * Without a strong pepper the custom-ID feature fails closed.
 */
function idToKey(normalizedId) {
  const pepper = getPepper();
  if (!pepper) return null;
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
  if (!getPepper()) {
    customIds = {};
    console.warn("[CUSTOM-ID] ID_HASH_PEPPER missing or too short — custom IDs disabled");
    return;
  }
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
    writeJsonAtomic(IDS_FILE, customIds);
  } catch (e) {
    console.error("[CUSTOM-ID] Save failed:", e.message);
  }
}

// ─── Pending activation storage (token-based) ─────────────────
// Replaces the broken flow that put password_hash in Stripe metadata.
// Tokens are opaque random bytes; the password hash stays server-side.
function loadPendingActivations() {
  if (!getPepper()) {
    console.warn("[CUSTOM-ID] Pending activations disabled without a strong ID_HASH_PEPPER");
    return;
  }
  try {
    if (!fs.existsSync(PENDING_FILE)) return;
    const raw = JSON.parse(fs.readFileSync(PENDING_FILE, "utf8"));
    const now = Date.now();
    let pruned = 0;
    let migrated = false;
    for (const [token, entry] of Object.entries(raw || {})) {
      const legacyId = typeof entry?.customId === "string" ? entry.customId.toLowerCase().trim() : null;
      const customIdKey = typeof entry?.customIdKey === "string" ? entry.customIdKey : (legacyId ? idToKey(legacyId) : null);
      if (entry && isHmacKey(customIdKey) && typeof entry.createdAt === "number" && now - entry.createdAt < PENDING_TTL_MS) {
        const { customId: _legacyCustomId, ...rest } = entry;
        pendingActivations.set(token, { ...rest, customIdKey, idLength: entry.idLength || legacyId?.length || null });
        if (legacyId) migrated = true;
      } else {
        pruned++;
      }
    }
    if (migrated) savePendingActivations();
    console.log(`[CUSTOM-ID] Loaded ${pendingActivations.size} pending activations (pruned ${pruned} stale)`);
  } catch (e) {
    console.warn("[CUSTOM-ID] Could not load pending activations:", e.message);
  }
}

function savePendingActivations() {
  try {
    const obj = {};
    const now = Date.now();
    for (const [token, entry] of pendingActivations) {
      if (now - entry.createdAt < PENDING_TTL_MS) obj[token] = entry;
    }
    writeJsonAtomic(PENDING_FILE, obj);
  } catch (e) {
    console.error("[CUSTOM-ID] Failed to persist pending activations:", e.message);
  }
}

function markPendingPaid(token, customId, stripeSessionId) {
  const pending = pendingActivations.get(token);
  const normalized = String(customId || "").toLowerCase().trim();
  if (!pending || !stripeSessionId || !stripeSessionId.startsWith("cs_")) return false;
  const key = idToKey(normalized);
  if (!key || pending.customIdKey !== key || pending.stripeSessionId !== stripeSessionId) return false;
  if (Date.now() - pending.createdAt > PENDING_TTL_MS) return false;
  pending.paidAt = pending.paidAt || new Date().toISOString();
  savePendingActivations();
  return true;
}

function revokeByStripeSession(stripeSessionId) {
  if (!stripeSessionId || !stripeSessionId.startsWith("cs_")) return { revokedIds: 0, revokedPending: 0 };
  let revokedIds = 0;
  let revokedPending = 0;
  for (const [key, entry] of Object.entries(customIds)) {
    if (entry?.stripeSessionId !== stripeSessionId) continue;
    delete customIds[key];
    revokedIds += 1;
  }
  for (const [token, entry] of pendingActivations) {
    if (entry?.stripeSessionId !== stripeSessionId) continue;
    pendingActivations.delete(token);
    revokedPending += 1;
  }
  if (revokedIds > 0) saveIds();
  if (revokedPending > 0) savePendingActivations();
  return { revokedIds, revokedPending };
}

/**
 * Auto-migrate cleartext keys to HMAC keys on startup.
 * Cleartext keys match ID_REGEX; HMAC keys are 64-char hex.
 * Only runs when pepper is available.
 */
function migrateToHmac() {
  const pepper = getPepper();
  if (!pepper) {
    console.warn("[CUSTOM-ID] ID_HASH_PEPPER missing or too short — migration skipped and feature disabled");
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
  if (len <= 4) return 500;  // EUR 5.00
  if (len <= 9) return 200;  // EUR 2.00
  return 100;                // EUR 1.00
}

// ─── Core functions ─────────────────────────────────────────

function isAvailable(id) {
  const normalized = id.toLowerCase().trim();
  if (!ID_REGEX.test(normalized)) return { available: false, error: "invalid_format" };
  if (normalized.length <= 2) return { available: false, error: "reserved" };
  const key = idToKey(normalized);
  if (!key) return { available: false, error: "privacy_not_configured" };
  if (customIds[key]) return { available: false, error: "taken" };
  const price = getPrice(normalized);
  return { available: true, price, id: normalized };
}

function activate(id, deviceId, password) {
  const normalized = id.toLowerCase().trim();
  if (!ID_REGEX.test(normalized)) return { success: false, error: "invalid_format" };
  if (normalized.length <= 2) return { success: false, error: "reserved" };

  const key = idToKey(normalized);
  if (!key) return { success: false, error: "privacy_not_configured" };
  const existing = customIds[key];
  if (!existing) return { success: false, error: "purchase_required" };
  if (!verifyPassword(password, existing.passwordHash, existing.passwordSalt)) {
    return { success: false, error: "wrong_password" };
  }
  existing.deviceId = deviceId;
  existing.lastTransfer = new Date().toISOString();
  saveIds();
  console.log(`[CUSTOM-ID] Transfer: ${key.substring(0, 12)}... -> ${deviceId}`);
  return { success: true, transferred: true };
}

/**
 * Resolve a custom ID to a deviceId (used by call routing).
 * Input: cleartext ID → HMAC lookup → return deviceId.
 */
function resolve(id) {
  if (!id) return null;
  const normalized = id.toLowerCase().trim();
  const key = idToKey(normalized);
  if (!key) return null;
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
    if (process.env.CUSTOM_ID_STRIPE_CHECKOUT_ENABLED !== "true") {
      return res.status(410).json({ error: "custom_id_checkout_disabled" });
    }
    if (!getPepper()) return res.status(503).json({ error: "custom_id_privacy_not_configured" });
    const secretKey = process.env.STRIPE_SECRET_KEY;
    if (!secretKey) return res.status(503).json({ error: "payments_disabled" });

    const { id, password } = req.body;
    const docType = req.body.docType === "invoice" ? "invoice" : "receipt";
    const billingCountry = String(req.body.billingCountry || "").trim().toUpperCase();
    const companyName = String(req.body.companyName || "").trim();
    const taxId = String(req.body.taxId || "").trim().toUpperCase();
    const customerEmail = String(req.body.customerEmail || "").trim().toLowerCase();
    if (!id || !password) return res.status(400).json({ error: "missing_fields" });
    if (password.length < 8) return res.status(400).json({ error: "password_too_short" });
    if (!/^[A-Z]{2}$/.test(billingCountry) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customerEmail)) {
      return res.status(400).json({ error: "invalid_billing_details" });
    }
    if (docType === "invoice" && (!companyName || companyName.length > 120 || !/^[A-Z0-9-]{5,24}$/.test(taxId))) {
      return res.status(400).json({ error: "invalid_business_details" });
    }

    const check = isAvailable(id);
    if (!check.available) return res.status(400).json(check);

    const priceId = PRICE_IDS[check.price];
    if (!priceId) return res.status(400).json({ error: "invalid_price" });
    const productKey = check.price === 500 ? "custom_id_ultra" : check.price === 200 ? "custom_id_short" : "custom_id_standard";

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
        customIdKey: idToKey(check.id),
        idLength: check.id.length,
        passwordHash,
        passwordSalt,
        createdAt: Date.now(),
        stripeSessionId: null,
        paidAt: null
      });

      const session = await stripe.checkout.sessions.create({
        line_items: [{ price: priceId, quantity: 1 }],
        mode: "payment",
        success_url: `https://stealthx.tech/payment-success.html?custom_id=${encodeURIComponent(check.id)}&token=${pendingToken}`,
        cancel_url: "https://stealthx.tech/wiki/custom-id.html",
        metadata: {
          type: "custom_id",
          tier: "custom_id",
          product: productKey,
          custom_id: check.id,
          pending_token: pendingToken,
          doc_type: docType,
          billing_country: billingCountry,
          company_name: docType === "invoice" ? companyName : "",
          tax_id: docType === "invoice" ? taxId : "",
          customer_email: customerEmail
        },
        customer_email: customerEmail,
        payment_method_types: ["card", "klarna", "link"]
      });

      const pending = pendingActivations.get(pendingToken);
      pending.stripeSessionId = session.id;
      savePendingActivations();

      console.log(`[CUSTOM-ID] Purchase session created (EUR ${check.price / 100})`);
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
  app.post("/custom-id/activate-token", customIdRateLimit, (req, res) => {
    const { id, deviceId, token } = req.body;
    if (!id || !deviceId || !token) {
      return res.status(400).json({ error: "missing_fields" });
    }

    const normalized = id.toLowerCase().trim();
    const key = idToKey(normalized);
    if (!key) return res.status(503).json({ error: "custom_id_privacy_not_configured" });

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
    if (pending.customIdKey !== key) {
      console.warn("[CUSTOM-ID] activate-token rejected: token/id mismatch");
      return res.status(400).json({ error: "token_id_mismatch" });
    }
    if (Date.now() - pending.createdAt > PENDING_TTL_MS) {
      pendingActivations.delete(token);
      savePendingActivations();
      return res.status(400).json({ error: "token_expired" });
    }
    if (!pending.paidAt || !pending.stripeSessionId) {
      console.warn("[CUSTOM-ID] activate-token rejected: Stripe payment not confirmed");
      return res.status(402).json({ error: "payment_not_confirmed" });
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
        stripeSessionId: pending.stripeSessionId,
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

module.exports = { isAvailable, activate, getPrice, resolve, markPendingPaid, revokeByStripeSession, setupRoutes };
