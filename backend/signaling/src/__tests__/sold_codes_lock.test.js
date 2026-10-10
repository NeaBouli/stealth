"use strict";

// STX-17: the sold-code store lock must recover from a stale owner (crashed
// pid or abandoned age) while a live, fresh lock still fails closed.
// Run: node src/__tests__/sold_codes_lock.test.js

const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-sold-lock-"));
process.env.SOLD_CODES_FILE = path.join(dir, "sold_codes.json");
const LOCK_FILE = `${process.env.SOLD_CODES_FILE}.lock`;

const soldCodes = require("../payments/sold_codes");

function writeLock(content, ageMs = 0) {
  fs.writeFileSync(LOCK_FILE, content, { mode: 0o600 });
  if (ageMs > 0) {
    const past = new Date(Date.now() - ageMs);
    fs.utimesSync(LOCK_FILE, past, past);
  }
}

(async () => {
  // 1. Dead owner pid (crashed writer) → lock reclaimed, sale succeeds.
  const deadChild = spawn(process.execPath, ["-e", ""]);
  await new Promise((resolve) => deadChild.once("exit", resolve));
  writeLock(`${deadChild.pid}:dead-owner-token\n`);
  const sale = soldCodes.recordSale({ code: "PRO-DEADLOCK-001", tier: "pro", stripeSessionId: "cs_lock_dead_owner" });
  assert.equal(sale.code, "PRO-DEADLOCK-001", "stale lock from dead pid is reclaimed");
  assert.equal(fs.existsSync(LOCK_FILE), false, "lock released after the critical section");

  const liveChild = spawn(process.execPath, ["-e", "setTimeout(() => {}, 30000)"]);
  try {
    // 2. Live owner with a fresh lock → still fails closed.
    writeLock(`${liveChild.pid}:live-owner-token\n`);
    assert.throws(
      () => soldCodes.recordSale({ code: "PRO-LIVELOCK-001", tier: "pro", stripeSessionId: "cs_lock_live_owner" }),
      /sold_code_store_busy/,
      "fresh lock from a live owner stays busy"
    );
    assert.equal(fs.existsSync(LOCK_FILE), true, "live lock is never reclaimed");

    // 3. Live owner but abandoned lock (older than any real critical section)
    //    → stale by age, reclaimed.
    writeLock(`${liveChild.pid}:live-owner-token\n`, 120000);
    const aged = soldCodes.recordSale({ code: "PRO-AGEDLOCK-01", tier: "pro", stripeSessionId: "cs_lock_aged" });
    assert.equal(aged.code, "PRO-AGEDLOCK-01", "abandoned lock is reclaimed by age");
  } finally {
    liveChild.kill("SIGKILL");
  }

  // 4. Store content survived both reclaim paths.
  const codes = soldCodes.loadAsActivationCodes();
  assert.deepEqual(codes.map((c) => c.code).sort(), ["PRO-AGEDLOCK-01", "PRO-DEADLOCK-001"]);

  fs.rmSync(dir, { recursive: true, force: true });
  console.log("sold_codes_lock.test.js: PASS");
  process.exit(0);
})().catch((err) => {
  fs.rmSync(dir, { recursive: true, force: true });
  console.error(err);
  process.exit(1);
});
