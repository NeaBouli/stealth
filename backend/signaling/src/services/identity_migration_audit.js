"use strict";

const fs = require("fs");
const path = require("path");

// Append-only audit trail for legacy alias migrations and their reversal (#144 review F2).
// One JSON object per line, mode 0600, fsync per record. Records hold identifiers and
// hashes only, never push tokens. Callers treat a failed append as fail-closed.
const EVENTS = new Set(["alias_bind_authorized", "alias_revoked"]);

class MigrationAuditError extends Error {
  constructor() { super("identity_migration_audit_unavailable"); this.code = "identity_migration_audit_unavailable"; }
}

function createMigrationAudit({ file, now = () => Math.floor(Date.now() / 1000) }) {
  if (!path.isAbsolute(file)) throw new MigrationAuditError();
  fs.mkdirSync(path.dirname(file), { recursive: true });

  function append(event, fields) {
    if (!EVENTS.has(event)) throw new MigrationAuditError();
    const record = JSON.stringify({ at: now(), event, ...fields }) + "\n";
    let fd;
    try {
      fd = fs.openSync(file, fs.constants.O_CREAT | fs.constants.O_APPEND | fs.constants.O_WRONLY
        | fs.constants.O_NOFOLLOW, 0o600);
      const stat = fs.fstatSync(fd);
      if (!stat.isFile() || (stat.mode & 0o077) !== 0) throw new MigrationAuditError();
      fs.writeSync(fd, record);
      fs.fsyncSync(fd);
    } catch {
      throw new MigrationAuditError();
    } finally {
      if (fd !== undefined) fs.closeSync(fd);
    }
  }

  function read() {
    if (!fs.existsSync(file)) return [];
    return fs.readFileSync(file, "utf8").split("\n").filter(Boolean).map(line => JSON.parse(line));
  }

  return { append, read };
}

module.exports = { createMigrationAudit, MigrationAuditError };
