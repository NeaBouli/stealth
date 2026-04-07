/**
 * Custom Call ID system.
 * Users purchase a custom alphanumeric ID (e.g. "marco", "trump")
 * protected by a password for device migration.
 *
 * Pricing: 10+ chars = $1, 5-9 chars = $2, 3-4 chars = $5, 1-2 chars = reserved
 */

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const IDS_FILE = path.join(__dirname, "..", "data", "custom_ids.json");
const ID_REGEX = /^[a-z0-9][a-z0-9-]{0,28}[a-z0-9]$|^[a-z0-9]$/;

let customIds = {};

function loadIds() {
  try {
    customIds = JSON.parse(fs.readFileSync(IDS_FILE, "utf8"));
    console.log(`[CUSTOM-ID] Loaded ${Object.keys(customIds).length} custom IDs`);
  } catch (e) {
    customIds = {};
  }
}

function saveIds() {
  try {
    fs.mkdirSync(path.dirname(IDS_FILE), { recursive: true });
    fs.writeFileSync(IDS_FILE, JSON.stringify(customIds, null, 2));
  } catch (e) {
    console.error("[CUSTOM-ID] Save failed:", e.message);
  }
}

function hashPassword(password, salt) {
  if (!salt) salt = crypto.randomBytes(16).toString("hex");
  const hash = crypto.pbkdf2Sync(password, salt, 100000, 64, "sha512").toString("hex");
  return { hash, salt };
}

function verifyPassword(password, storedHash, storedSalt) {
  const { hash } = hashPassword(password, storedSalt);
  return hash === storedHash;
}

function getPrice(id) {
  const len = id.length;
  if (len <= 2) return null; // reserved
  if (len <= 4) return 500;  // $5.00
  if (len <= 9) return 200;  // $2.00
  return 100;                // $1.00
}

function isAvailable(id) {
  const normalized = id.toLowerCase().trim();
  if (!ID_REGEX.test(normalized)) return { available: false, error: "invalid_format" };
  if (normalized.length <= 2) return { available: false, error: "reserved" };
  if (customIds[normalized]) return { available: false, error: "taken" };
  const price = getPrice(normalized);
  return { available: true, price, id: normalized };
}

function activate(id, deviceId, password) {
  const normalized = id.toLowerCase().trim();
  if (!ID_REGEX.test(normalized)) return { success: false, error: "invalid_format" };
  if (normalized.length <= 2) return { success: false, error: "reserved" };

  const existing = customIds[normalized];
  if (existing) {
    // Already owned — check password for re-activation or transfer
    if (!verifyPassword(password, existing.passwordHash, existing.passwordSalt)) {
      return { success: false, error: "wrong_password" };
    }
    // Transfer to new device
    existing.deviceId = deviceId;
    existing.lastTransfer = new Date().toISOString();
    saveIds();
    console.log(`[CUSTOM-ID] Transfer: ${normalized} -> ${deviceId}`);
    return { success: true, transferred: true };
  }

  // New registration
  const { hash, salt } = hashPassword(password);
  customIds[normalized] = {
    deviceId,
    passwordHash: hash,
    passwordSalt: salt,
    purchasedAt: new Date().toISOString(),
    tier: "premium_only"
  };
  saveIds();
  console.log(`[CUSTOM-ID] Registered: ${normalized} -> ${deviceId}`);
  return { success: true, transferred: false };
}

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

  // Purchase via Stripe Checkout
  const PRICE_IDS = {
    100: "price_1TJITIBcyoLtm3FA0qZyTL5O",  // 10+ chars = $1
    200: "price_1TJITKBcyoLtm3FARalsHHII",  // 5-9 chars = $2
    500: "price_1TJITNBcyoLtm3FAlvw1HlRY"   // 3-4 chars = $5
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
        payment_method_types: ["card", "klarna", "sepa_debit"]
      });

      console.log(`[CUSTOM-ID] Purchase session created: ${check.id} ($${check.price / 100})`);
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

    // Check if this ID was already activated (token already used)
    if (customIds[normalized] && customIds[normalized].deviceId) {
      // Allow re-activation on same device
      if (customIds[normalized].deviceId === deviceId) {
        return res.json({ success: true, transferred: false, message: "already_active" });
      }
      return res.status(400).json({ error: "already_activated_on_other_device" });
    }

    // For token-based activation, we trust the purchase token
    // The ID should have been reserved during Stripe checkout
    if (!ID_REGEX.test(normalized) || normalized.length <= 2) {
      return res.status(400).json({ error: "invalid_id" });
    }

    // Register the ID with the device (no password needed for initial token activation)
    if (!customIds[normalized]) {
      customIds[normalized] = {
        deviceId,
        purchasedAt: new Date().toISOString(),
        tier: "premium_only",
        activationToken: token
      };
    } else {
      customIds[normalized].deviceId = deviceId;
    }
    saveIds();
    console.log(`[CUSTOM-ID] Token activation: ${normalized} -> ${deviceId}`);
    res.json({ success: true, transferred: false });
  });

  // List all IDs (admin only)
  if (requireAdmin) {
    app.get("/admin/custom-ids", requireAdmin, (req, res) => {
      const ids = Object.entries(customIds).map(([id, data]) => ({
        id,
        deviceId: data.deviceId,
        purchasedAt: data.purchasedAt,
        length: id.length,
        price: getPrice(id)
      }));
      res.json({ count: ids.length, ids });
    });
  }

  // Resolve custom ID to deviceId (public — used by call routing)
  app.get("/custom-id/resolve", (req, res) => {
    const id = (req.query.id || "").toLowerCase().trim();
    if (!id) return res.status(400).json({ found: false, error: "missing_id" });
    const deviceId = resolve(id);
    if (deviceId) {
      res.json({ found: true, deviceId, id });
    } else {
      res.json({ found: false, id });
    }
  });

  console.log("[CUSTOM-ID] Routes: GET /custom-id/check, GET /custom-id/resolve, POST /custom-id/activate, POST /custom-id/purchase");
}

loadIds();

/**
 * Resolve a custom ID to a deviceId.
 * Returns the deviceId string if found, or null.
 */
function resolve(id) {
  if (!id) return null;
  const normalized = id.toLowerCase().trim();
  const entry = customIds[normalized];
  return (entry && entry.deviceId) ? entry.deviceId : null;
}

module.exports = { isAvailable, activate, getPrice, resolve, setupRoutes };
