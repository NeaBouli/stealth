"use strict";

const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");

const CODES_FILE = process.env.CODES_FILE
  || path.join(__dirname, "../../data/activation_codes.json");

// Exported as const — callers hold a stable reference; use .splice() to mutate
const activationCodes = [];

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
        const existing = new Set(loaded.map(c => c.code));
        const toAdd = seedCodes
          .filter(c => c.code && c.tier && !existing.has(c.code))
          .map(c => ({ code: c.code, tier: c.tier, maxUses: c.maxUses || 5, currentUses: 0, usedBy: [] }));
        loaded.push(...toAdd);
        console.log(`[ACTIVATION] Seeded ${toAdd.length} codes from SEED_ACTIVATION_CODES env var`);
      }
    } catch (e) {
      console.error("[ACTIVATION] Failed to parse SEED_ACTIVATION_CODES:", e.message);
    }
  }

  // sold_codes.json is authoritative for payment-backed activation codes.
  try {
    const sold = require("../payments/sold_codes").loadAsActivationCodes();
    const activeSoldBySession = new Map(
      sold
        .filter(code => code.stripeSessionId)
        .map(code => [code.stripeSessionId, code]),
    );
    loaded = loaded.filter(code => {
      if (!code?.stripeSessionId && !code?.productKey) return true;
      const activeSale = activeSoldBySession.get(code.stripeSessionId);
      return Boolean(activeSale && activeSale.code === code.code);
    });
    if (sold.length > 0) {
      const existing = new Set(loaded.map(c => c.code));
      const toAdd = sold.filter(c => !existing.has(c.code));
      loaded.push(...toAdd);
      console.log(`[ACTIVATION] Merged ${toAdd.length} sold codes from Stripe purchases`);
    }
  } catch (e) {
    console.warn("[ACTIVATION] Could not load sold_codes.json:", e.message);
    loaded = loaded.filter(code => !code?.stripeSessionId && !code?.productKey);
  }

  // Use .splice() to mutate in-place — preserves all stale references to this array
  activationCodes.splice(0, activationCodes.length, ...loaded);
}

function saveActivationCodes(options = {}) {
  try {
    writeJsonAtomic(CODES_FILE, { codes: activationCodes });
  } catch (e) {
    console.error("[ACTIVATION] Failed to save activation_codes.json:", e.message);
    if (options.throwOnError) throw e;
  }
}

module.exports = { activationCodes, loadActivationCodes, saveActivationCodes };
