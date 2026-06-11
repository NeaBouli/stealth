/**
 * Dynamic License Pricing — Pro Lifetime (100 × €15→€50) + Premium Lifetime (100 × €25→€100)
 * Price increases linearly as licenses are sold.
 */

const fs = require("fs");
const path = require("path");
const { writeJsonAtomic } = require("./utils/json_store");

const LICENSES_FILE = process.env.LICENSES_FILE || path.join(__dirname, "..", "data", "licenses.json");

const LICENSES = {
  pro_lifetime: {
    name: "SecureCall Pro Lifetime",
    activationTier: "pro",
    sold: 0,
    max: 100,
    startPrice: 1500,  // €15.00 in cents
    endPrice: 5000,    // €50.00 in cents
    stripeProductId: "prod_UHMPlLJaBG5v8u"
  },
  premium_lifetime: {
    name: "SecureCall Premium Lifetime",
    activationTier: "premium",
    sold: 0,
    max: 100,
    startPrice: 2500,  // €25.00 in cents
    endPrice: 10000,   // €100.00 in cents
    stripeProductId: "prod_UHMc9gmBYfGQTT"
  },
  securechat_pro_lifetime: {
    name: "SecureChat Pro Lifetime",
    activationTier: "pro",
    sold: 0,
    max: 100,
    startPrice: 900,
    endPrice: 1499
  },
  securechat_elite_lifetime: {
    name: "SecureChat Elite Lifetime",
    activationTier: "elite",
    sold: 0,
    max: 100,
    startPrice: 1900,
    endPrice: 2399
  },
  chameleon_pro_lifetime: {
    name: "Chameleon Pro Lifetime",
    activationTier: "pro",
    sold: 0,
    max: 100,
    startPrice: 900,
    endPrice: 1499
  },
  chameleon_elite_lifetime: {
    name: "Chameleon Elite Lifetime",
    activationTier: "elite",
    sold: 0,
    max: 100,
    startPrice: 1900,
    endPrice: 2399
  },
  stealthx_suite_lifetime: {
    name: "StealthX Suite Lifetime",
    activationTier: "elite",
    sold: 0,
    max: 100,
    startPrice: 5400,
    endPrice: 9900
  }
};

function getCurrentPrice(type) {
  const lic = LICENSES[type];
  if (!lic || lic.sold >= lic.max) return null;
  const step = (lic.endPrice - lic.startPrice) / lic.max;
  return Math.round(lic.startPrice + lic.sold * step);
}

function getNextPrice(type) {
  const lic = LICENSES[type];
  if (!lic) return null;
  const next = Math.min(lic.sold + 1, lic.max);
  const step = (lic.endPrice - lic.startPrice) / lic.max;
  return Math.round(lic.startPrice + next * step);
}

function getRemainingLicenses(type) {
  const lic = LICENSES[type];
  return lic ? lic.max - lic.sold : 0;
}

function recordSale(type) {
  const lic = LICENSES[type];
  if (!lic || lic.sold >= lic.max) return false;
  lic.sold++;
  console.log(`[LICENSES] Sale recorded: ${type} — sold: ${lic.sold}/${lic.max}, next price: €${(getCurrentPrice(type) / 100).toFixed(2)}`);
  saveLicenses();
  return true;
}

function getStatus() {
  return Object.fromEntries(Object.entries(LICENSES).map(([key, lic]) => [key, {
    remaining: getRemainingLicenses(key),
    currentPrice: getCurrentPrice(key),
    nextPrice: getNextPrice(key),
    sold: lic.sold,
    soldOut: lic.sold >= lic.max
  }]));
}

function loadLicenses() {
  try {
    const data = JSON.parse(fs.readFileSync(LICENSES_FILE, "utf8"));
    for (const [key, value] of Object.entries(data || {})) {
      if (LICENSES[key] && typeof value?.sold === "number") {
        LICENSES[key].sold = Math.max(0, Math.min(value.sold, LICENSES[key].max));
      }
    }
    console.log(`[LICENSES] Loaded: ${Object.entries(LICENSES).map(([key, lic]) => `${key} ${lic.sold}/${lic.max}`).join(", ")}`);
  } catch (e) {
    console.log("[LICENSES] No saved data — starting at 0 sales");
  }
}

function saveLicenses() {
  try {
    writeJsonAtomic(LICENSES_FILE, {
      ...Object.fromEntries(Object.entries(LICENSES).map(([key, lic]) => [key, { sold: lic.sold }])),
      lastUpdated: new Date().toISOString()
    });
  } catch (e) {
    console.error("[LICENSES] Failed to save:", e.message);
  }
}

loadLicenses();

module.exports = { getCurrentPrice, getNextPrice, getRemainingLicenses, recordSale, getStatus, saveLicenses, LICENSES };
