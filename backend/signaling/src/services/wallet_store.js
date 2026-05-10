"use strict";

const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");

const WALLETS_FILE = process.env.WALLETS_FILE
  || path.join(__dirname, "../../data/wallets.json");

// Exported as const — callers hold a stable reference; use .splice() to mutate
const walletMappings = [];

function loadWalletMappings() {
  try {
    const fs = require("fs");
    const raw = fs.readFileSync(WALLETS_FILE, "utf8");
    const loaded = JSON.parse(raw).wallets || [];
    walletMappings.splice(0, walletMappings.length, ...loaded);
    console.log(`[IFR] Loaded ${walletMappings.length} wallet mappings`);
  } catch (e) {
    walletMappings.splice(0, walletMappings.length);
  }
}

function saveWalletMappings() {
  try {
    writeJsonAtomic(WALLETS_FILE, { wallets: walletMappings });
  } catch (_) {}
}

module.exports = { walletMappings, loadWalletMappings, saveWalletMappings };
