"use strict";

const fs = require("fs");
const path = require("path");

function writeJsonAtomic(targetFile, data) {
  ensureDir(targetFile);
  const tmp = `${targetFile}.${process.pid}.tmp`;
  let created = false;
  try {
    fs.writeFileSync(tmp, JSON.stringify(data, null, 2), { encoding: "utf8", mode: 0o600, flag: "wx" });
    created = true;
    fs.renameSync(tmp, targetFile);
  } catch (error) {
    if (created) fs.rmSync(tmp, { force: true });
    throw error;
  }
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
