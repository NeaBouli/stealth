"use strict";

// Regression control only: execute the current synthetic tests against four immutable
// pre-completion source modules, without rewriting the worktree or accessing any provider.
const { execFileSync } = require("node:child_process");
const Module = require("node:module");
const path = require("node:path");
const root = path.resolve(__dirname, "../..");
const base = "a7d70ddd169ddd789be01212ba4c501f3dd155be";
const sources = new Map([
  "backend/signaling/src/subscriptions.js",
  "backend/signaling/src/payments/google_play_billing.js",
  "backend/signaling/src/payments/google_play_rtdn.js",
  "backend/signaling/src/ws/handlers/subscription.js",
].map(file => [path.join(root, file), execFileSync("git", ["show", `${base}:${file}`], {
  cwd: root, encoding: "utf8",
})]));
const original = Module._extensions[".js"];
Module._extensions[".js"] = (module, file) => sources.has(file)
  ? module._compile(sources.get(file), file) : original(module, file);
