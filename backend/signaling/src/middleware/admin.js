"use strict";

// BUG-076: Only accept admin key via header, not query param (prevents log leak)
function makeRequireAdmin(adminApiKey) {
  return function requireAdmin(req, res, next) {
    if (!adminApiKey) {
      return res.status(403).json({ error: "admin_api_disabled" });
    }
    const provided = req.headers["x-admin-key"];
    if (provided !== adminApiKey) {
      return res.status(401).json({ error: "unauthorized" });
    }
    next();
  };
}

module.exports = { makeRequireAdmin };
