"use strict";

module.exports = function subscriptionHandlers(ctx) {
  const {
    activationCodes, walletMappings, fcmTokens, giftCodes,
    getClientId, sendToClient,
    saveActivationCodes, saveWalletMappings, saveGiftCodes,
    subscriptions, fcm, verifyIfrLock,
  } = ctx;

  const BLOCKED_CODES = ["BETA-PRO0-2026", "BETA-PREM-2026"];

  return {
    SUBSCRIPTION_VERIFY(ws, connId, msg) {
      const myClientId = getClientId(connId);
      if (!myClientId) {
        ws.send(JSON.stringify({ type: "ERROR", error: "not_registered" }));
        return;
      }
      const { purchaseToken, productId } = msg;
      if (!purchaseToken || !productId) {
        ws.send(JSON.stringify({ type: "ERROR", message: "Missing purchaseToken or productId" }));
        return;
      }
      const result = subscriptions.verifySubscription(myClientId, purchaseToken, productId);
      ws.send(JSON.stringify({ type: "SUBSCRIPTION_VERIFY_ACK", tier: result.tier, expiresAt: result.expiresAt }));
      console.log(`[SUBSCRIPTION] Verified: ${myClientId}, tier=${result.tier}, product=${productId}`);
    },

    ACTIVATE_CODE(ws, connId, msg) {
      const code = (msg.code || "").trim().toUpperCase();
      if (!code) {
        return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: false, error: "missing_code" }));
      }

      if (BLOCKED_CODES.includes(code)) {
        console.log("[ACTIVATION] Blocked expired BETA code:", code);
        return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: false, error: "expired", message: "This beta code has expired. Thank you for testing!" }));
      }

      const entry = activationCodes.find(c => c.code === code);

      if (!entry && giftCodes.has(code)) {
        const gift = giftCodes.get(code);
        if (gift.used) return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: false, error: "already_used" }));
        if (new Date(gift.expires) < new Date()) return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: false, error: "expired" }));
        gift.used = true;
        gift.usedBy = getClientId(connId);
        saveGiftCodes();
        const myClientId = getClientId(connId);
        console.log("[GIFT] Code redeemed:", code.substring(0, 4) + "****", "-> tier:", gift.tier, "by:", myClientId);
        return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: true, tier: gift.tier }));
      }

      if (!entry) {
        console.log("[ACTIVATION] Invalid code attempted:", code.substring(0, 4) + "****");
        return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: false, error: "invalid" }));
      }

      const myClientId = getClientId(connId);
      const devices = Array.isArray(entry.usedBy) ? entry.usedBy : (entry.usedBy ? [entry.usedBy] : []);

      if (devices.includes(myClientId)) {
        console.log("[ACTIVATION] Code re-activated:", code.substring(0, 4) + "****", "by:", myClientId);
        return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: true, tier: entry.tier, code, slot: devices.indexOf(myClientId) + 1, maxSlots: entry.maxUses }));
      }

      if (devices.length >= entry.maxUses) {
        console.log("[ACTIVATION] Code exhausted:", code.substring(0, 4) + "****", "devices:", devices.length, "/", entry.maxUses, "attempted:", myClientId);
        return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: false, error: "max_devices", message: `Code already used on ${entry.maxUses} devices` }));
      }

      devices.push(myClientId);
      entry.usedBy = devices;
      entry.currentUses = devices.length;
      const slot = devices.length;
      console.log("[ACTIVATION] Code redeemed:", code.substring(0, 4) + "****", "-> tier:", entry.tier, "by:", myClientId, "slot:", slot + "/" + entry.maxUses);
      saveActivationCodes();
      return ws.send(JSON.stringify({ type: "ACTIVATE_CODE_RESULT", success: true, tier: entry.tier, code, slot, maxSlots: entry.maxUses }));
    },

    VERIFY_IFR_LOCK(ws, connId, msg) {
      const wallet = (msg.walletAddress || "").trim();
      if (!wallet || !wallet.match(/^0x[0-9a-fA-F]{40}$/)) {
        return ws.send(JSON.stringify({ type: "IFR_LOCK_RESULT", success: false, error: "invalid_address" }));
      }

      const myClientId = getClientId(connId);
      const existing = walletMappings.find(w => w.wallet.toLowerCase() === wallet.toLowerCase());
      if (existing && existing.clientId !== myClientId) {
        return ws.send(JSON.stringify({ type: "IFR_LOCK_RESULT", success: false, error: "wallet_bound", boundTo: existing.clientId.substring(0, 8) + "..." }));
      }

      console.log("[IFR] Verifying lock for wallet:", wallet, "client:", myClientId);

      verifyIfrLock(wallet).then(result => {
        if (result.success) {
          const idx = walletMappings.findIndex(w => w.wallet.toLowerCase() === wallet.toLowerCase());
          if (idx >= 0) {
            walletMappings[idx].clientId = myClientId;
            walletMappings[idx].tier = result.tier;
            walletMappings[idx].lastVerified = Date.now();
          } else {
            walletMappings.push({ wallet: wallet.toLowerCase(), clientId: myClientId, tier: result.tier, lastVerified: Date.now() });
          }
          saveWalletMappings();
          console.log("[IFR] Lock verified:", wallet, "->", result.tier, "(", result.lockedAmount, "IFR)");
        }
        // H-07: guard against closed WS after async
        try {
          if (ws.readyState === 1) {
            ws.send(JSON.stringify({ type: "IFR_LOCK_RESULT", success: result.success, tier: result.tier || "", lockedAmount: result.lockedAmount || "0", walletAddress: wallet, error: result.error || "" }));
          }
        } catch (_) {}
      }).catch(e => {
        console.error("[IFR] Verification error:", e.message);
        try {
          if (ws.readyState === 1) ws.send(JSON.stringify({ type: "IFR_LOCK_RESULT", success: false, error: "server_error" }));
        } catch (_) {}
      });
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
