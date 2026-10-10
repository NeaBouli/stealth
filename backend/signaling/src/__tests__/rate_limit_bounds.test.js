"use strict";

// STX-17: rate_limit.js flood buckets must be bounded — hard cap with
// least-recently-active eviction, idle pruning, unchanged per-conn limits.
// Run: node src/__tests__/rate_limit_bounds.test.js

const assert = require("node:assert/strict");
const rateLimit = require("../rate_limit");

const CAP = 10000;

// conn-0 is created first and hammered over the limit, so it holds the oldest
// lastSeen once the cap fills — the deterministic eviction candidate.
assert.equal(rateLimit.registerEvent("conn-0"), true);
for (let i = 0; i < 41; i++) rateLimit.registerEvent("conn-0");
assert.equal(rateLimit.registerEvent("conn-0"), false, "per-conn text limit stays 40 events/10s");

for (let i = 1; i < CAP; i++) {
  assert.equal(rateLimit.registerEvent(`conn-${i}`), true);
}
assert.equal(rateLimit.bucketCount(), CAP, "map fills up to the hard cap");

// One more distinct connection must evict (oldest-first), never grow past cap.
assert.equal(rateLimit.registerEvent("conn-overflow"), true);
assert.equal(rateLimit.bucketCount(), CAP, "hard cap holds under overflow");
assert.equal(rateLimit.registerEvent("conn-0"), true, "evicted bucket restarts instead of staying limited");
assert.equal(rateLimit.registerEvent("conn-1"), true, "untouched buckets keep counting");

// Binary limit unchanged: 1000 frames/10s per connection.
for (let i = 0; i < 1000; i++) assert.equal(rateLimit.registerBinaryEvent("bin-1"), true);
assert.equal(rateLimit.registerBinaryEvent("bin-1"), false, "per-conn binary limit stays 1000 frames/10s");

// Idle pruning drops buckets with no recent events.
rateLimit._pruneIdle(Date.now() + 120000);
assert.equal(rateLimit.bucketCount(), 0, "idle prune removes every stale bucket");

// clear() still removes a live bucket on disconnect.
rateLimit.registerEvent("conn-x");
assert.equal(rateLimit.bucketCount(), 1);
rateLimit.clear("conn-x");
assert.equal(rateLimit.bucketCount(), 0, "clear() still works");

console.log("rate_limit_bounds.test.js: PASS");
