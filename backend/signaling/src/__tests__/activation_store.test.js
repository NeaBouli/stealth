"use strict";

const assert = require("assert");
const {
  activationCodes,
  normalizeSeedActivationCode,
  newSeedActivationCodes,
  revokeActivationCode,
} = require("../services/activation_store");

const reviewer = normalizeSeedActivationCode({
  code: "ELIT-REVIEW-TEST-0001",
  tier: "ELITE",
  productKey: "securechat_elite_lifetime",
  maxUses: 25,
});
assert.deepStrictEqual(reviewer, {
  code: "ELIT-REVIEW-TEST-0001",
  tier: "elite",
  productKey: "securechat_elite_lifetime",
  maxUses: 25,
  currentUses: 0,
  usedBy: [],
});

assert.strictEqual(normalizeSeedActivationCode({
  code: "lowercase-code",
  tier: "elite",
  productKey: "securechat_elite_lifetime",
}), null);

activationCodes.splice(0, activationCodes.length, {
  code: "ELIT-REVOKE-TEST-0001",
  tier: "elite",
  productKey: "securechat_elite_lifetime",
  maxUses: 25,
  currentUses: 1,
  usedBy: ["reviewer-device"],
});
assert.deepStrictEqual(revokeActivationCode("ELIT-REVOKE-TEST-0001", () => true), { success: true });
assert.deepStrictEqual(newSeedActivationCodes(activationCodes, [{
  code: "ELIT-REVOKE-TEST-0001",
  tier: "elite",
  productKey: "securechat_elite_lifetime",
}]), [], "revocation tombstone prevents an environment seed from restoring the code");
assert.strictEqual(activationCodes[0].revoked, true, "successful revoke leaves a durable tombstone");

activationCodes.splice(0, activationCodes.length, { code: "ELIT-REVOKE-TEST-0002", tier: "elite" });
assert.deepStrictEqual(revokeActivationCode("ELIT-REVOKE-TEST-0002", () => false), {
  success: false,
  error: "persistence_failed",
});
assert.strictEqual(activationCodes.length, 1, "failed persistence rolls back the revoke");
assert.strictEqual(activationCodes[0].revoked, undefined, "rollback restores the active entry");
assert.deepStrictEqual(revokeActivationCode("invalid", () => true), { success: false, error: "invalid_code" });
activationCodes.splice(0);
assert.strictEqual(normalizeSeedActivationCode({
  code: "ELIT-NO-PRODUCT-0001",
  tier: "elite",
  maxUses: 5,
}), null);

const legacy = normalizeSeedActivationCode({
  code: "LEGACY-PRO-TEST-01",
  tier: "pro",
  maxUses: 2,
});
assert.strictEqual(legacy.productKey, undefined);
assert.strictEqual(legacy.tier, "pro");

const persisted = [{
  code: "ELIT-REVIEW-TEST-0001",
  tier: "elite",
  productKey: "securechat_elite_lifetime",
  maxUses: 25,
  currentUses: 1,
  usedBy: ["existing-device"],
}];
assert.deepStrictEqual(newSeedActivationCodes(persisted, [{
  code: "ELIT-REVIEW-TEST-0001",
  tier: "elite",
  productKey: "securechat_elite_lifetime",
  maxUses: 25,
}]), []);
assert.deepStrictEqual(persisted[0].usedBy, ["existing-device"]);
assert.strictEqual(normalizeSeedActivationCode({
  code: "ELIT-REVIEW-TEST-0002",
  tier: "elite",
  productKey: "securechat/invalid",
}), null);
assert.strictEqual(normalizeSeedActivationCode({
  code: "ELIT-REVIEW-TEST-0003",
  tier: "elite",
  productKey: "securechat_elite_lifetime",
  maxUses: 51,
}), null);

console.log("activation_store.test.js ok");
