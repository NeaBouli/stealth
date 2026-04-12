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
const ID_REGEX = /^[a-z0-9][a-z0-9-]{0,28}[a-z0-9]$|^[a-z0-9]$/;

let customIds = {};

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
    fs.writeFileSync(IDS_FILE, JSON.stringify(customIds, null, 2));
  } catch (e) {
    console.error("[CUSTOM-ID] Save failed:", e.message);
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
  // Check availability (public)
  app.get("/custom-id/check", (req, res) => {
    const id = (req.query.id || "").toLowerCase().trim();
    if (!id) return res.status(400).json({ error: "missing_id" });
    res.json(isAvailable(id));
  });

  // Activate (from app via WS or direct API)
  app.post("/custom-id/activate", (req, res) => {
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

  app.post("/custom-id/purchase", async (req, res) => {
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

      // Hash password and store as pending (not yet activated)
      const pendingToken = crypto.randomBytes(32).toString("hex");

      const session = await stripe.checkout.sessions.create({
        line_items: [{ price: priceId, quantity: 1 }],
        mode: "payment",
        success_url: `https://stealthx.tech/payment-success.html?custom_id=${encodeURIComponent(check.id)}&token=${pendingToken}`,
        cancel_url: "https://stealthx.tech/wiki/custom-id.html",
        metadata: {
          type: "custom_id",
          custom_id: check.id,
          pending_token: pendingToken,
          password_hash: hashPassword(password).hash,
          password_salt: hashPassword(password).salt
        },
        payment_method_types: ["card", "klarna", "paypal", "link"]
      });

      console.log(`[CUSTOM-ID] Purchase session created ($${check.price / 100})`);
      res.json({ url: session.url, sessionId: session.id });
    } catch (err) {
      console.error("[CUSTOM-ID] Stripe error:", err.message);
      res.status(500).json({ error: "checkout_failed" });
    }
  });

  // Activate via token (from deep link after Stripe payment)
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

    // For token-based activation, we trust the purchase token
    if (!ID_REGEX.test(normalized) || normalized.length <= 2) {
      return res.status(400).json({ error: "invalid_id" });
    }

    // Register the ID with the device (no password needed for initial token activation)
    if (!customIds[key]) {
      customIds[key] = {
        deviceId,
        idLength: normalized.length,
        purchasedAt: new Date().toISOString(),
        tier: "premium_only",
        activationToken: token
      };
    } else {
      customIds[key].deviceId = deviceId;
    }
    saveIds();
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

module.exports = { isAvailable, activate, getPrice, resolve, setupRoutes };
