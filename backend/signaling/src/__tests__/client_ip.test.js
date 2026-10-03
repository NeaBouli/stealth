"use strict";
const assert = require("node:assert/strict");
const { getClientIp, isTrustProxyEnabled } = require("../middleware/ip");

function withEnv(env, fn) {
  const saved = { TRUST_PROXY: process.env.TRUST_PROXY, RAILWAY_ENVIRONMENT: process.env.RAILWAY_ENVIRONMENT };
  delete process.env.TRUST_PROXY;
  delete process.env.RAILWAY_ENVIRONMENT;
  Object.assign(process.env, env);
  try { fn(); } finally {
    for (const [key, value] of Object.entries(saved)) {
      if (value === undefined) delete process.env[key]; else process.env[key] = value;
    }
  }
}

const req = (xff, remote = "10.0.0.7") => ({ headers: xff === undefined ? {} : { "x-forwarded-for": xff }, socket: { remoteAddress: remote } });

// Default: no proxy trust, X-Forwarded-For is ignored entirely.
withEnv({}, () => {
  assert.equal(isTrustProxyEnabled(), false);
  assert.equal(getClientIp(req("1.2.3.4")), "10.0.0.7");
});

// Railway alone no longer implies trust; TRUST_PROXY must be set explicitly.
withEnv({ RAILWAY_ENVIRONMENT: "production" }, () => {
  assert.equal(getClientIp(req("6.6.6.6, 203.0.113.9")), "10.0.0.7");
});

// With TRUST_PROXY the rightmost entry (appended by the one trusted proxy) wins,
// so a client-supplied prefix cannot choose its own rate-limit bucket.
for (const flag of ["true", "1"]) {
  withEnv({ TRUST_PROXY: flag, RAILWAY_ENVIRONMENT: "production" }, () => {
    assert.equal(isTrustProxyEnabled(), true);
    assert.equal(getClientIp(req("6.6.6.6, 203.0.113.9")), "203.0.113.9");
    assert.equal(getClientIp(req("spoofed, also-spoofed , 198.51.100.4 ")), "198.51.100.4");
    assert.equal(getClientIp(req("203.0.113.10")), "203.0.113.10");
    // Empty or missing header falls back to the socket peer.
    assert.equal(getClientIp(req(" , ")), "10.0.0.7");
    assert.equal(getClientIp(req(undefined)), "10.0.0.7");
  });
}

// Other values do not enable trust.
withEnv({ TRUST_PROXY: "yes" }, () => {
  assert.equal(getClientIp(req("6.6.6.6")), "10.0.0.7");
});

console.log("client_ip.test.js: PASS");
