const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");
const { generateActivationCode } = require("./stripe_handler");
const soldCodes = require("./sold_codes");
const { sendActivationCode } = require("./email_handler");

const MAX_CLOCK_SKEW_SECONDS = 300;
const MIN_FULFILLMENT_SECRET_LENGTH = 32;
const CATALOG_VERSION = "stealthx-lifetime-v1";
const inFlightOrders = new Set();

// Static immutable sales contract for the private VLABS receiver.
// Parsed by the VLABS AST checker: keep this a plain frozen literal with
// inline values only (no computed keys, no identifier references).
const PRODUCTS = Object.freeze({
  "stealthx-securecall-pro-lifetime": {
    tier: "pro",
    productKey: "vlabs_securecall_pro_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "securecall-pro-eur-1500-lifetime-v1",
    releaseId: "securecall-android-1.0.50-vc78017-api36",
    amount: 1500,
    currency: "eur",
    name: "SecureCall",
    url: "https://stealthx.tech/download.html",
  },
  "stealthx-securecall-premium-lifetime": {
    tier: "premium",
    productKey: "vlabs_securecall_premium_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "securecall-premium-eur-2500-lifetime-v1",
    releaseId: "securecall-android-1.0.50-vc78017-api36",
    amount: 2500,
    currency: "eur",
    name: "SecureCall",
    url: "https://stealthx.tech/download.html",
  },
  "stealthx-securechat-pro-lifetime": {
    tier: "pro",
    productKey: "securechat_pro_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "securechat-pro-eur-900-lifetime-v1",
    releaseId: "securechat-android-0.1.11-alpha-vc15-api36",
    amount: 900,
    currency: "eur",
    name: "SecureChat",
    url: "https://securechat.stealthx.tech/",
  },
  "stealthx-securechat-elite-lifetime": {
    tier: "elite",
    productKey: "securechat_elite_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "securechat-elite-eur-1900-lifetime-v1",
    releaseId: "securechat-android-0.1.11-alpha-vc15-api36",
    amount: 1900,
    currency: "eur",
    name: "SecureChat",
    url: "https://securechat.stealthx.tech/",
  },
  "stealthx-chameleon-pro-lifetime": {
    tier: "pro",
    productKey: "chameleon_pro_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "chameleon-pro-eur-900-lifetime-v1",
    releaseId: "chameleon-android-0.1.13-alpha-vc14-api36",
    amount: 900,
    currency: "eur",
    name: "Chameleon",
    url: "https://chameleon.stealthx.tech/",
  },
  "stealthx-chameleon-elite-lifetime": {
    tier: "elite",
    productKey: "chameleon_elite_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "chameleon-elite-eur-1900-lifetime-v1",
    releaseId: "chameleon-android-0.1.13-alpha-vc14-api36",
    amount: 1900,
    currency: "eur",
    name: "Chameleon",
    url: "https://chameleon.stealthx.tech/",
  },
});
Object.values(PRODUCTS).forEach(Object.freeze);
const REVOCATION_REASONS = new Set(["stripe_full_refund", "stripe_dispute"]);

function ordersFile() {
  return process.env.VLABS_FULFILLMENT_ORDERS_FILE
    || path.join(__dirname, "..", "..", "data", "vlabs_fulfillment_orders.json");
}

function ordersLockFile() {
  return `${ordersFile()}.lock`;
}

// Minimal ownership-checked lock so concurrent local processes cannot
// interleave read-modify-write cycles on the order registry.
function acquireOrdersLock() {
  fs.mkdirSync(path.dirname(ordersLockFile()), { recursive: true, mode: 0o700 });
  try {
    const descriptor = fs.openSync(ordersLockFile(), "wx", 0o600);
    const ownerToken = crypto.randomBytes(16).toString("hex");
    fs.writeFileSync(descriptor, `${process.pid}:${ownerToken}\n`, "utf8");
    return { descriptor, ownerToken };
  } catch (error) {
    // Never reclaim automatically. Recovery is allowed only with every writer stopped.
    if (error.code === "EEXIST") throw new Error("vlabs_fulfillment_store_busy");
    throw error;
  }
}

function assertOrdersLockOwned(lock) {
  try {
    const descriptorStat = fs.fstatSync(lock.descriptor);
    const pathStat = fs.statSync(ordersLockFile());
    const owner = fs.readFileSync(ordersLockFile(), "utf8").trim();
    if (
      descriptorStat.dev === pathStat.dev
      && descriptorStat.ino === pathStat.ino
      && owner.endsWith(`:${lock.ownerToken}`)
    ) return;
  } catch {
    // Missing, replaced or unreadable ownership state is a lost lock.
  }
  throw new Error("vlabs_fulfillment_store_lock_lost");
}

function releaseOrdersLock(lock) {
  try {
    fs.closeSync(lock.descriptor);
  } catch (error) {
    console.error("[VLABS-FULFILLMENT] Failed to close store lock:", error.message);
  }
  try {
    const owner = fs.readFileSync(ordersLockFile(), "utf8").trim();
    if (owner.endsWith(`:${lock.ownerToken}`)) fs.unlinkSync(ordersLockFile());
  } catch (error) {
    if (error.code !== "ENOENT") console.error("[VLABS-FULFILLMENT] Failed to release store lock:", error.message);
  }
}

function withOrdersLock(operation) {
  const lock = acquireOrdersLock();
  try {
    return operation(lock);
  } finally {
    releaseOrdersLock(lock);
  }
}

function loadOrders() {
  const file = ordersFile();
  if (!fs.existsSync(file)) return {};
  const parsed = JSON.parse(fs.readFileSync(file, "utf8"));
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("vlabs_fulfillment_store_invalid");
  }
  return parsed;
}

function saveOrders(orders) {
  writeJsonAtomic(ordersFile(), orders);
}

function httpError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}

function validString(value, maxLength) {
  return typeof value === "string" && value.length > 0 && value.length <= maxLength;
}

function findProduct(productId) {
  return Object.prototype.hasOwnProperty.call(PRODUCTS, productId) ? PRODUCTS[productId] : null;
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

function requireExactClaim(value, expected, label) {
  if (!validString(value, 160)) throw httpError(400, `Missing or invalid ${label}`);
  if (value !== expected) throw httpError(409, `Contract drift: ${label} mismatch`);
}

function validateFulfillmentContract(body) {
  const { externalOrderId, productId, catalogVersion, offerVersion, releaseId,
    paymentReference, paymentEventId, paymentStatus, amount, currency, customerEmail } = body || {};
  if (!validString(externalOrderId, 128) || !/^cs_[a-zA-Z0-9_]+$/.test(externalOrderId)) {
    throw httpError(400, "Invalid external order ID");
  }
  if (!validString(productId, 100)) throw httpError(400, "Missing or invalid productId");
  const product = findProduct(productId);
  if (!product) throw httpError(409, "Product activation is not enabled");
  requireExactClaim(catalogVersion, CATALOG_VERSION, "catalogVersion");
  requireExactClaim(offerVersion, product.offerVersion, "offerVersion");
  requireExactClaim(releaseId, product.releaseId, "releaseId");
  if (!validString(paymentReference, 128) || !/^pi_[a-zA-Z0-9_]+$/.test(paymentReference)) {
    throw httpError(400, "Missing or invalid Stripe paid proof");
  }
  if (!validString(paymentEventId, 128) || !/^evt_[a-zA-Z0-9_]+$/.test(paymentEventId)) {
    throw httpError(400, "Missing or invalid Stripe payment event");
  }
  if (paymentStatus !== "paid") throw httpError(409, "Stripe payment is not proven paid");
  if (!Number.isInteger(amount)) throw httpError(400, "Missing or invalid amount");
  if (amount !== product.amount) throw httpError(409, "Contract drift: amount mismatch");
  if (!validString(currency, 8)) throw httpError(400, "Missing or invalid currency");
  if (currency.toLowerCase() !== product.currency) throw httpError(409, "Contract drift: currency mismatch");
  if (!validString(customerEmail, 254) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customerEmail)) {
    throw httpError(400, "Invalid customer email");
  }
  return { product };
}

function validateRevocationContract(body) {
  const { externalOrderId, productId, catalogVersion, offerVersion, releaseId,
    paymentReference, adjustmentEventId, reason } = body || {};
  if (externalOrderId !== undefined
    && (!validString(externalOrderId, 128) || !/^cs_[a-zA-Z0-9_]+$/.test(externalOrderId))) {
    throw httpError(400, "Invalid external order ID");
  }
  if (!validString(productId, 100)) throw httpError(400, "Missing or invalid productId");
  const product = findProduct(productId);
  if (!product) throw httpError(409, "Product activation is not enabled");
  requireExactClaim(catalogVersion, CATALOG_VERSION, "catalogVersion");
  requireExactClaim(offerVersion, product.offerVersion, "offerVersion");
  requireExactClaim(releaseId, product.releaseId, "releaseId");
  if (!validString(paymentReference, 128) || !/^pi_[a-zA-Z0-9_]+$/.test(paymentReference)) {
    throw httpError(400, "Missing or invalid Stripe payment reference");
  }
  if (!validString(adjustmentEventId, 128) || !/^evt_[a-zA-Z0-9_]+$/.test(adjustmentEventId)) {
    throw httpError(400, "Missing or invalid Stripe adjustment event");
  }
  if (!isRevocationReason(reason)) throw httpError(400, "Revocation requires a full refund or dispute");
  return { product };
}

function fulfillmentTupleDrift(order, body) {
  return order.productId !== body.productId
    || (order.catalogVersion || null) !== (body.catalogVersion || null)
    || (order.offerVersion || null) !== (body.offerVersion || null)
    || (order.releaseId || null) !== (body.releaseId || null)
    || (order.paymentReference || null) !== (body.paymentReference || null)
    || (Number.isInteger(order.amount) ? order.amount : null) !== (Number.isInteger(body.amount) ? body.amount : null)
    || (order.currency || null) !== (typeof body.currency === "string" ? body.currency.toLowerCase() : null);
}

function isLegacyOrder(order) {
  return !order.catalogVersion && !order.offerVersion && !order.releaseId;
}

function bindLegacyOrder(order, body, product) {
  return {
    ...order,
    catalogVersion: CATALOG_VERSION,
    offerVersion: product.offerVersion,
    releaseId: product.releaseId,
    amount: product.amount,
    currency: product.currency,
    paymentReference: body.paymentReference,
    paymentEventId: body.paymentEventId || order.paymentEventId,
    migratedAt: new Date().toISOString(),
  };
}

function revocationTupleDrift(order, body) {
  return order.productId !== body.productId
    || (order.catalogVersion || null) !== (body.catalogVersion || null)
    || (order.offerVersion || null) !== (body.offerVersion || null)
    || (order.releaseId || null) !== (body.releaseId || null)
    || (order.paymentReference || null) !== (body.paymentReference || null);
}

function configuredSecret() {
  const secret = process.env.VLABS_FULFILLMENT_SECRET;
  return typeof secret === "string" && secret.length >= MIN_FULFILLMENT_SECRET_LENGTH ? secret : null;
}

function setupVlabsFulfillmentRoute(app, activationCodesRef) {
  app.post("/internal/vlabs/fulfill", async (req, res) => {
    const secret = configuredSecret();
    if (!secret) return res.status(503).json({ ok: false, error: "Fulfillment is not configured" });
    if (!authenticateRequest(req, secret)) {
      return res.status(401).json({ ok: false, error: "Invalid fulfillment signature" });
    }

    let product;
    try {
      ({ product } = validateFulfillmentContract(req.body));
    } catch (error) {
      return res.status(error.status || 400).json({ ok: false, error: error.message });
    }
    const { externalOrderId, productId, customerEmail } = req.body;

    if (inFlightOrders.has(externalOrderId)) {
      return res.status(409).json({ ok: false, error: "Order fulfillment is already in progress" });
    }

    inFlightOrders.add(externalOrderId);
    try {
      let order;
      let duplicate = false;
      try {
        withOrdersLock(lock => {
          assertOrdersLockOwned(lock);
          const orders = loadOrders();
          let existing = orders[externalOrderId];
          if (existing) {
            if (isLegacyOrder(existing) && existing.productId === productId) {
              existing = bindLegacyOrder(existing, req.body, product);
              orders[externalOrderId] = existing;
              saveOrders(orders);
            }
            if (existing.status === "TOMBSTONED" || existing.status === "REVOKED") {
              throw httpError(409, "Order was revoked before fulfillment");
            }
            if (fulfillmentTupleDrift(existing, req.body)) {
              throw httpError(409, "Order contract mismatch");
            }
            if (existing.status === "FULFILLED") {
              duplicate = true;
              order = existing;
              return;
            }
            order = existing;
            return;
          }

          let recorded;
          try {
            // Buyer email stays transient: it is never persisted, only used for delivery.
            recorded = soldCodes.recordSale({
              code: generateActivationCode(product.tier),
              tier: product.tier,
              stripeSessionId: externalOrderId,
              paymentReference: req.body.paymentReference,
              productKey: product.productKey,
              catalogVersion: CATALOG_VERSION,
              offerVersion: product.offerVersion,
              releaseId: product.releaseId,
              externalProductId: productId,
              activationCodesRef,
            });
          } catch (saleError) {
            if (saleError && saleError.message === "payment_reversed") {
              orders[externalOrderId] = {
                productId,
                tier: product.tier,
                catalogVersion: CATALOG_VERSION,
                offerVersion: product.offerVersion,
                releaseId: product.releaseId,
                amount: product.amount,
                currency: product.currency,
                paymentReference: req.body.paymentReference,
                paymentEventId: req.body.paymentEventId,
                status: "TOMBSTONED",
                createdAt: new Date().toISOString(),
                tombstonedAt: new Date().toISOString(),
                revokeReason: "reversal_precedes_fulfillment",
              };
              saveOrders(orders);
              throw httpError(409, "Order was revoked before fulfillment");
            }
            if (saleError && saleError.message === "sale_binding_mismatch") {
              throw httpError(409, "Order contract mismatch");
            }
            throw saleError;
          }
          order = {
            productId,
            tier: product.tier,
            catalogVersion: CATALOG_VERSION,
            offerVersion: product.offerVersion,
            releaseId: product.releaseId,
            amount: product.amount,
            currency: product.currency,
            paymentReference: req.body.paymentReference,
            paymentEventId: req.body.paymentEventId,
            code: recorded.code,
            status: "PENDING_EMAIL",
            createdAt: new Date().toISOString(),
          };
          orders[externalOrderId] = order;
          saveOrders(orders);
        });
      } catch (error) {
        if (error && error.status) {
          return res.status(error.status).json({ ok: false, error: error.message });
        }
        throw error;
      }

      if (duplicate) return res.json({ ok: true, duplicate: true, productId });

      const emailSent = await sendActivationCode(customerEmail, order.code, product.tier, {
        productKey: product.productKey,
        productName: product.name,
        productUrl: product.url,
      });
      if (!emailSent) throw new Error("Activation email was not accepted by a delivery provider");

      let revokedBeforeFinalized = false;
      withOrdersLock(lock => {
        assertOrdersLockOwned(lock);
        const orders = loadOrders();
        const current = orders[externalOrderId];
        if (!current || current.status === "REVOKED" || current.status === "TOMBSTONED") {
          revokedBeforeFinalized = true;
          return;
        }
        soldCodes.updateEmailDelivery(externalOrderId, {
          status: "delivered",
          attempts: 1,
          lastAttemptAt: new Date().toISOString(),
          deliveredAt: new Date().toISOString(),
        });
        orders[externalOrderId] = {
          ...current,
          status: "FULFILLED",
          fulfilledAt: new Date().toISOString(),
          emailSent: true,
        };
        saveOrders(orders);
      });
      if (revokedBeforeFinalized) {
        return res.status(409).json({ ok: false, error: "Order was revoked before fulfillment" });
      }
      return res.json({ ok: true, fulfilled: true, productId });
    } catch (error) {
      console.error("[VLABS-FULFILLMENT] Order failed:", soldCodes.maskStripeId(externalOrderId), error.message);
      return res.status(503).json({ ok: false, error: "Fulfillment failed" });
    } finally {
      inFlightOrders.delete(externalOrderId);
    }
  });

  app.post("/internal/vlabs/revoke", (req, res) => {
    const secret = configuredSecret();
    if (!secret) return res.status(503).json({ ok: false, error: "Fulfillment is not configured" });
    if (!authenticateRequest(req, secret)) {
      return res.status(401).json({ ok: false, error: "Invalid fulfillment signature" });
    }

    let product;
    try {
      ({ product } = validateRevocationContract(req.body));
    } catch (error) {
      return res.status(error.status || 400).json({ ok: false, error: error.message });
    }
    const { externalOrderId, productId, reason, paymentReference, adjustmentEventId } = req.body;
    const orderKey = externalOrderId
      || `payment_${crypto.createHash("sha256").update(paymentReference).digest("hex").slice(0, 32)}`;

    try {
      const outcome = withOrdersLock(lock => {
        assertOrdersLockOwned(lock);
        const orders = loadOrders();
        let existing = orders[orderKey];
        if (existing && isLegacyOrder(existing) && existing.productId === productId) {
          existing = bindLegacyOrder(existing, req.body, product);
          orders[orderKey] = existing;
          saveOrders(orders);
        }
        if (existing && revocationTupleDrift(existing, req.body)) {
          throw httpError(409, "Revocation contract mismatch");
        }
        if (existing && (existing.status === "REVOKED" || existing.status === "TOMBSTONED")) {
          return { duplicate: true };
        }

        const result = soldCodes.revokeByStripeSession(externalOrderId || null, activationCodesRef, {
          paymentIntent: paymentReference,
          productKey: product.productKey,
          eventId: adjustmentEventId,
          reason,
          catalogVersion: CATALOG_VERSION,
          offerVersion: product.offerVersion,
          releaseId: product.releaseId,
        });

        if (!existing) {
          // Revoke-before-fulfill: tombstone so a later fulfillment fails closed.
          orders[orderKey] = {
            productId,
            tier: product.tier,
            catalogVersion: CATALOG_VERSION,
            offerVersion: product.offerVersion,
            releaseId: product.releaseId,
            amount: product.amount,
            currency: product.currency,
            paymentReference,
            status: "TOMBSTONED",
            createdAt: new Date().toISOString(),
            tombstonedAt: new Date().toISOString(),
            revokeReason: reason,
            adjustmentEventId,
            soldCodeTombstoned: result.tombstoned === true,
          };
          saveOrders(orders);
          return { tombstoned: true };
        }

        orders[orderKey] = {
          ...existing,
          status: "REVOKED",
          revokedAt: new Date().toISOString(),
          revokeReason: reason,
          adjustmentEventId,
        };
        saveOrders(orders);
        return { revoked: true };
      });

      if (outcome.duplicate) return res.json({ ok: true, duplicate: true, productId });
      if (outcome.tombstoned) return res.json({ ok: true, tombstoned: true, productId });
      return res.json({ ok: true, revoked: true, productId });
    } catch (error) {
      if (error && error.status) {
        return res.status(error.status).json({ ok: false, error: error.message });
      }
      console.error("[VLABS-FULFILLMENT] Revocation failed:", soldCodes.maskStripeId(externalOrderId || paymentReference), error.message);
      return res.status(503).json({ ok: false, error: "Revocation failed" });
    }
  });
}

module.exports = {
  setupVlabsFulfillmentRoute,
  verifySignature,
  isRevocationReason,
  validateFulfillmentContract,
  validateRevocationContract,
  PRODUCTS,
  CATALOG_VERSION,
  MIN_FULFILLMENT_SECRET_LENGTH,
};
