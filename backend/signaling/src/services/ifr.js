"use strict";

const { ethers } = require("ethers");

// IFR token hold model — users only need to hold IFR in their wallet to unlock tiers.
const IFR_TOKEN_ADDRESS = "0x77e99917Eca8539c62F509ED1193ac36580A6e7B";
const IFR_DECIMALS = 9;
const IFR_PRO_THRESHOLD   = BigInt(2000) * BigInt(10 ** IFR_DECIMALS);
const IFR_ELITE_THRESHOLD = BigInt(6000) * BigInt(10 ** IFR_DECIMALS);
const IFR_TOKEN_ABI = ["function balanceOf(address) view returns (uint256)"];

const DEFAULT_ETH_RPC_URLS = [
  "https://ethereum.publicnode.com",
  "https://cloudflare-eth.com",
];
const ETH_RPC_URLS = Array.from(new Set(
  (process.env.ETH_RPC_URL || DEFAULT_ETH_RPC_URLS.join(","))
    .split(",")
    .map(url => url.trim())
    .filter(Boolean)
));

console.log(`[IFR] Configured ${ETH_RPC_URLS.length} Ethereum RPC endpoints for IFR token ${IFR_TOKEN_ADDRESS}`);

function classifyLegacyTier(balance) {
  const humanAmount = (balance / BigInt(10 ** IFR_DECIMALS)).toString();
  if (balance >= IFR_ELITE_THRESHOLD) return { success: true, tier: "premium", lockedAmount: humanAmount, balanceAmount: humanAmount };
  if (balance >= IFR_PRO_THRESHOLD) return { success: true, tier: "pro", lockedAmount: humanAmount, balanceAmount: humanAmount };
  return { success: false, error: "insufficient", lockedAmount: humanAmount, balanceAmount: humanAmount };
}

function classifyHolderEligibility(balance) {
  const humanAmount = (balance / BigInt(10 ** IFR_DECIMALS)).toString();
  return balance > 0n
    ? { success: true, holder: true, balanceAmount: humanAmount }
    : { success: false, holder: false, error: "insufficient", balanceAmount: humanAmount };
}

async function readIfrBalance(walletAddress) {
  if (ETH_RPC_URLS.length === 0) return { success: false, error: "eth_unavailable" };

  for (const url of ETH_RPC_URLS) {
    const provider = new ethers.JsonRpcProvider(url);
    const contract = new ethers.Contract(IFR_TOKEN_ADDRESS, IFR_TOKEN_ABI, provider);
    try {
      const withTimeout = (p) => Promise.race([
        p,
        new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), 10000))
      ]);
      const balance = await withTimeout(contract.balanceOf(walletAddress));
      const humanAmount = (balance / BigInt(10 ** IFR_DECIMALS)).toString();
      console.log("[IFR] balanceOf(" + walletAddress + ") = " + humanAmount + " IFR (via " + url + ")");
      return { success: true, balance };
    } catch (e) {
      console.warn("[IFR] RPC failed (" + url + "):", e.message, "— trying next");
    } finally {
      provider.destroy();
    }
  }
  return { success: false, error: "all_rpc_failed" };
}

async function verifyIfrLock(walletAddress) {
  const result = await readIfrBalance(walletAddress);
  return result.success ? classifyLegacyTier(result.balance) : result;
}

async function verifyIfrHolding(walletAddress) {
  const result = await readIfrBalance(walletAddress);
  return result.success ? classifyHolderEligibility(result.balance) : result;
}

module.exports = {
  verifyIfrLock,
  verifyIfrHolding,
  classifyHolderEligibility,
};
