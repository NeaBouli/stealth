"use strict";

const assert = require("assert");
const { classifyHolderEligibility } = require("../services/ifr");

assert.deepStrictEqual(
  classifyHolderEligibility(0n),
  { success: false, holder: false, error: "insufficient", balanceAmount: "0" },
  "zero IFR is not holder-eligible"
);
assert.deepStrictEqual(
  classifyHolderEligibility(1n),
  { success: true, holder: true, balanceAmount: "0" },
  "the smallest positive on-chain balance is holder-eligible"
);
assert.deepStrictEqual(
  classifyHolderEligibility(1_500n * 1_000_000_000n),
  { success: true, holder: true, balanceAmount: "1500" },
  "eligibility has no legacy 2,000 IFR threshold"
);

console.log("ifr_holder.test PASSED - every positive IFR balance is eligible");
