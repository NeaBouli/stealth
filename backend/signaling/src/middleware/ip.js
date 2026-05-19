"use strict";

// Only trusts X-Forwarded-For in production (behind known reverse proxy).
function getClientIp(req) {
  const tp = process.env.TRUST_PROXY;
  if (tp === "true" || tp === "1" || process.env.RAILWAY_ENVIRONMENT) {
    const xff = req.headers["x-forwarded-for"];
    if (xff) {
      const clientIp = xff.split(",")[0].trim();
      if (clientIp) return clientIp;
    }
  }
  return req.socket.remoteAddress;
}

module.exports = { getClientIp };
