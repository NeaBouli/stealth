"use strict";

// Test-only preload: synthetic localhost/IPC tests may run, provider sockets may not.
const net = require("node:net");
const childProcess = require("node:child_process");
const connect = net.Socket.prototype.connect;
net.Socket.prototype.connect = function (...args) {
  const options = Array.isArray(args[0]) ? args[0][0] : net._normalizeArgs(args)[0];
  const host = options.host || "localhost";
  if (!options.path && !["localhost", "127.0.0.1", "::1", "::ffff:127.0.0.1"].includes(host)) {
    throw new Error("external_network_blocked_in_synthetic_tests");
  }
  return connect.apply(this, args);
};

// Some startup regressions deliberately replace the child's environment. Preserve only
// this guard in those child environments, not the caller's credentials or config.
const preload = `--require=${__filename}`;
for (const name of ["spawn", "spawnSync"]) {
  const original = childProcess[name];
  childProcess[name] = function (command, args, options) {
    if (!Array.isArray(args)) { options = args; args = []; }
    const env = { ...(options?.env || process.env) };
    if (!(env.NODE_OPTIONS || "").includes(preload)) {
      env.NODE_OPTIONS = `${env.NODE_OPTIONS || ""} ${preload}`.trim();
    }
    return original.call(this, command, args, { ...options, env });
  };
}
