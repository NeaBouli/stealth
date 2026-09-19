"use strict";

const fs = require("fs");
const path = require("path");
const { createTesterLicenseRegistry } = require("./tester_license_registry");

/** Explicit provisioning only. Misconfiguration disables gifts, not calling. */
function loadTesterLicenseRuntime(env = process.env) {
  if (env.SECURECALL_TESTER_LICENSE_ENABLED !== "true") return null;
  try {
    const keyFile = env.SECURECALL_TESTER_SIGNER_FILE;
    if (typeof keyFile !== "string" || !path.isAbsolute(keyFile)) return null;
    for (let entry = keyFile; ; entry = path.dirname(entry)) {
      if (fs.lstatSync(entry).isSymbolicLink() || fs.existsSync(path.join(entry, ".git"))) return null;
      if (path.dirname(entry) === entry) break;
    }
    const directory = fs.statSync(path.dirname(keyFile));
    const stat = fs.lstatSync(keyFile);
    if (!directory.isDirectory() || directory.uid !== process.getuid() || (directory.mode & 0o777) !== 0o700 ||
        !stat.isFile() || stat.uid !== process.getuid() || stat.nlink !== 1 || (stat.mode & 0o777) !== 0o600 || stat.size > 4096) return null;
    return createTesterLicenseRegistry({file:env.SECURECALL_TESTER_REGISTRY_FILE, privateKey:fs.readFileSync(keyFile,"utf8")});
  } catch { return null; }
}

module.exports = { loadTesterLicenseRuntime };
