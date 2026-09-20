"use strict";

// STX-08: the modular route enforces the same bounded per-client registration
// limiter as the inline route in server.js. Callers may inject a compatible
// middleware (tests); production falls back to the shared env-configured
// default limiter so both paths share one bounded bucket store.
const { pkdRegistrationRateLimit } = require("../security/pkd_registration_limiter");

function setup(app, { pkd, requireAdmin, registerRateLimit }) {
  const limiter = registerRateLimit || pkdRegistrationRateLimit;

  app.post("/key/register", limiter, (req, res) => {
    const { publicKey } = req.body || {};
    if (!publicKey || typeof publicKey !== "string") {
      return res.status(400).json({
        error: "missing_public_key",
        message: "Field 'publicKey' is required"
      });
    }
    if (publicKey.length > 256) {
      return res.status(400).json({ error: "public_key_too_large" });
    }
    const entry = pkd.registerKey(publicKey);
    res.status(201).json({ keyId: entry.keyId, publicKey: entry.publicKey, created: entry.created });
  });

  app.get("/key/:id", (req, res) => {
    const entry = pkd.getKey(req.params.id);
    if (!entry) {
      return res.status(404).json({ error: "key_not_found" });
    }
    res.json({ keyId: entry.keyId, publicKey: entry.publicKey, created: entry.created });
  });

  app.put("/key/:id", requireAdmin, (req, res) => {
    const { publicKey } = req.body || {};
    if (!publicKey || typeof publicKey !== "string") {
      return res.status(400).json({
        error: "missing_public_key",
        message: "Field 'publicKey' is required"
      });
    }
    if (publicKey.length > 256) {
      return res.status(400).json({ error: "public_key_too_large" });
    }
    const entry = pkd.rotateKey(req.params.id, publicKey);
    if (!entry) {
      return res.status(404).json({ error: "key_not_found" });
    }
    res.json({ keyId: entry.keyId, publicKey: entry.publicKey, updated: entry.updated });
  });

  app.delete("/key/:id", requireAdmin, (req, res) => {
    const deleted = pkd.deleteKey(req.params.id);
    if (!deleted) {
      return res.status(404).json({ error: "key_not_found" });
    }
    res.json({ ok: true });
  });
}

module.exports = { setup };
