"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { logIp, truncateIp } = require("../security/log_ip");

// IPv4 and IPv4-mapped IPv6 are reduced to their /24 network.
assert.equal(truncateIp("203.0.113.57"), "203.0.113.0/24");
assert.equal(truncateIp("::ffff:198.51.100.9"), "198.51.100.0/24");
assert.equal(truncateIp(" 192.0.2.1 "), "192.0.2.0/24");

// IPv6 is reduced to its /48 prefix, also for compressed and zoned forms.
assert.equal(truncateIp("2001:db8:abcd:12:3456:789a:bcde:f012"), "2001:db8:abcd::/48");
assert.equal(truncateIp("2001:db8::1"), "2001:db8:0::/48");
assert.equal(truncateIp("fe80::1%eth0"), "fe80:0:0::/48");
assert.equal(truncateIp("::1"), "0:0:0::/48");

// Anything that is not an IP is never echoed back.
for (const value of [undefined, null, "", "unknown", "not-an-ip", "1.2.3", "<script>", 42]) {
  assert.equal(truncateIp(value), "unknown");
}

// Without a pepper only the reduced network is logged.
assert.equal(logIp("203.0.113.57", {}), "203.0.113.0/24");
assert.equal(logIp("garbage", { ID_HASH_PEPPER: "p" }), "unknown");

// With a pepper a short HMAC tag correlates lines of the same client without the full address.
const env = { ID_HASH_PEPPER: "test-pepper-not-a-secret" };
const a1 = logIp("203.0.113.57", env);
const a2 = logIp("203.0.113.57", env);
const b = logIp("203.0.113.58", env);
assert.match(a1, /^203\.0\.113\.0\/24#[0-9a-f]{10}$/);
assert.equal(a1, a2, "same client -> same tag");
assert.notEqual(a1, b, "different client in the same /24 -> different tag");
assert.ok(!a1.includes("203.0.113.57"), "full address must not appear in the log value");
assert.notEqual(logIp("203.0.113.57", { LOG_IP_PEPPER: "other" }), a1, "LOG_IP_PEPPER takes precedence");

// Source guard: no console output in the signaling sources may print a raw client IP.
// Rate limiting and connection buckets keep the real IP; only log output is reduced.
const srcRoot = path.join(__dirname, "..");
const offenders = [];
(function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== "__tests__" && entry.name !== "node_modules") walk(full);
      continue;
    }
    if (!entry.name.endsWith(".js") || full.endsWith(path.join("security", "log_ip.js"))) continue;
    fs.readFileSync(full, "utf8").split("\n").forEach((line, index) => {
      if (!/console\.(log|warn|error|info|debug)\s*\(/.test(line)) return;
      const withoutWrapped = line.replace(/logIp\([^)]*\)/g, "");
      if (/(\$\{\s*(ip|clientIp|remoteAddress)\s*\}|[,(]\s*(ip|clientIp|remoteAddress|req\.ip)\s*[,)])/.test(withoutWrapped)) {
        offenders.push(`${path.relative(srcRoot, full)}:${index + 1}`);
      }
    });
  }
})(srcRoot);
assert.deepEqual(offenders, [], `raw client IP in log output: ${offenders.join(", ")}`);

console.log("log_ip.test.js: PASS");
