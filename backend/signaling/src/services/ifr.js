"use strict";

const { ethers } = require("ethers");

// IFR Lock contract — users must LOCK tokens here to unlock tiers.
// Matches IFRConstants.kt: IFR_LOCK_ADDRESS, decimals 9, PRO=2000, ELITE=6000.
const IFR_LOCK_ADDRESS = "0x769928aBDfc949D0718d8766a1C2d7dBb63954Eb";
const IFR_DECIMALS = 9;
const IFR_PRO_THRESHOLD   = BigInt(2000) * BigInt(10 ** IFR_DECIMALS);
const IFR_ELITE_THRESHOLD = BigInt(6000) * BigInt(10 ** IFR_DECIMALS);
const IFR_LOCK_ABI = ["function lockedBalance(address) view returns (uint256)"];

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

console.log(`[IFR] Configured ${ETH_RPC_URLS.length} Ethereum RPC endpoints for IFR lock ${IFR_LOCK_ADDRESS}`);

async function verifyIfrLock(walletAddress) {
  if (ETH_RPC_URLS.length === 0) return { success: false, error: "eth_unavailable" };

  for (const url of ETH_RPC_URLS) {
    const provider = new ethers.JsonRpcProvider(url);
    const contract = new ethers.Contract(IFR_LOCK_ADDRESS, IFR_LOCK_ABI, provider);
    try {
      const withTimeout = (p) => Promise.race([
        p,
        new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), 10000))
      ]);
      const locked = await withTimeout(contract.lockedBalance(walletAddress));
      const humanAmount = (locked / BigInt(10 ** IFR_DECIMALS)).toString();
      console.log("[IFR] lockedBalance(" + walletAddress + ") = " + humanAmount + " IFR (via " + url + ")");
      if (locked >= IFR_ELITE_THRESHOLD) return { success: true, tier: "elite", lockedAmount: humanAmount };
      if (locked >= IFR_PRO_THRESHOLD)   return { success: true, tier: "pro",   lockedAmount: humanAmount };
      return { success: false, error: "insufficient", lockedAmount: humanAmount };
    } catch (e) {
      console.warn("[IFR] RPC failed (" + url + "):", e.message, "— trying next");
    } finally {
      provider.destroy();
    }
  }
  return { success: false, error: "all_rpc_failed" };
}

module.exports = { verifyIfrLock };
