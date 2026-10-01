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

module.exports = { resolveDataDir, EPHEMERAL_FALLBACK };
