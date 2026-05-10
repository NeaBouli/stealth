"use strict";

function setup(app, { licenses, requireAdmin }) {
  app.get("/licenses/status", (req, res) => {
    res.json(licenses.getStatus());
  });

  app.post("/admin/simulate-sale", requireAdmin, (req, res) => {
    const { tier, count } = req.body;
    if (!tier || !["pro_lifetime", "premium_lifetime"].includes(tier)) {
      return res.status(400).json({ error: "Invalid tier" });
    }
    const n = Math.min(count || 1, 20);
    for (let i = 0; i < n; i++) licenses.recordSale(tier);
    res.json({ ok: true, simulated: n, status: licenses.getStatus() });
  });

  app.post("/admin/reset-licenses", requireAdmin, (req, res) => {
    licenses.LICENSES.pro_lifetime.sold = 0;
    licenses.LICENSES.premium_lifetime.sold = 0;
    licenses.saveLicenses();
    console.log("[LICENSES] Reset to 0 by admin");
    res.json({ ok: true, status: licenses.getStatus() });
  });
}

module.exports = { setup };
