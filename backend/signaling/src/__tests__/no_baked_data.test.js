"use strict";

// STX-09: runtime stores (activation codes, wallets, sales, identities) must not be tracked
// in Git or copied into the Docker image.
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const root = path.join(__dirname, "..", "..");
const dockerfile = fs.readFileSync(path.join(root, "Dockerfile"), "utf8");
assert.ok(!/^\s*COPY\s+data\b/m.test(dockerfile), "Dockerfile must not COPY data/");
assert.ok(/^data\/\s*$/m.test(fs.readFileSync(path.join(root, ".dockerignore"), "utf8")), ".dockerignore excludes data/");

let tracked = [];
try {
  tracked = execFileSync("git", ["ls-files", "data"], { cwd: root, encoding: "utf8" })
    .split("\n").filter(Boolean).filter(file => file !== "data/.gitkeep");
} catch {
  tracked = null; // not a git checkout (e.g. inside an image); the Dockerfile checks still apply
}
if (tracked) assert.deepStrictEqual(tracked, [], "no runtime store files tracked under data/");

console.log("no_baked_data.test.js: PASS");
