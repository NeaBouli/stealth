"use strict";

const fs = require("fs");
const path = require("path");

const EPHEMERAL_FALLBACK = "/tmp/stealthx-data";

function isWritableDirectory(directory) {
  try {
    fs.mkdirSync(directory, { recursive: true });
    const probe = path.join(directory, ".write_test");
    fs.writeFileSync(probe, "1");
    fs.unlinkSync(probe);
    return true;
  } catch {
    return false;
  }
}

// Persistent state (licenses, activation codes, subscriptions) lives here.
// Outside production an unwritable directory falls back to an ephemeral path.
// In production that fallback would silently drop sold licenses on restart,
// so it requires an explicit DATA_DIR_EPHEMERAL_OK=1 opt-in.
function resolveDataDir({ preferred, env = process.env, fallback = EPHEMERAL_FALLBACK, warn = console.warn } = {}) {
  if (isWritableDirectory(preferred)) return preferred;
  const production = env.NODE_ENV === "production";
  if (production && env.DATA_DIR_EPHEMERAL_OK !== "1") {
    const error = new Error(`[DATA] ${preferred} is not writable; refusing ephemeral fallback in production`);
    error.code = "data_dir_not_writable";
    throw error;
  }
  warn(`[DATA] ${preferred} not writable — using ephemeral ${fallback}`);
  fs.mkdirSync(fallback, { recursive: true });
  return fallback;
}

// Every file-backed store. All of them are pinned into the validated data
// directory so no store can silently fall back to a module-relative default
// (e.g. ../../data) outside DATA_DIR. data_dir.test.js fails when a module
// reads a *_FILE variable that is missing here.
const STORE_FILES = Object.freeze({
  FCM_TOKENS_FILE: "fcm_tokens.json",
  CODES_FILE: "activation_codes.json",
  WALLETS_FILE: "wallets.json",
  SUBS_FILE: "subscriptions.json",
  LICENSES_FILE: "licenses.json",
  IDS_FILE: "custom_ids.json",
  PENDING_FILE: "pending_activations.json",
  GIFT_CODES_FILE: "gift_codes.json",
  STRIPE_PROCESSED_FILE: "stripe_processed_events.json",
  SOLD_CODES_FILE: "sold_codes.json",
  GOOGLE_PLAY_RTDN_FILE: "google_play_rtdn.json",
  VLABS_FULFILLMENT_ORDERS_FILE: "vlabs_fulfillment_orders.json",
  IDENTITY_REGISTRY_FILE: "identity_registry.json",
});

// Must run before any store module is required (several read their path at
// module load time).
function alignStoreFiles(dataDir, env = process.env) {
  for (const [key, file] of Object.entries(STORE_FILES)) {
    env[key] = path.join(dataDir, file);
  }
}

module.exports = { resolveDataDir, alignStoreFiles, STORE_FILES, EPHEMERAL_FALLBACK };
