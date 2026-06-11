"use strict";

const fs = require("fs");
const path = require("path");

function writeJsonAtomic(targetFile, data) {
  ensureDir(targetFile);
  const tmp = targetFile + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), "utf8");
  fs.renameSync(tmp, targetFile);
}

function ensureDir(filePath) {
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
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
