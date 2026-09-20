"use strict";

module.exports = function subscriptionHandlers(ctx) {
  const {
    activationCodes, fcmTokens, giftCodes,
    getClientId, sendToClient,
    saveActivationCodes, saveGiftCodes,
    subscriptions, fcm, issueEntitlementToken, verifyEntitlementToken, entitlementOrderHash,
    verifyPlaySubscription, acknowledgePlaySubscription, playBillingEnabled,
    identityRegistry,
  } = ctx;

  const BLOCKED_CODES = ["BETA-PRO0-2026", "BETA-PREM-2026"];
  const LEGACY_UNSIGNED_PRODUCTS = new Set([
    "pro_lifetime",
    "pro_monthly",
    "premium_lifetime",
    "premium_monthly",
    "securecall_activation",
    "securecall_pro_lifetime",
    "securecall_premium_lifetime",
  ]);

  function requiresSignedEntitlement(entry) {
    if (typeof entry.productKey !== "string" || !entry.productKey) return false;
    return !LEGACY_UNSIGNED_PRODUCTS.has(entry.productKey);
  }

  function signedActivation(entry, clientId, extra) {
    let entitlementToken = null;
    try {
      entitlementToken = typeof issueEntitlementToken === "function"
        ? issueEntitlementToken({
            subject: clientId,
            productKey: entry.productKey || "securecall_activation",
            tier: entry.tier,
            externalOrderId: entry.stripeSessionId || entry.code,
            catalogVersion: entry.catalogVersion || undefined,
            offerVersion: entry.offerVersion || undefined,
            releaseId: entry.releaseId || undefined,
          })
        : null;
    } catch (error) {
      console.error("[ACTIVATION] Entitlement signing failed:", error.message);
    }
    if (!entitlementToken && requiresSignedEntitlement(entry)) {
      return {
        type: "ACTIVATE_CODE_RESULT",
        success: false,
        error: "entitlement_temporarily_unavailable",
      };
    }
    return {
      type: "ACTIVATE_CODE_RESULT",
      success: true,
      tier: entry.tier,
      ...extra,
      ...(entitlementToken ? { entitlementToken } : {}),
    };
  }

  function authorizedSubjects(clientId) {
    const subjects = [clientId];
    if (!identityRegistry || typeof identityRegistry.aliasesFor !== "function") return subjects;
    try {
      for (const alias of identityRegistry.aliasesFor(clientId)) {
        if (typeof alias === "string" && alias && !subjects.includes(alias)) subjects.push(alias);
        if (subjects.length >= 9) break;
      }
    } catch {
      // Canonical entitlements remain usable; legacy migration stays fail-closed.
    }
    return subjects;
  }

  function migrateActivationBinding(entry, sourceSubject, targetSubject) {
    const previousHadUsedBy = Object.prototype.hasOwnProperty.call(entry, "usedBy");
    const previousUsedBy = entry.usedBy;
    const previousHadCurrentUses = Object.prototype.hasOwnProperty.call(entry, "currentUses");
    const previousCurrentUses = entry.currentUses;
    const devices = Array.isArray(previousUsedBy) ? [...previousUsedBy]
      : (previousUsedBy ? [previousUsedBy] : []);
    if (sourceSubject === targetSubject) return devices;
    if (!devices.includes(sourceSubject)) return null;

    const migrated = [];
    for (const device of devices) {
      const value = device === sourceSubject ? targetSubject : device;
      if (!migrated.includes(value)) migrated.push(value);
    }
    entry.usedBy = migrated;
    entry.currentUses = migrated.length;
    let persisted = false;
    try { persisted = saveActivationCodes() !== false; } catch { persisted = false; }
    if (persisted) return migrated;

    if (previousHadUsedBy) entry.usedBy = previousUsedBy;
    else delete entry.usedBy;
    if (previousHadCurrentUses) entry.currentUses = previousCurrentUses;
    else delete entry.currentUses;
    return null;
  }

  return {
    SUBSCRIPTION_VERIFY(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        ws.send(JSON.stringify({ type: "ERROR", error: "not_registered" }));
        return;
      }
      const { requestId, purchaseToken, productId, packageName, catalogVersion } = msg;
      const validRequestId = typeof requestId === "string" && /^[a-f0-9-]{36}$/i.test(requestId);
      if (playBillingEnabled !== true) {
        ws.send(JSON.stringify({
          type: "SUBSCRIPTION_VERIFY_ACK",
          success: false,
          requestId: validRequestId ? requestId : "",
          tier: "FREE",
          expiresAt: 0,
          productId: "",
          packageName: "",
          catalogVersion: "",
          error: "play_billing_disabled",
        }));
        return;
      }
      if (!validRequestId || !purchaseToken || !productId || !packageName || !catalogVersion
          || typeof verifyPlaySubscription !== "function") {
        ws.send(JSON.stringify({
          type: "SUBSCRIPTION_VERIFY_ACK",
          success: false,
          requestId: validRequestId ? requestId : "",
          tier: "FREE",
          expiresAt: 0,
          productId: "",
          packageName: "",
          catalogVersion: "",
          error: "invalid_subscription_verification_request",
        }));
        return;
      }
      const complete = result => {
        if (!result || result.catalogVersion !== catalogVersion) {
          throw new Error("catalog_version_mismatch");
        }
        const stored = subscriptions.recordVerifiedSubscription(
          myClientId,
          purchaseToken,
          productId,
          result.tier,
          result.expiresAt,
          packageName,
          catalogVersion
        );
        const sendSuccess = () => {
          ws.send(JSON.stringify({
            type: "SUBSCRIPTION_VERIFY_ACK",
            success: true,
            requestId,
            tier: stored.tier,
            expiresAt: stored.expiresAt,
            productId,
            packageName,
            catalogVersion,
          }));
          console.log(`[SUBSCRIPTION] Verified: ${myClientId}, tier=${stored.tier}, product=${productId}`);
        };
        if (result.needsAcknowledgement !== false) {
          if (typeof acknowledgePlaySubscription !== "function") {
            throw new Error("subscription_acknowledgement_unavailable");
          }
          let acknowledgement;
          try {
            acknowledgement = acknowledgePlaySubscription(packageName, productId, purchaseToken);
          } catch (error) {
            subscriptions.expireSubscription(myClientId);
            throw error;
          }
          if (acknowledgement && typeof acknowledgement.then === "function") {
            return acknowledgement.then(sendSuccess).catch(error => {
              subscriptions.expireSubscription(myClientId);
              throw error;
            });
          }
        }
        sendSuccess();
      };
      const reject = error => {
        console.warn("[SUBSCRIPTION] Google Play verification rejected:", error.message);
        ws.send(JSON.stringify({
          type: "SUBSCRIPTION_VERIFY_ACK",
          success: false,
          requestId,
          tier: "FREE",
          expiresAt: 0,
          productId,
          packageName,
          catalogVersion,
          error: "subscription_verification_failed",
        }));
      };
      try {
        const verification = verifyPlaySubscription(packageName, productId, purchaseToken);
        if (verification && typeof verification.then === "function") verification.then(complete).catch(reject);
        else {
          const completion = complete(verification);
          if (completion && typeof completion.then === "function") completion.catch(reject);
        }
      } catch (error) {
        reject(error);
      }
    },

    ACTIVATE_CODE(ws, connId, msg) {
      const requestId = typeof msg.requestId === "string" && /^[a-f0-9-]{36}$/i.test(msg.requestId)
        ? msg.requestId : undefined;
      const respond = result => ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", ...result,
        ...(requestId ? { requestId } : {}) }));
      const myClientId = getClientId(connId);
      if (!myClientId) {
        return respond({ success: false, error: "not_registered" });
      }
      const code = (msg.code || "").trim().toUpperCase();
      if (!code) {
        return respond({ success: false, error: "missing_code" });
      }

      if (BLOCKED_CODES.includes(code)) {
        console.log("[ACTIVATION] Blocked expired BETA code:", code);
        return respond({ success: false, error: "expired", message: "This beta code has expired. Thank you for testing!" });
      }

      const entry = activationCodes.find(c => c.code === code);

      if (!entry && giftCodes.has(code)) {
        const gift = giftCodes.get(code);
        if (gift.used) return respond({ success: false, error: "already_used" });
        if (new Date(gift.expires) < new Date()) return respond({ success: false, error: "expired" });
        const previousUsed = gift.used;
        const hadUsedBy = Object.prototype.hasOwnProperty.call(gift, "usedBy");
        const previousUsedBy = gift.usedBy;
        gift.used = true;
        gift.usedBy = myClientId;
        if (saveGiftCodes() === false) {
          gift.used = previousUsed;
          if (hadUsedBy) gift.usedBy = previousUsedBy;
          else delete gift.usedBy;
          return respond({ success: false, error: "entitlement_temporarily_unavailable" });
        }
        console.log("[GIFT] Code redeemed:", code.substring(0, 4) + "****", "-> tier:", gift.tier, "by:", myClientId);
        return respond({ success: true, tier: gift.tier });
      }

      if (!entry) {
        console.log("[ACTIVATION] Invalid code attempted:", code.substring(0, 4) + "****");
        return respond({ success: false, error: "invalid" });
      }
      if (entry.revoked === true) {
        return respond({
          success: false,
          error: "entitlement_revoked",
        });
      }

      const devices = Array.isArray(entry.usedBy) ? [...entry.usedBy] : (entry.usedBy ? [entry.usedBy] : []);
      const boundSubject = authorizedSubjects(myClientId).find(subject => devices.includes(subject));

      if (boundSubject) {
        const activation = signedActivation(entry, myClientId, {
          slot: devices.indexOf(boundSubject) + 1,
          maxSlots: entry.maxUses,
        });
        if (!activation.success) return respond(activation);
        if (boundSubject !== myClientId
            && !migrateActivationBinding(entry, boundSubject, myClientId)) {
          return respond({ success: false, error: "entitlement_temporarily_unavailable" });
        }
        console.log("[ACTIVATION] Code re-activated:", code.substring(0, 4) + "****", "by:", myClientId);
        return respond(activation);
      }

      if (entry.expires) {
        const expiresAt = Date.parse(entry.expires);
        if (!Number.isFinite(expiresAt) || expiresAt < Date.now()) {
          return respond({ success: false, error: "expired" });
        }
      }

      if (devices.length >= entry.maxUses) {
        console.log("[ACTIVATION] Code exhausted:", code.substring(0, 4) + "****", "devices:", devices.length, "/", entry.maxUses, "attempted:", myClientId);
        return respond({ success: false, error: "max_devices", message: `Code already used on ${entry.maxUses} devices` });
      }

      const activation = signedActivation(entry, myClientId, {
        slot: devices.length + 1,
        maxSlots: entry.maxUses,
      });
      if (!activation.success) return respond(activation);

      devices.push(myClientId);
      entry.usedBy = devices;
      entry.currentUses = devices.length;
      const slot = devices.length;
      if (saveActivationCodes() === false) {
        devices.pop();
        entry.currentUses = devices.length;
        return respond({
          success: false,
          error: "entitlement_temporarily_unavailable",
        });
      }
      console.log("[ACTIVATION] Code redeemed:", code.substring(0, 4) + "****", "-> tier:", entry.tier, "by:", myClientId, "slot:", slot + "/" + entry.maxUses);
      return respond(activation);
    },

    REFRESH_ENTITLEMENT(ws, connId, msg) {
      const requestId = typeof msg.requestId === "string" && /^[a-f0-9-]{36}$/i.test(msg.requestId)
        ? msg.requestId : undefined;
      const respond = result => ws.send(JSON.stringify({ type: "ENTITLEMENT_REFRESH_RESULT", ...result,
        ...(requestId ? { requestId } : {}) }));
      const myClientId = getClientId(connId);
      if (!myClientId || typeof msg.entitlementToken !== "string") {
        return respond({ success: false, error: "invalid_entitlement" });
      }
      // Keep tester proofs out of commercial renewal until verified enrollment
      // persistence and its dedicated renewal adapter are available.
      if (msg.entitlementToken.startsWith("sct1.")) {
        return respond({ success: false, error: "tester_enrollment_unavailable" });
      }
      try {
        let claims;
        let verifiedSubject;
        let verificationError;
        for (const subject of authorizedSubjects(myClientId)) {
          try {
            claims = verifyEntitlementToken(msg.entitlementToken, {
              expectedSubject: subject,
              expiryGraceSeconds: 7 * 24 * 60 * 60,
            });
            verifiedSubject = subject;
            break;
          } catch (error) {
            if (error && error.code === "ENTITLEMENT_SIGNING_UNAVAILABLE") throw error;
            verificationError = error;
          }
        }
        if (!claims || !verifiedSubject) throw verificationError || new Error("invalid entitlement");
        const entry = activationCodes.find(candidate => {
          const devices = Array.isArray(candidate.usedBy) ? candidate.usedBy : (candidate.usedBy ? [candidate.usedBy] : []);
          const reference = candidate.stripeSessionId || candidate.code;
          return candidate.revoked !== true
            && devices.includes(verifiedSubject)
            && candidate.productKey === claims.product
            && entitlementOrderHash(reference) === claims.order
            && (claims.v !== "2" || (
              candidate.catalogVersion === claims.catalog
              && candidate.offerVersion === claims.offer
              && candidate.releaseId === claims.release
            ));
        });
        if (!entry) {
          return respond({ success: false, error: "entitlement_revoked" });
        }
        const refreshed = signedActivation(entry, myClientId, {});
        if (!refreshed.success || !refreshed.entitlementToken) {
          return respond({
            success: false,
            error: "entitlement_temporarily_unavailable",
          });
        }
        if (verifiedSubject !== myClientId
            && !migrateActivationBinding(entry, verifiedSubject, myClientId)) {
          return respond({
            success: false,
            error: "entitlement_temporarily_unavailable",
          });
        }
        return respond({
          success: true,
          entitlementToken: refreshed.entitlementToken,
        });
      } catch (error) {
        if (error && error.code === "ENTITLEMENT_SIGNING_UNAVAILABLE") {
          console.warn("[ACTIVATION] Entitlement refresh deferred: signer unavailable");
          return respond({
            success: false,
            error: "entitlement_temporarily_unavailable",
          });
        }
        console.warn("[ACTIVATION] Entitlement refresh rejected:", error.message);
        return respond({ success: false, error: "invalid_entitlement" });
      }
    },

    INVITE_ACCEPTED(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) return ws.send(JSON.stringify({ type: "ERROR", error: "not_registered" }));
      const inviterSecureId = typeof msg.inviterSecureId === "string" ? msg.inviterSecureId.trim() : "";
      if (!inviterSecureId) return ws.send(JSON.stringify({ type: "ERROR", error: "missing_inviterSecureId" }));

      const fcmToken = fcmTokens.get(inviterSecureId);
      if (fcmToken && fcm.isInitialized()) {
        fcm.sendDataMessage(fcmToken, { type: "INVITE_ACCEPTED", newUserSecureId: myClientId, message: myClientId + " joined SecureCall and added you as a contact!" });
      }
      sendToClient(inviterSecureId, { type: "INVITE_ACCEPTED", newUserSecureId: myClientId, message: myClientId + " joined SecureCall!" });
      console.log("[INVITE] Accepted (WS) from", myClientId, "to", inviterSecureId);
      return ws.send(JSON.stringify({ type: "INVITE_ACCEPTED_ACK", ok: true }));
    },
  };
};
