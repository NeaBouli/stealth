"use strict";

const assert = require("assert");
const { classifyHolderEligibility, maskWalletAddress } = require("../services/ifr");

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

assert.strictEqual(
  maskWalletAddress("0x17e99917Eca8539c62F509ED1193ac36580A6e7B"),
  "0x17e9...6e7B",
  "balance logs keep only edge characters of a wallet address"
);
assert.strictEqual(maskWalletAddress(""), "***", "empty input is fully masked");
assert.strictEqual(maskWalletAddress("0x123"), "***", "short input is fully masked");

console.log("ifr_holder.test PASSED - every positive IFR balance is eligible");
