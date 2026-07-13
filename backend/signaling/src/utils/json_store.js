"use strict";

const fs = require("fs");
const path = require("path");

function writeJsonAtomic(targetFile, data) {
  ensureDir(targetFile);
  const tmp = `${targetFile}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), { encoding: "utf8", mode: 0o600, flag: "w" });
  fs.renameSync(tmp, targetFile);
  fs.chmodSync(targetFile, 0o600);
}

function ensureDir(filePath) {
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
}

function readJsonFile(filePath, defaultValue) {
  try {
    if (fs.existsSync(filePath)) {
      return JSON.parse(fs.readFileSync(filePath, "utf8"));
    }
  } catch (e) {
    console.warn(`[json_store] Could not read ${filePath}:`, e.message);
  }
  return defaultValue;
}

module.exports = { writeJsonAtomic, ensureDir, readJsonFile };
