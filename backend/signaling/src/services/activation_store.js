"use strict";

const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");

const CODES_FILE = process.env.CODES_FILE
  || path.join(__dirname, "../../data/activation_codes.json");

// Exported as const — callers hold a stable reference; use .splice() to mutate
const activationCodes = [];

function normalizeSeedActivationCode(entry) {
  if (!entry || typeof entry !== "object") return null;
  const code = typeof entry.code === "string" ? entry.code.trim() : "";
  const tier = typeof entry.tier === "string" ? entry.tier.trim().toLowerCase() : "";
  const productKey = typeof entry.productKey === "string" ? entry.productKey.trim() : "";
  const maxUses = entry.maxUses == null ? 5 : Number(entry.maxUses);
  if (!/^[A-Z0-9][A-Z0-9-]{7,63}$/.test(code)) return null;
  if (!["pro", "premium", "elite"].includes(tier)) return null;
  if (tier === "elite" && !productKey) return null;
  if (productKey && !/^[a-zA-Z0-9_.:-]{1,120}$/.test(productKey)) return null;
  if (!Number.isSafeInteger(maxUses) || maxUses < 1 || maxUses > 50) return null;
  return {
    code,
    tier,
    ...(productKey ? { productKey } : {}),
    maxUses,
    currentUses: 0,
    usedBy: [],
  };
}

function newSeedActivationCodes(loaded, seedCodes) {
  if (!Array.isArray(loaded) || !Array.isArray(seedCodes)) return [];
  const existing = new Set(loaded.map(entry => entry.code));
  return seedCodes
    .map(normalizeSeedActivationCode)
    .filter(entry => entry && !existing.has(entry.code));
}

function loadActivationCodes() {
  let loaded = [];

  // Primary file
  try {
    const fs = require("fs");
    const raw = fs.readFileSync(CODES_FILE, "utf8");
    const data = JSON.parse(raw);
    loaded = data.codes || [];
    console.log(`[ACTIVATION] Loaded ${loaded.length} activation codes from file`);
  } catch (e) {
    console.warn("[ACTIVATION] Could not load activation_codes.json:", e.message,
      "— starting with zero codes (fail-closed)");
  }

  // Seed from env (survives Railway redeploys where filesystem is ephemeral)
  if (process.env.SEED_ACTIVATION_CODES) {
    try {
      const seedCodes = JSON.parse(process.env.SEED_ACTIVATION_CODES);
      if (Array.isArray(seedCodes) && seedCodes.length > 0) {
        const toAdd = newSeedActivationCodes(loaded, seedCodes);
        const validCount = seedCodes.map(normalizeSeedActivationCode).filter(Boolean).length;
        if (validCount !== seedCodes.length) {
          console.warn(`[ACTIVATION] Rejected ${seedCodes.length - validCount} invalid seeded activation code entries`);
        }
        loaded.push(...toAdd);
        console.log(`[ACTIVATION] Seeded ${toAdd.length} codes from SEED_ACTIVATION_CODES env var`);
      }
    } catch (e) {
      console.error("[ACTIVATION] Failed to parse SEED_ACTIVATION_CODES:", e.message);
    }
  }

  // Merge codes from Stripe purchases (sold_codes.json)
  try {
    const sold = require("../payments/sold_codes").loadAsActivationCodes();
    if (sold.length > 0) {
      const existing = new Set(loaded.map(c => c.code));
      const toAdd = sold.filter(c => !existing.has(c.code));
      loaded.push(...toAdd);
      console.log(`[ACTIVATION] Merged ${toAdd.length} sold codes from Stripe purchases`);
    }
  } catch (e) {
    console.warn("[ACTIVATION] Could not load sold_codes.json:", e.message);
  }

  // Use .splice() to mutate in-place — preserves all stale references to this array
  activationCodes.splice(0, activationCodes.length, ...loaded);
}

function saveActivationCodes() {
  try {
    writeJsonAtomic(CODES_FILE, { codes: activationCodes });
    return true;
  } catch (e) {
    console.error("[ACTIVATION] Failed to save activation_codes.json:", e.message);
    return false;
  }
}

function revokeActivationCode(code, persist = saveActivationCodes) {
  const normalizedCode = typeof code === "string" ? code.trim().toUpperCase() : "";
  if (!/^[A-Z0-9][A-Z0-9-]{7,63}$/.test(normalizedCode)) {
    return { success: false, error: "invalid_code" };
  }
  const index = activationCodes.findIndex(entry => String(entry.code || "").toUpperCase() === normalizedCode);
  if (index < 0) return { success: false, error: "not_found" };
  if (activationCodes[index].revoked === true) return { success: true };

  const tombstone = {
    code: normalizedCode,
    revoked: true,
    revokedAt: new Date().toISOString(),
  };
  const [removed] = activationCodes.splice(index, 1, tombstone);
  if (persist() === false) {
    activationCodes.splice(index, 1, removed);
    return { success: false, error: "persistence_failed" };
  }
  return { success: true };
}

module.exports = {
  activationCodes,
  loadActivationCodes,
  saveActivationCodes,
  normalizeSeedActivationCode,
  newSeedActivationCodes,
  revokeActivationCode,
};
