"use strict";

const crypto = require("crypto");

// Admin reversal of a disputed legacy alias migration (#144 review F2). Revokes the alias,
// drops the push route the canonical identity inherited from that legacy id, and records
// the reversal. Paid rights moved through the alias are not touched here; the response
// says so, and they follow the operator entitlement process.
function setupIdentityAliasAdminRoutes(app, requireAdmin, {
  identityRegistry, identityMigrationAudit, fcmTokens, saveFcmTokens,
}) {
  app.post("/admin/identity/alias/revoke", requireAdmin, (req, res) => {
    const alias = typeof req.body?.alias === "string" ? req.body.alias : "";
    const reason = typeof req.body?.reason === "string" ? req.body.reason.slice(0, 200) : "";
    if (!alias || !reason) return res.status(400).json({ error: "alias_and_reason_required" });
    if (!identityRegistry || !identityMigrationAudit) {
      return res.status(503).json({ error: "identity_service_unavailable" });
    }

    const existing = identityRegistry.getAlias(alias);
    if (!existing) return res.status(404).json({ error: "identity_alias_unknown" });
    const identityId = existing.identityId;
    const bindRecord = identityMigrationAudit.read()
      .filter(record => record.event === "alias_bind_authorized" && record.alias === alias).at(-1);
    try {
      identityMigrationAudit.append("alias_revoked", { alias, identityId, reason });
    } catch {
      return res.status(503).json({ error: "identity_migration_audit_unavailable" });
    }

    let result;
    try {
      result = identityRegistry.revokeAlias(alias);
    } catch (error) {
      if (error?.code === "identity_alias_unknown") return res.status(404).json({ error: error.code });
      return res.status(503).json({ error: "identity_service_unavailable" });
    }

    let routeRemoved = false;
    const canonical = result.identityId;
    const token = fcmTokens.get(canonical);
    if (token && bindRecord?.routeHash
        && crypto.createHash("sha256").update(token).digest("base64url") === bindRecord.routeHash) {
      fcmTokens.delete(canonical);
      routeRemoved = saveFcmTokens() !== false;
    }
    return res.json({
      ok: true,
      alias,
      identityId: canonical,
      changed: result.changed,
      inheritedRouteRemoved: routeRemoved,
      entitlementsReviewRequired: true,
    });
  });
}

module.exports = { setupIdentityAliasAdminRoutes };
