/**
 * Sold Activation Codes Store
 *
 * Persists codes generated from Stripe purchases to data/sold_codes.json.
 * Each entry includes buyer email + Stripe session ID for audit trail.
 *
 * Integration: server.js loads sold codes on startup and merges them into
 * the main `activationCodes` array used by the ACTIVATE_CODE WebSocket handler.
 * New codes (from runtime Stripe webhooks) are appended both to the on-disk
 * JSON and to the in-memory array so they work immediately without restart.
 */

const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");
const { readFileSync, existsSync } = require("fs");

const SOLD_FILE = process.env.SOLD_CODES_FILE ||
  path.join(__dirname, "..", "..", "data", "sold_codes.json");

function load() {
  try {
    if (existsSync(SOLD_FILE)) {
      const data = JSON.parse(readFileSync(SOLD_FILE, "utf8"));
      const codes = Array.isArray(data.codes) ? data.codes : [];
      console.log(`[SOLD-CODES] Loaded ${codes.length} sold codes from ${SOLD_FILE}`);
      return codes;
    }
  } catch (e) {
    console.error("[SOLD-CODES] Load failed:", e.message);
  }
  return [];
}

function save(codes) {
  try {
    writeJsonAtomic(SOLD_FILE, { codes });
  } catch (e) {
    console.error("[SOLD-CODES] Save failed:", e.message);
    throw e;
  }
}

function mergeIntoActivationCodes(entry, activationCodesRef) {
  if (!Array.isArray(activationCodesRef)) return;
  const alreadyPresent = activationCodesRef.some(c => c.code === entry.code);
  if (alreadyPresent) return;
  activationCodesRef.push({
    code: entry.code,
    tier: entry.tier,
    maxUses: entry.maxUses,
    currentUses: entry.currentUses,
    usedBy: entry.usedBy
  });
}

/**
 * Record a new sold code.
 * @param {Object} params
 * @param {string} params.code - Activation code (e.g. "PREM-XXXX-XXXX-XXXX")
 * @param {string} params.tier - "pro" or "premium"
 * @param {string} params.email - Buyer email
 * @param {string} params.stripeSessionId - Stripe checkout session ID
 * @param {string} [params.productKey] - pro_monthly / premium_monthly / premium_lifetime
 * @param {Array}  [params.activationCodesRef] - Live reference to server.js activationCodes array
 * @returns {Object} The stored entry (also in activationCodes format)
 */
function recordSale({ code, tier, email, stripeSessionId, productKey, activationCodesRef }) {
  const existing = load();
  if (stripeSessionId) {
    const existingEntry = existing.find(c => c.stripeSessionId === stripeSessionId);
    if (existingEntry) {
      mergeIntoActivationCodes(existingEntry, activationCodesRef);
      console.log(`[SOLD-CODES] Existing sale reused for Stripe session: ${stripeSessionId}`);
      return existingEntry;
    }
  }

  const entry = {
    code,
    tier,
    maxUses: 2,
    currentUses: 0,
    usedBy: [],
    // Metadata (ignored by ACTIVATE_CODE handler, used for audit)
    email,
    stripeSessionId: stripeSessionId || null,
    productKey: productKey || null,
    createdAt: new Date().toISOString(),
    emailDelivery: {
      status: "pending",
      attempts: 0,
      lastAttemptAt: null,
      deliveredAt: null,
      lastError: null
    },
    used: false
  };

  existing.push(entry);
  save(existing);

  // Live-merge into the server's activationCodes array so the new code
  // is immediately usable by the ACTIVATE_CODE handler without restart.
  mergeIntoActivationCodes(entry, activationCodesRef);

  console.log(`[SOLD-CODES] Recorded: ${code} (${tier}) -> ${email}`);
  return entry;
}

function updateEmailDelivery(stripeSessionId, delivery) {
  if (!stripeSessionId) return null;
  const existing = load();
  const index = existing.findIndex(c => c.stripeSessionId === stripeSessionId);
  if (index === -1) return null;

  const current = existing[index].emailDelivery || {};
  existing[index] = {
    ...existing[index],
    emailDelivery: {
      status: delivery.status,
      attempts: typeof delivery.attempts === "number" ? delivery.attempts : (current.attempts || 0),
      lastAttemptAt: delivery.lastAttemptAt || current.lastAttemptAt || null,
      deliveredAt: delivery.deliveredAt || current.deliveredAt || null,
      lastError: delivery.lastError || null
    }
  };
  save(existing);
  return existing[index];
}

/**
 * Returns all sold codes in the activationCodes-compatible shape
 * (code, tier, maxUses, currentUses, usedBy) for initial merge at startup.
 */
function loadAsActivationCodes() {
  return load().map(c => ({
    code: c.code,
    tier: c.tier,
    maxUses: c.maxUses || 2,
    currentUses: c.currentUses || 0,
    usedBy: Array.isArray(c.usedBy) ? c.usedBy : []
  }));
}

module.exports = { load, save, recordSale, updateEmailDelivery, loadAsActivationCodes };
