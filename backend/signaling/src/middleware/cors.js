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

module.exports = { makeCorsMiddleware, DEFAULT_ALLOWED_ORIGINS };
