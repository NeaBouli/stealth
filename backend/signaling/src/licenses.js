/**
 * Dynamic License Pricing — Pro Lifetime (100 × €15→€50) + Premium Lifetime (100 × €25→€100)
 * Price increases linearly as licenses are sold.
 */

const fs = require("fs");
const path = require("path");

const LICENSES_FILE = path.join(__dirname, "..", "data", "licenses.json");

const LICENSES = {
  pro_lifetime: {
    sold: 0,
    max: 100,
    startPrice: 1500,  // €15.00 in cents
    endPrice: 5000,    // €50.00 in cents
    stripeProductId: "prod_UHMPlLJaBG5v8u"
  },
  premium_lifetime: {
    sold: 0,
    max: 100,
    startPrice: 2500,  // €25.00 in cents
    endPrice: 10000,   // €100.00 in cents
    stripeProductId: "prod_UHMc9gmBYfGQTT"
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
  return {
    pro_lifetime: {
      remaining: getRemainingLicenses("pro_lifetime"),
      currentPrice: getCurrentPrice("pro_lifetime"),
      nextPrice: getNextPrice("pro_lifetime"),
      sold: LICENSES.pro_lifetime.sold,
      soldOut: LICENSES.pro_lifetime.sold >= LICENSES.pro_lifetime.max
    },
    premium_lifetime: {
      remaining: getRemainingLicenses("premium_lifetime"),
      currentPrice: getCurrentPrice("premium_lifetime"),
      nextPrice: getNextPrice("premium_lifetime"),
      sold: LICENSES.premium_lifetime.sold,
      soldOut: LICENSES.premium_lifetime.sold >= LICENSES.premium_lifetime.max
    }
  };
}

function loadLicenses() {
  try {
    const data = JSON.parse(fs.readFileSync(LICENSES_FILE, "utf8"));
    if (data.pro_lifetime) LICENSES.pro_lifetime.sold = data.pro_lifetime.sold || 0;
    if (data.premium_lifetime) LICENSES.premium_lifetime.sold = data.premium_lifetime.sold || 0;
    console.log(`[LICENSES] Loaded: Pro ${LICENSES.pro_lifetime.sold}/100, Premium ${LICENSES.premium_lifetime.sold}/100`);
  } catch (e) {
    console.log("[LICENSES] No saved data — starting at 0 sales");
  }
}

function saveLicenses() {
  try {
    fs.mkdirSync(path.dirname(LICENSES_FILE), { recursive: true });
    fs.writeFileSync(LICENSES_FILE, JSON.stringify({
      pro_lifetime: { sold: LICENSES.pro_lifetime.sold },
      premium_lifetime: { sold: LICENSES.premium_lifetime.sold },
      lastUpdated: new Date().toISOString()
    }, null, 2));
  } catch (e) {
    console.error("[LICENSES] Failed to save:", e.message);
  }
}

loadLicenses();

module.exports = { getCurrentPrice, getNextPrice, getRemainingLicenses, recordSale, getStatus, LICENSES };
