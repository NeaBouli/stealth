const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");
const { generateActivationCode } = require("./stripe_handler");
const soldCodes = require("./sold_codes");
const { sendActivationCode } = require("./email_handler");

const MAX_CLOCK_SKEW_SECONDS = 300;
const RATE_LIMIT_WINDOW_MS = 5 * 60 * 1000;
const RATE_LIMIT_MAX_ATTEMPTS = 60;
const RATE_LIMIT_MAX_KEYS = 1024;
const PAYMENT_TOMBSTONES_KEY = "__paymentTombstones";

const PRODUCTS = Object.freeze({
  "stealthx-securecall-pro-lifetime": {
    tier: "pro", amount: 1500, currency: "eur", productKey: "vlabs_securecall_pro_lifetime", productName: "SecureCall", productUrl: "https://stealthx.tech/download.html"
  },
  "stealthx-securecall-premium-lifetime": {
    tier: "premium", amount: 2500, currency: "eur", productKey: "vlabs_securecall_premium_lifetime", productName: "SecureCall", productUrl: "https://stealthx.tech/download.html"
  },
  "stealthx-securechat-pro-lifetime": {
    tier: "pro", amount: 900, currency: "eur", productKey: "securechat_pro_lifetime", productName: "SecureChat", productUrl: "https://securechat.stealthx.tech/"
  },
  "stealthx-securechat-elite-lifetime": {
    tier: "elite", amount: 1900, currency: "eur", productKey: "securechat_elite_lifetime", productName: "SecureChat", productUrl: "https://securechat.stealthx.tech/"
  },
  "stealthx-chameleon-pro-lifetime": {
    tier: "pro", amount: 900, currency: "eur", productKey: "chameleon_pro_lifetime", productName: "Chameleon", productUrl: "https://chameleon.stealthx.tech/"
  },
  "stealthx-chameleon-elite-lifetime": {
    tier: "elite", amount: 1900, currency: "eur", productKey: "chameleon_elite_lifetime", productName: "Chameleon", productUrl: "https://chameleon.stealthx.tech/"
  },
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

function hashReference(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function paymentTombstones(orders) {
  const value = orders[PAYMENT_TOMBSTONES_KEY];
  return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}

function findOrderOwner(orders, field, value, exceptOrderId) {
  return Object.entries(orders).find(([orderId, order]) => (
    orderId !== PAYMENT_TOMBSTONES_KEY
    && orderId !== exceptOrderId
    && order
    && typeof order === "object"
    && order[field] === value
  ));
}

function acquireRegistryLock() {
  const lockFile = `${ordersFile()}.lock`;
  fs.mkdirSync(path.dirname(lockFile), { recursive: true, mode: 0o700 });
  try {
    const descriptor = fs.openSync(lockFile, "wx", 0o600);
    const ownerToken = crypto.randomBytes(16).toString("hex");
    fs.writeFileSync(descriptor, `${process.pid}:${ownerToken}\n`, "utf8");
    return { descriptor, lockFile, ownerToken };
  } catch (error) {
    // Never reclaim automatically. Recovery is allowed only with every writer stopped.
    if (error.code === "EEXIST") return null;
    throw error;
  }
}

function releaseRegistryLock(lock) {
  if (!lock) return;
  try {
    fs.closeSync(lock.descriptor);
  } finally {
    try {
      const owner = fs.readFileSync(lock.lockFile, "utf8").trim();
      if (owner.endsWith(`:${lock.ownerToken}`)) fs.unlinkSync(lock.lockFile);
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
  }
}

function assertRegistryLockOwned(lock) {
  try {
    const descriptorStat = fs.fstatSync(lock.descriptor);
    const pathStat = fs.statSync(lock.lockFile);
    const owner = fs.readFileSync(lock.lockFile, "utf8").trim();
    if (
      descriptorStat.dev === pathStat.dev
      && descriptorStat.ino === pathStat.ino
      && owner.endsWith(`:${lock.ownerToken}`)
    ) return;
  } catch {
    // Missing or unreadable ownership state is a lost lease.
  }
  throw new Error("Order registry lease lost");
}

function saveOrdersLocked(lock, orders) {
  assertRegistryLockOwned(lock);
  saveOrders(orders);
}

function validString(value, maxLength) {
  return typeof value === "string" && value.length > 0 && value.length <= maxLength;
}

function verifySignature(secret, timestamp, rawBody, receivedSignature) {
  if (typeof secret !== "string" || secret.length < 32) return false;
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

function createRequestRateLimiter(options = {}) {
  const maxAttempts = options.maxAttempts || RATE_LIMIT_MAX_ATTEMPTS;
  const windowMs = options.windowMs || RATE_LIMIT_WINDOW_MS;
  const maxKeys = options.maxKeys || RATE_LIMIT_MAX_KEYS;
  const now = options.now || Date.now;
  const attempts = new Map();

  return (req, route) => {
    const currentTime = now();
    const address = req.ip || req.socket?.remoteAddress || req.connection?.remoteAddress || "unknown";
    const key = `${route}:${address}`;
    let entry = attempts.get(key);
    if (!entry || currentTime - entry.windowStartedAt >= windowMs) {
      if (!entry && attempts.size >= maxKeys) {
        for (const [storedKey, storedEntry] of attempts) {
          if (currentTime - storedEntry.windowStartedAt >= windowMs) attempts.delete(storedKey);
        }
        if (attempts.size >= maxKeys) return false;
      }
      entry = { count: 0, windowStartedAt: currentTime };
      attempts.set(key, entry);
    }
    if (entry.count >= maxAttempts) return false;
    entry.count += 1;
    return true;
  };
}

function rejectRateLimited(res) {
  if (typeof res.set === "function") res.set("Retry-After", String(Math.ceil(RATE_LIMIT_WINDOW_MS / 1000)));
  return res.status(429).json({ ok: false, error: "rate_limited" });
}

function setupVlabsFulfillmentRoute(app, activationCodesRef, deps = {}) {
  const sendActivationCodeImpl = deps.sendActivationCode || sendActivationCode;
  const generateActivationCodeImpl = deps.generateActivationCode || generateActivationCode;
  const rateLimitRequest = deps.rateLimitRequest || createRequestRateLimiter();
  app.post("/internal/vlabs/fulfill", async (req, res) => {
    const secret = process.env.VLABS_FULFILLMENT_SECRET;
    if (!secret || secret.length < 32) return res.status(503).json({ ok: false, error: "Fulfillment is not configured" });
    if (!rateLimitRequest(req, "fulfill")) return rejectRateLimited(res);
    if (!authenticateRequest(req, secret)) {
      return res.status(401).json({ ok: false, error: "Invalid fulfillment signature" });
    }

    const {
      externalOrderId,
      productId,
      customerEmail,
      paymentProvider,
      paymentStatus,
      paymentEventId,
      paymentReference,
      amount,
      currency,
    } = req.body || {};
    if (!validString(externalOrderId, 128) || !/^cs_[a-zA-Z0-9_]+$/.test(externalOrderId)) {
      return res.status(400).json({ ok: false, error: "Invalid external order ID" });
    }
    if (!validString(productId, 100) || !PRODUCTS[productId]) {
      return res.status(409).json({ ok: false, error: "Product activation is not enabled" });
    }
    if (!validString(customerEmail, 254) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customerEmail)) {
      return res.status(400).json({ ok: false, error: "Invalid customer email" });
    }
    const product = PRODUCTS[productId];
    if (
      paymentProvider !== "stripe"
      || paymentStatus !== "paid"
      || !validString(paymentEventId, 128)
      || !/^evt_[a-zA-Z0-9_]+$/.test(paymentEventId)
      || !validString(paymentReference, 128)
      || !/^pi_[a-zA-Z0-9_]+$/.test(paymentReference)
      || !Number.isSafeInteger(amount)
      || amount !== product.amount
      || String(currency || "").toLowerCase() !== product.currency
    ) {
      return res.status(409).json({ ok: false, error: "Paid order proof mismatch" });
    }

    const lock = acquireRegistryLock();
    if (!lock) return res.status(409).json({ ok: false, error: "Order registry is busy" });

    try {
      const orders = loadOrders();
      const paymentEventHash = hashReference(paymentEventId);
      const paymentReferenceHash = hashReference(paymentReference);
      const tombstone = paymentTombstones(orders)[paymentReferenceHash];
      if (tombstone) {
        return res.status(409).json({ ok: false, error: "Payment was reversed" });
      }
      if (findOrderOwner(orders, "paymentReferenceHash", paymentReferenceHash, externalOrderId)) {
        return res.status(409).json({ ok: false, error: "Payment is already bound to another order" });
      }
      if (findOrderOwner(orders, "paymentEventHash", paymentEventHash, externalOrderId)) {
        return res.status(409).json({ ok: false, error: "Payment event is already bound to another order" });
      }
      if (orders[externalOrderId] && orders[externalOrderId].productId !== productId) {
        return res.status(409).json({ ok: false, error: "Order product mismatch" });
      }
      if (
        orders[externalOrderId]?.paymentReferenceHash
        && orders[externalOrderId].paymentReferenceHash !== paymentReferenceHash
      ) {
        return res.status(409).json({ ok: false, error: "Order payment mismatch" });
      }
      if (
        orders[externalOrderId]?.paymentEventHash
        && orders[externalOrderId].paymentEventHash !== paymentEventHash
      ) {
        return res.status(409).json({ ok: false, error: "Order payment event mismatch" });
      }
      if (orders[externalOrderId]?.status === "FULFILLED") {
        return res.json({ ok: true, duplicate: true, productId });
      }
      if (orders[externalOrderId]?.status === "REVOKED") {
        return res.status(409).json({ ok: false, error: "Order has been revoked" });
      }

      let order = orders[externalOrderId];
      if (!order) {
        const recorded = soldCodes.recordSale({
          code: generateActivationCodeImpl(product.tier),
          tier: product.tier,
          stripeSessionId: externalOrderId,
          productKey: product.productKey,
          activationCodesRef,
        });
        order = {
          productId,
          tier: product.tier,
          code: recorded.code,
          paymentProvider,
          paymentStatus,
          paymentEventHash,
          paymentReferenceHash,
          amount,
          currency: product.currency,
          status: "PENDING_EMAIL",
          createdAt: new Date().toISOString(),
        };
        orders[externalOrderId] = order;
        saveOrdersLocked(lock, orders);
      } else if (!order.paymentReferenceHash) {
        order = {
          ...order,
          paymentProvider,
          paymentStatus,
          paymentEventHash,
          paymentReferenceHash,
          amount,
          currency: product.currency,
        };
        orders[externalOrderId] = order;
        saveOrdersLocked(lock, orders);
      }

      const emailSent = await sendActivationCodeImpl(customerEmail, order.code, product.tier, {
        productKey: product.productKey,
        productName: product.productName,
        productUrl: product.productUrl,
      });
      if (!emailSent) throw new Error("Activation email was not accepted by a delivery provider");
      assertRegistryLockOwned(lock);
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
      saveOrdersLocked(lock, orders);
      return res.json({ ok: true, fulfilled: true, productId });
    } catch (error) {
      console.error("[VLABS-FULFILLMENT] Order failed:", soldCodes.maskStripeId(externalOrderId), error.message);
      return res.status(503).json({ ok: false, error: "Fulfillment failed" });
    } finally {
      releaseRegistryLock(lock);
    }
  });

  app.post("/internal/vlabs/revoke", (req, res) => {
    const secret = process.env.VLABS_FULFILLMENT_SECRET;
    if (!secret || secret.length < 32) return res.status(503).json({ ok: false, error: "Fulfillment is not configured" });
    if (!rateLimitRequest(req, "revoke")) return rejectRateLimited(res);
    if (!authenticateRequest(req, secret)) {
      return res.status(401).json({ ok: false, error: "Invalid fulfillment signature" });
    }

    const { externalOrderId, productId, reason, paymentProvider, adjustmentEventId, paymentReference } = req.body || {};
    if (
      (externalOrderId !== undefined && (!validString(externalOrderId, 128) || !/^cs_[a-zA-Z0-9_]+$/.test(externalOrderId)))
      || !validString(productId, 100)
      || !PRODUCTS[productId]
      || !isRevocationReason(reason)
      || paymentProvider !== "stripe"
      || !validString(adjustmentEventId, 128)
      || !/^evt_[a-zA-Z0-9_]+$/.test(adjustmentEventId)
      || !validString(paymentReference, 128)
      || !/^pi_[a-zA-Z0-9_]+$/.test(paymentReference)
    ) {
      return res.status(400).json({ ok: false, error: "Invalid revocation request" });
    }

    const lock = acquireRegistryLock();
    if (!lock) return res.status(409).json({ ok: false, error: "Order registry is busy" });
    try {
      const orders = loadOrders();
      const paymentReferenceHash = hashReference(paymentReference);
      const tombstones = paymentTombstones(orders);
      const existingTombstone = tombstones[paymentReferenceHash];
      if (existingTombstone && existingTombstone.productId !== productId) {
        return res.status(409).json({ ok: false, error: "Reversal product mismatch" });
      }
      let resolvedOrderId = externalOrderId;
      let order = resolvedOrderId ? orders[resolvedOrderId] : undefined;
      if (!order) {
        const paymentOwner = findOrderOwner(orders, "paymentReferenceHash", paymentReferenceHash);
        if (paymentOwner) {
          [resolvedOrderId, order] = paymentOwner;
        }
      }
      if (order && order.productId !== productId) {
        return res.status(409).json({ ok: false, error: "Order product mismatch" });
      }
      if (!order) {
        if (existingTombstone) return res.json({ ok: true, duplicate: true, productId });
        tombstones[paymentReferenceHash] = {
          productId,
          reason,
          adjustmentEventHash: hashReference(adjustmentEventId),
          reversedAt: new Date().toISOString(),
        };
        orders[PAYMENT_TOMBSTONES_KEY] = tombstones;
        saveOrdersLocked(lock, orders);
        return res.json({ ok: true, revoked: true, productId });
      }
      if (
        !order.paymentReferenceHash
        || order.paymentReferenceHash !== paymentReferenceHash
      ) {
        return res.status(409).json({ ok: false, error: "Order payment mismatch" });
      }
      if (order.status === "REVOKED" || existingTombstone) {
        return res.json({ ok: true, duplicate: true, productId });
      }

      assertRegistryLockOwned(lock);
      const result = soldCodes.revokeByStripeSession(resolvedOrderId, activationCodesRef);
      if (!result.found) return res.status(409).json({ ok: false, error: "Activation code not found" });
      orders[resolvedOrderId] = {
        ...order,
        status: "REVOKED",
        revokedAt: new Date().toISOString(),
        revokeReason: reason,
        adjustmentEventHash: hashReference(adjustmentEventId),
      };
      tombstones[paymentReferenceHash] = {
        productId,
        reason,
        adjustmentEventHash: hashReference(adjustmentEventId),
        reversedAt: orders[resolvedOrderId].revokedAt,
      };
      orders[PAYMENT_TOMBSTONES_KEY] = tombstones;
      saveOrdersLocked(lock, orders);
      return res.json({ ok: true, revoked: true, productId });
    } finally {
      releaseRegistryLock(lock);
    }
  });
}

module.exports = {
  setupVlabsFulfillmentRoute,
  verifySignature,
  isRevocationReason,
  createRequestRateLimiter,
  hashReference,
  PRODUCTS,
};
