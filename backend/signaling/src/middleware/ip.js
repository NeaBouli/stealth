"use strict";

function isTrustProxyEnabled() {
  const value = process.env.TRUST_PROXY;
  return value === "true" || value === "1";
}

// TRUST_PROXY is an explicit deployment contract: exactly one trusted reverse
// proxy appends the real peer address to X-Forwarded-For. Reading from the
// right prevents a client-supplied prefix from creating arbitrary IP buckets.
function getClientIp(req) {
  if (isTrustProxyEnabled()) {
    const xff = req.headers["x-forwarded-for"];
    if (xff) {
      const forwarded = xff.split(",").map(value => value.trim()).filter(Boolean);
      const clientIp = forwarded[forwarded.length - 1];
      if (clientIp) return clientIp;
    }
  }
  return req.socket.remoteAddress;
}

module.exports = { getClientIp, isTrustProxyEnabled };
