/**
 * Sold Activation Codes Store
 *
 * Persists codes generated from Stripe purchases to data/sold_codes.json.
 * Entries contain technical payment and delivery state, never buyer email.
 *
 * Integration: server.js loads sold codes on startup and merges them into
 * the main `activationCodes` array used by the ACTIVATE_CODE WebSocket handler.
 * New codes (from runtime Stripe webhooks) are appended both to the on-disk
 * JSON and to the in-memory array so they work immediately without restart.
 */

const path = require("path");
const crypto = require("crypto");
const fs = require("fs");
const { writeJsonAtomic } = require("../utils/json_store");

const SOLD_FILE = process.env.SOLD_CODES_FILE ||
  path.join(__dirname, "..", "..", "data", "sold_codes.json");
const SOLD_LOCK_FILE = `${SOLD_FILE}.lock`;

function acquireStoreLock() {
  fs.mkdirSync(path.dirname(SOLD_LOCK_FILE), { recursive: true, mode: 0o700 });
  try {
    const descriptor = fs.openSync(SOLD_LOCK_FILE, "wx", 0o600);
    const ownerToken = crypto.randomBytes(16).toString("hex");
    fs.writeFileSync(descriptor, `${process.pid}:${ownerToken}\n`, "utf8");
    return { descriptor, ownerToken };
  } catch (error) {
    // Never reclaim automatically. Recovery is allowed only with every writer stopped.
    if (error.code === "EEXIST") throw new Error("sold_code_store_busy");
    throw error;
  }
}

function assertStoreLockOwned(lock) {
  try {
    const descriptorStat = fs.fstatSync(lock.descriptor);
    const pathStat = fs.statSync(SOLD_LOCK_FILE);
    const owner = fs.readFileSync(SOLD_LOCK_FILE, "utf8").trim();
    if (
      descriptorStat.dev === pathStat.dev
      && descriptorStat.ino === pathStat.ino
      && owner.endsWith(`:${lock.ownerToken}`)
    ) return;
  } catch {
    // Missing, replaced or unreadable ownership state is a lost lock.
  }
  throw new Error("sold_code_store_lock_lost");
}

function releaseStoreLock(lock) {
  try {
    fs.closeSync(lock.descriptor);
  } catch (error) {
    console.error("[SOLD-CODES] Failed to close store lock:", error.message);
  }
  try {
    const owner = fs.readFileSync(SOLD_LOCK_FILE, "utf8").trim();
    if (owner.endsWith(`:${lock.ownerToken}`)) fs.unlinkSync(SOLD_LOCK_FILE);
  } catch (error) {
    if (error.code !== "ENOENT") console.error("[SOLD-CODES] Failed to release store lock:", error.message);
  }
}

function withStoreLock(operation) {
  const lock = acquireStoreLock();
  try {
    return operation(lock);
  } finally {
    releaseStoreLock(lock);
  }
}

function readStore() {
  if (!fs.existsSync(SOLD_FILE)) return { codes: [], reversals: [] };
  const data = JSON.parse(fs.readFileSync(SOLD_FILE, "utf8"));
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new Error("Invalid sold-code store");
  }
  return {
    codes: Array.isArray(data.codes) ? data.codes : [],
    reversals: Array.isArray(data.reversals) ? data.reversals : [],
  };
}

function sanitizeCodes(codes) {
  return codes.map(entry => {
    if (!entry || typeof entry !== "object") return entry;
    const { email: _legacyEmail, ...piiFreeEntry } = entry;
    return piiFreeEntry;
  });
}

function writeStoreLocked(lock, codes, reversals) {
  assertStoreLockOwned(lock);
  writeJsonAtomic(SOLD_FILE, { codes, reversals });
}

function hashReference(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function loadReversals() {
  return readStore().reversals;
}

function isReversed(stripeSessionId) {
  if (!stripeSessionId) return false;
  return loadReversals().some(entry => entry.stripeSessionId === stripeSessionId);
}

function maskCode(value) {
  return typeof value === "string" && value.length >= 4 ? `${value.slice(0, 4)}****` : "****";
}

function maskEmail(value) {
  if (typeof value !== "string" || !value.includes("@")) return "***";
  const [local, domain] = value.split("@");
  return `${local.slice(0, 2)}***@${domain}`;
}

function maskStripeId(value) {
  return typeof value === "string" && value.length >= 8 ? `${value.slice(0, 8)}...` : "***";
}

function load() {
  try {
    if (fs.existsSync(SOLD_FILE)) {
      const store = readStore();
      const hadLegacyEmail = store.codes.some(entry => entry && Object.prototype.hasOwnProperty.call(entry, "email"));
      const codes = sanitizeCodes(store.codes);
      if (hadLegacyEmail) {
        try {
          withStoreLock(lock => {
            const current = readStore();
            writeStoreLocked(lock, sanitizeCodes(current.codes), current.reversals);
          });
        } catch (migrationError) {
          console.error("[SOLD-CODES] Legacy email migration write failed:", migrationError.message);
        }
      }
      console.log(`[SOLD-CODES] Loaded ${codes.length} sold codes from ${SOLD_FILE}`);
      return codes;
    }
  } catch (e) {
    console.error("[SOLD-CODES] Load failed:", e.message);
  }
  return [];
}

function save(codes, reversals) {
  try {
    withStoreLock(lock => {
      const currentReversals = reversals === undefined ? readStore().reversals : reversals;
      writeStoreLocked(lock, sanitizeCodes(codes), currentReversals);
    });
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
    usedBy: entry.usedBy,
    productKey: entry.productKey || null,
    stripeSessionId: entry.stripeSessionId || null
  });
}

/**
 * Record a new sold code.
 * @param {Object} params
 * @param {string} params.code - Activation code (e.g. "PREM-XXXX-XXXX-XXXX")
 * @param {string} params.tier - "pro" or "premium"
 * @param {string} params.stripeSessionId - Stripe checkout session ID
 * @param {string} [params.productKey] - pro_monthly / premium_monthly / premium_lifetime
 * @param {Array}  [params.activationCodesRef] - Live reference to server.js activationCodes array
 * @returns {Object} The stored entry (also in activationCodes format)
 */
function recordSale({ code, tier, stripeSessionId, productKey, activationCodesRef }) {
  let entry;
  let reused = false;
  withStoreLock(lock => {
    const store = readStore();
    const existing = sanitizeCodes(store.codes);
    if (stripeSessionId && store.reversals.some(item => item.stripeSessionId === stripeSessionId)) {
      throw new Error("payment_reversed");
    }
    if (stripeSessionId) {
      entry = existing.find(c => c.stripeSessionId === stripeSessionId);
      if (entry) {
        if (entry.revoked) throw new Error("payment_reversed");
        if (entry.tier !== tier || (entry.productKey || null) !== (productKey || null)) {
          throw new Error("sale_binding_mismatch");
        }
        reused = true;
        return;
      }
    }

    entry = {
      code,
      tier,
      maxUses: 2,
      currentUses: 0,
      usedBy: [],
      // Technical payment metadata only. Customer email remains transient in the delivery call.
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
    writeStoreLocked(lock, existing, store.reversals);
  });

  // Live-merge into the server's activationCodes array so the new code
  // is immediately usable by the ACTIVATE_CODE handler without restart.
  mergeIntoActivationCodes(entry, activationCodesRef);

  if (reused) {
    console.log(`[SOLD-CODES] Existing sale reused for Stripe session: ${maskStripeId(stripeSessionId)}`);
    return entry;
  }

  console.log(`[SOLD-CODES] Recorded: ${maskCode(code)} (${tier})`);
  return entry;
}

function updateEmailDelivery(stripeSessionId, delivery) {
  if (!stripeSessionId) return null;
  return withStoreLock(lock => {
    const store = readStore();
    const existing = sanitizeCodes(store.codes);
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
    writeStoreLocked(lock, existing, store.reversals);
    return existing[index];
  });
}

/**
 * Returns all sold codes in the activationCodes-compatible shape
 * (code, tier, maxUses, currentUses, usedBy) for initial merge at startup.
 */
function loadAsActivationCodes() {
  return sanitizeCodes(readStore().codes).filter(c => !c.revoked).map(c => ({
    code: c.code,
    tier: c.tier,
    maxUses: c.maxUses || 2,
    currentUses: c.currentUses || 0,
    usedBy: Array.isArray(c.usedBy) ? c.usedBy : [],
    productKey: c.productKey || null,
    stripeSessionId: c.stripeSessionId || null
  }));
}

function findByStripeSession(stripeSessionId) {
  if (!stripeSessionId) return null;
  return load().find(entry => entry && entry.stripeSessionId === stripeSessionId) || null;
}

function revokeByStripeSession(stripeSessionId, activationCodesRef, reversalData) {
  let entry;
  const result = withStoreLock(lock => {
    const store = readStore();
    const existing = sanitizeCodes(store.codes);
    const reversals = [...store.reversals];
    entry = existing.find(item => item.stripeSessionId === stripeSessionId);
    let reversalDuplicate = false;
    if (reversalData) {
      const prior = reversals.find(item => item.stripeSessionId === stripeSessionId);
      reversalDuplicate = Boolean(prior);
      if (!prior) {
        reversals.push({
          stripeSessionId,
          paymentIntentHash: hashReference(reversalData.paymentIntent),
          productKey: reversalData.productKey || null,
          eventHash: hashReference(reversalData.eventId),
          reason: reversalData.reason,
          reversedAt: new Date().toISOString(),
        });
      }
    }
    if (!entry) {
      if (reversalData && !reversalDuplicate) writeStoreLocked(lock, existing, reversals);
      return { found: false, duplicate: reversalDuplicate, tombstoned: Boolean(reversalData) };
    }
    if (entry.revoked) {
      if (reversalData && !reversalDuplicate) writeStoreLocked(lock, existing, reversals);
      return { found: true, duplicate: true };
    }

    entry.revoked = true;
    entry.revokedAt = new Date().toISOString();
    writeStoreLocked(lock, existing, reversals);
    return { found: true, duplicate: false };
  });
  if (!result.found || result.duplicate) return result;
  if (Array.isArray(activationCodesRef)) {
    const index = activationCodesRef.findIndex(item => item.code === entry.code);
    if (index >= 0) activationCodesRef.splice(index, 1);
  }
  return result;
}

module.exports = {
  load, save, recordSale, updateEmailDelivery, revokeByStripeSession, loadAsActivationCodes,
  findByStripeSession, isReversed,
  maskCode, maskEmail, maskStripeId,
};
