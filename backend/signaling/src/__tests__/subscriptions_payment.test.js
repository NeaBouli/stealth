const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const testDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-subscription-payment-"));
process.env.SUBS_FILE = path.join(testDir, "subscriptions.json");
const subscriptions = require("../subscriptions");

assert.throws(
  () => subscriptions.verifySubscription("device", "token", "securecall_pro_monthly"),
  /external_google_play_verification_required/,
  "unverified client claims cannot create subscriptions"
);
const expiresAt = Date.now() + 86400000;
const packageName = "com.securecall.app.free";
const catalogVersion = "securecall-play-v1";
assert.deepStrictEqual(
  subscriptions.recordVerifiedSubscription(
    "device", "token", "securecall_pro_monthly", "pro", expiresAt, packageName, catalogVersion
  ),
  { tier: "pro", expiresAt }
);
assert.strictEqual(subscriptions.getTier("device"), "pro");
assert.strictEqual(
  subscriptions.refreshByPurchaseToken(
    "token", "securecall_premium_monthly", "premium", expiresAt + 1000, packageName, catalogVersion
  ),
  1
);
assert.strictEqual(subscriptions.getTier("device"), "premium");
assert.strictEqual(subscriptions.expireByPurchaseToken("token"), 1);
assert.strictEqual(subscriptions.getTier("device"), "FREE");
assert.throws(
  () => subscriptions.recordVerifiedSubscription(
    "device", "token", "fake", "admin", expiresAt, packageName, catalogVersion
  ),
  /invalid_verified_subscription/
);

fs.rmSync(process.env.SUBS_FILE, { force: true });
fs.mkdirSync(process.env.SUBS_FILE);
assert.throws(
  () => subscriptions.recordVerifiedSubscription(
    "persistence-device", "persistence-token", "securecall_pro_monthly", "pro",
    expiresAt, packageName, catalogVersion
  ),
  /subscription_persistence_failed/,
  "paid access must not remain in memory when persistence fails"
);
assert.strictEqual(subscriptions.getTier("persistence-device"), "FREE");

fs.rmSync(testDir, { recursive: true, force: true });
console.log("subscriptions_payment.test.js ok");
