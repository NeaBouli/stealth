"use strict";

const { revokeActivationCode } = require("./activation_store");

function setupActivationAdminRoutes(app, requireAdmin, revoke = revokeActivationCode) {
  app.post("/admin/activation-codes/revoke", requireAdmin, (req, res) => {
    const result = revoke(req.body?.code);
    if (result.success) {
      console.log("[ACTIVATION] Activation code revoked by admin");
      return res.json({ ok: true });
    }
    if (result.error === "invalid_code") return res.status(400).json({ error: result.error });
    if (result.error === "not_found") return res.status(404).json({ error: result.error });
    return res.status(503).json({ error: "persistence_failed" });
  });
}

module.exports = { setupActivationAdminRoutes };
