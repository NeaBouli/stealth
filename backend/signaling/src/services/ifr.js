"use strict";

const { ethers } = require("ethers");

const IFR_TOKEN_ADDRESS = "0x77e99917Eca8539c62F509ED1193ac36580A6e7B";
const IFR_DECIMALS = 9;
const IFR_PRO_THRESHOLD = BigInt(1000) * BigInt(10 ** IFR_DECIMALS);
const IFR_PREMIUM_THRESHOLD = BigInt(5000) * BigInt(10 ** IFR_DECIMALS);
const IFR_TOKEN_ABI = ["function balanceOf(address) view returns (uint256)"];

const ETH_RPC_URLS = (process.env.ETH_RPC_URL || "https://eth.llamarpc.com")
  .split(",")
  .concat(["https://rpc.ankr.com/eth", "https://cloudflare-eth.com"]);

const ifrTokenContracts = [];

for (const url of ETH_RPC_URLS) {
  try {
    const provider = new ethers.JsonRpcProvider(url);
    const tokenContract = new ethers.Contract(IFR_TOKEN_ADDRESS, IFR_TOKEN_ABI, provider);
    ifrTokenContracts.push({ contract: tokenContract, url });
  } catch (e) {
    console.warn("[IFR] Failed to init provider:", url, e.message);
  }
}
console.log(`[IFR] Initialized ${ifrTokenContracts.length} Ethereum RPC endpoints for IFR token ${IFR_TOKEN_ADDRESS}`);

async function verifyIfrLock(walletAddress) {
  if (ifrTokenContracts.length === 0) return { success: false, error: "eth_unavailable" };

  for (const { contract, url } of ifrTokenContracts) {
    try {
      const withTimeout = (p) => Promise.race([
        p,
        new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), 10000))
      ]);
      const balance = await withTimeout(contract.balanceOf(walletAddress));
      const humanAmount = (balance / BigInt(10 ** IFR_DECIMALS)).toString();
      console.log("[IFR] balanceOf(" + walletAddress + ") = " + humanAmount + " IFR (via " + url + ")");
      if (balance >= IFR_PREMIUM_THRESHOLD) return { success: true, tier: "premium", lockedAmount: humanAmount };
      if (balance >= IFR_PRO_THRESHOLD) return { success: true, tier: "pro", lockedAmount: humanAmount };
      return { success: false, error: "insufficient", lockedAmount: humanAmount };
    } catch (e) {
      console.warn("[IFR] RPC failed (" + url + "):", e.message, "— trying next");
    }
  }
  return { success: false, error: "all_rpc_failed" };
}

module.exports = { verifyIfrLock };
