"use strict";

// The registry is injected only after operator provisioning; absent means disabled.
module.exports = function testerLicenseHandlers({ getClientId, testerLicenseRegistry }) {
  const actions = {
    TESTER_ACTIVATION_BEGIN: ["begin", "TESTER_ACTIVATION_CHALLENGE"],
    TESTER_ACTIVATION_COMPLETE: ["activate", "TESTER_ACTIVATION_RESULT"],
    TESTER_RENEWAL_BEGIN: ["beginRefresh", "TESTER_RENEWAL_CHALLENGE"],
    TESTER_RENEWAL_COMPLETE: ["refresh", "TESTER_RENEWAL_RESULT"],
  };
  return Object.fromEntries(Object.entries(actions).map(([type, [method, resultType]]) => [type,
    (ws, connId, msg) => {
      const requestId = typeof msg.requestId === "string" && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(msg.requestId)
        ? msg.requestId : "";
      const respond = fields => ws.send(JSON.stringify({ type: resultType, requestId, ...fields }));
      const subject = getClientId(connId);
      if (!subject || !requestId) return respond({ success: false, error: "invalid_tester_request" });
      if (!testerLicenseRegistry) return respond({ success: false, error: "tester_license_unavailable" });
      try {
        // Subject always comes from the registered session, never the message.
        let input;
        if (method === "begin") input = { subject, code: msg.code, keyHash: msg.keyHash, packageName: msg.packageName };
        else if (method === "beginRefresh") input = { subject, token: msg.entitlementToken, keyHash: msg.keyHash };
        else input = { subject, challengeId: msg.challengeId, signature: msg.signature };
        const result = testerLicenseRegistry[method](input);
        return respond(typeof result === "string"
          ? { success: true, entitlementToken: result }
          : { success: true, challengeId: result.challengeId, challenge: result.challenge });
      } catch {
        // Never include code, storage path, signer or enrollment details in errors.
        return respond({ success: false, error: "tester_license_unavailable" });
      }
    },
  ]));
};
