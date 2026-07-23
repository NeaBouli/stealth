"use strict";

const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-activation-restart-"));
process.env.CODES_FILE = path.join(directory, "activation_codes.json");
process.env.SOLD_CODES_FILE = path.join(directory, "sold_codes.json");
delete process.env.SEED_ACTIVATION_CODES;

fs.writeFileSync(process.env.CODES_FILE, JSON.stringify({
  codes: [
    { code: "MANUAL-ACTIVE", tier: "pro", maxUses: 1, currentUses: 0, usedBy: [] },
    {
      code: "PAID-ACTIVE",
      tier: "pro",
      maxUses: 2,
      currentUses: 1,
      usedBy: ["device-a"],
      productKey: "vlabs_securecall_pro_lifetime",
      stripeSessionId: "cs_paid_active",
    },
    {
      code: "PAID-REVOKED",
      tier: "premium",
      maxUses: 2,
      currentUses: 1,
      usedBy: ["device-b"],
      productKey: "vlabs_securecall_premium_lifetime",
      stripeSessionId: "cs_paid_revoked",
    },
    {
      code: "PAID-ORPHAN",
      tier: "pro",
      maxUses: 2,
      currentUses: 0,
      usedBy: [],
      productKey: "vlabs_securecall_pro_lifetime",
      stripeSessionId: "cs_paid_orphan",
    },
  ],
}));
fs.writeFileSync(process.env.SOLD_CODES_FILE, JSON.stringify({
  codes: [
    {
      code: "PAID-ACTIVE",
      tier: "pro",
      maxUses: 2,
      currentUses: 0,
      usedBy: [],
      productKey: "vlabs_securecall_pro_lifetime",
      stripeSessionId: "cs_paid_active",
      revoked: false,
    },
    {
      code: "PAID-REVOKED",
      tier: "premium",
      maxUses: 2,
      currentUses: 0,
      usedBy: [],
      productKey: "vlabs_securecall_premium_lifetime",
      stripeSessionId: "cs_paid_revoked",
      revoked: true,
    },
  ],
  reversals: [
    { stripeSessionId: "cs_paid_revoked", reason: "stripe_full_refund" },
  ],
}));

const { activationCodes, loadActivationCodes } = require("../services/activation_store");

loadActivationCodes();
assert.deepStrictEqual(
  activationCodes.map(entry => entry.code).sort(),
  ["MANUAL-ACTIVE", "PAID-ACTIVE"],
  "restart keeps only manual codes and payment codes still active in the authoritative sold store",
);
assert.deepStrictEqual(
  activationCodes.find(entry => entry.code === "PAID-ACTIVE").usedBy,
  ["device-a"],
  "restart preserves newer device usage from the primary activation store",
);

fs.writeFileSync(process.env.SOLD_CODES_FILE, "{invalid-json");
loadActivationCodes();
assert.deepStrictEqual(
  activationCodes.map(entry => entry.code),
  ["MANUAL-ACTIVE"],
  "corrupt payment state fails closed without restoring payment-backed activations",
);

fs.rmSync(directory, { recursive: true, force: true });
console.log("activation_store_revocation.test.js ok");
