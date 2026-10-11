"use strict";

// Fix HIGH-001 (2026-04-16): no wildcard fallback. If allowedOrigins is not set
// or empty, we only emit Allow-Origin for requests from stealthx.tech.
const DEFAULT_ALLOWED_ORIGINS = ["https://stealthx.tech", "https://www.stealthx.tech"];

function makeCorsMiddleware(allowedOrigins) {
  return function corsMiddleware(req, res, next) {
    const origins = (allowedOrigins && allowedOrigins.length > 0)
      ? allowedOrigins
      : DEFAULT_ALLOWED_ORIGINS;
    const origin = req.headers.origin;
    if (origin && origins.includes(origin)) {
      res.setHeader("Access-Control-Allow-Origin", origin);
      res.setHeader("Vary", "Origin");
    }
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Admin-Key");
    if (req.method === "OPTIONS") return res.sendStatus(204);
    next();
  };
}

// STX-15: WebSocket upgrades follow the same allowlist as CORS. Native clients send no
// Origin and pass; a browser Origin outside the allowlist (configured, or the defaults
// when none is configured) is rejected, so foreign web pages cannot open /signal.
function isWebSocketOriginAllowed(origin, configuredOrigins, defaultOrigins = DEFAULT_ALLOWED_ORIGINS) {
  if (!origin) return true;
  const origins = (configuredOrigins && configuredOrigins.length > 0) ? configuredOrigins : defaultOrigins;
  return origins.includes(origin);
}

module.exports = { makeCorsMiddleware, isWebSocketOriginAllowed, DEFAULT_ALLOWED_ORIGINS };
