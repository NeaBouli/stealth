const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");
const { generateActivationCode } = require("./stripe_handler");
const soldCodes = require("./sold_codes");
const { sendActivationCode } = require("./email_handler");

const MAX_CLOCK_SKEW_SECONDS = 300;
const inFlightOrders = new Set();

const PRODUCTS = Object.freeze({
  "stealthx-securecall-pro-lifetime": { tier: "pro", productKey: "vlabs_securecall_pro_lifetime" },
  "stealthx-securecall-premium-lifetime": { tier: "premium", productKey: "vlabs_securecall_premium_lifetime" },
});
const REVOCATION_REASONS = new Set(["stripe_full_refund", "stripe_dispute"]);

function ordersFile() {
  return process.env.VLABS_FULFILLMENT_ORDERS_FILE
    || path.join(__dirname, "..", "..", "data", "vlabs_fulfillment_orders.json");
}

function loadOrders() {
  try {
    const file = ordersFile();
    if (!fs.existsSync(file)) return {};
    const parsed = JSON.parse(fs.readFileSync(file, "utf8"));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch (error) {
    console.error("[VLABS-FULFILLMENT] Failed to load order registry:", error.message);
    return {};
  }
}

function saveOrders(orders) {
  writeJsonAtomic(ordersFile(), orders);
}

function validString(value, maxLength) {
  return typeof value === "string" && value.length > 0 && value.length <= maxLength;
}

function verifySignature(secret, timestamp, rawBody, receivedSignature) {
  if (!/^\d{10}$/.test(timestamp) || !/^[a-f0-9]{64}$/i.test(receivedSignature)) return false;
  const age = Math.abs(Math.floor(Date.now() / 1000) - Number(timestamp));
  if (age > MAX_CLOCK_SKEW_SECONDS) return false;
  const expected = crypto.createHmac("sha256", secret).update(`${timestamp}.${rawBody}`).digest("hex");
  const expectedBuffer = Buffer.from(expected, "hex");
  const receivedBuffer = Buffer.from(receivedSignature, "hex");
  return expectedBuffer.length === receivedBuffer.length && crypto.timingSafeEqual(expectedBuffer, receivedBuffer);
}

function isRevocationReason(value) {
  return typeof value === "string" && REVOCATION_REASONS.has(value);
}

function authenticateRequest(req, secret) {
  const timestamp = req.get("x-vlabs-timestamp") || "";
  const signature = req.get("x-vlabs-signature") || "";
  return verifySignature(secret, timestamp, JSON.stringify(req.body || {}), signature);
}

function setupVlabsFulfillmentRoute(app, activationCodesRef) {
  app.post("/internal/vlabs/fulfill", async (req, res) => {
    const secret = process.env.VLABS_FULFILLMENT_SECRET;
    if (!secret) return res.status(503).json({ ok: false, error: "Fulfillment is not configured" });
    if (!authenticateRequest(req, secret)) {
      return res.status(401).json({ ok: false, error: "Invalid fulfillment signature" });
    }

    const { externalOrderId, productId, customerEmail } = req.body || {};
    if (!validString(externalOrderId, 128) || !/^cs_[a-zA-Z0-9_]+$/.test(externalOrderId)) {
      return res.status(400).json({ ok: false, error: "Invalid external order ID" });
    }
    if (!validString(productId, 100) || !PRODUCTS[productId]) {
      return res.status(409).json({ ok: false, error: "Product activation is not enabled" });
    }
    if (!validString(customerEmail, 254) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customerEmail)) {
      return res.status(400).json({ ok: false, error: "Invalid customer email" });
    }

    const orders = loadOrders();
    if (orders[externalOrderId] && orders[externalOrderId].productId !== productId) {
      return res.status(409).json({ ok: false, error: "Order product mismatch" });
    }
    if (orders[externalOrderId]?.status === "FULFILLED") {
      return res.json({ ok: true, duplicate: true, productId });
    }
    if (inFlightOrders.has(externalOrderId)) {
      return res.status(409).json({ ok: false, error: "Order fulfillment is already in progress" });
    }

    inFlightOrders.add(externalOrderId);
    try {
      const product = PRODUCTS[productId];
      let order = orders[externalOrderId];
      if (!order) {
        const recorded = soldCodes.recordSale({
          code: generateActivationCode(product.tier),
          tier: product.tier,
          email: customerEmail,
          stripeSessionId: externalOrderId,
          productKey: product.productKey,
          activationCodesRef,
        });
        order = {
          productId,
          tier: product.tier,
          code: recorded.code,
          status: "PENDING_EMAIL",
          createdAt: new Date().toISOString(),
        };
        orders[externalOrderId] = order;
        saveOrders(orders);
      }

      const emailSent = await sendActivationCode(customerEmail, order.code, product.tier, {
        productKey: product.productKey,
        productName: "SecureCall",
        productUrl: "https://stealthx.tech/download.html",
      });
      if (!emailSent) throw new Error("Activation email was not accepted by a delivery provider");
      soldCodes.updateEmailDelivery(externalOrderId, {
        status: "delivered",
        attempts: 1,
        lastAttemptAt: new Date().toISOString(),
        deliveredAt: new Date().toISOString(),
      });
      orders[externalOrderId] = {
        ...order,
        status: "FULFILLED",
        fulfilledAt: new Date().toISOString(),
        emailSent: true,
      };
      saveOrders(orders);
      return res.json({ ok: true, fulfilled: true, productId });
    } catch (error) {
      console.error("[VLABS-FULFILLMENT] Order failed:", soldCodes.maskStripeId(externalOrderId), error.message);
      return res.status(503).json({ ok: false, error: "Fulfillment failed" });
    } finally {
      inFlightOrders.delete(externalOrderId);
    }
  });

  app.post("/internal/vlabs/revoke", (req, res) => {
    const secret = process.env.VLABS_FULFILLMENT_SECRET;
    if (!secret) return res.status(503).json({ ok: false, error: "Fulfillment is not configured" });
    if (!authenticateRequest(req, secret)) {
      return res.status(401).json({ ok: false, error: "Invalid fulfillment signature" });
    }

    const { externalOrderId, productId, reason } = req.body || {};
    if (!validString(externalOrderId, 128) || !validString(productId, 100) || !isRevocationReason(reason)) {
      return res.status(400).json({ ok: false, error: "Invalid revocation request" });
    }

    const orders = loadOrders();
    const order = orders[externalOrderId];
    if (!order || order.productId !== productId) {
      return res.status(404).json({ ok: false, error: "Fulfilled order not found" });
    }
    if (order.status === "REVOKED") return res.json({ ok: true, duplicate: true, productId });

    const result = soldCodes.revokeByStripeSession(externalOrderId, activationCodesRef);
    if (!result.found) return res.status(409).json({ ok: false, error: "Activation code not found" });
    orders[externalOrderId] = { ...order, status: "REVOKED", revokedAt: new Date().toISOString(), revokeReason: reason };
    saveOrders(orders);
    return res.json({ ok: true, revoked: true, productId });
  });
}

module.exports = { setupVlabsFulfillmentRoute, verifySignature, isRevocationReason, PRODUCTS };
