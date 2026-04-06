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

  console.log("[CUSTOM-ID] Routes: GET /custom-id/check, POST /custom-id/activate");
}

loadIds();

module.exports = { isAvailable, activate, getPrice, setupRoutes };
