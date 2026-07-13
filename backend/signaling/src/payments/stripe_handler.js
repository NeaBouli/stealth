/**
 * Stripe Checkout Handler for SecureCall
 *
 * ═══════════════════════════════════════════════════════════════
 * SETUP (Kaspartizan muss dies in Stripe Dashboard durchführen):
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. Gehe zu: https://dashboard.stripe.com/test/products (Sandbox)
 *    oder https://dashboard.stripe.com/products (Live)
 *
 * 2. Erstelle 3 Produkte:
 *
 *    Produkt 1: "SecureCall Pro"
 *    → Preis: €3.49 / Monat (recurring)
 *    → Notiere: price_xxxxxxxxxxxxxxxxx  → price_1TInrMBcyoLtm3FAWcdsNUX3
 *
 *    Produkt 2: "SecureCall Premium"
 *    → Preis: €4.99 / Monat (recurring)
 *    → Notiere: price_xxxxxxxxxxxxxxxxx  → price_1TInt2BcyoLtm3FAPOw0vJ5p
 *
 *    Produkt 3: "SecureCall Premium Lifetime"
 *    → Preis: €49.00 einmalig (one_time)
 *    → Notiere: price_xxxxxxxxxxxxxxxxx  → price_1TInt8BcyoLtm3FACbPJZZPg
 *
 * 3. Webhook erstellen:
 *    → URL: https://protective-healing-production.up.railway.app/stripe/webhook
 *    → Events: checkout.session.completed
 *    → Notiere: whsec_xxxxxxxx  → STRIPE_WEBHOOK_SECRET
 *
 * 4. Environment Variables in Railway setzen:
 *    STRIPE_SECRET_KEY=sk_test_... (oder sk_live_...)
 *    STRIPE_WEBHOOK_SECRET=whsec_...
 *    (Price IDs are hardcoded below — no env vars needed)
 *
 * ═══════════════════════════════════════════════════════════════
 *
 * Flow:
 *   1. User clicks "Buy" on stealthx.tech
 *   2. POST /stripe/create-checkout → Stripe Checkout Session
 *   3. User pays via card/SEPA/Klarna
 *   4. Stripe fires webhook → generate activation code
 *   5. Redirect to success page with code displayed
 *
 * PAYMENT LINKS (Stripe-hosted, no backend needed):
 *   Premium Lifetime €49: https://buy.stripe.com/test_28E3cu3Sf545baKgeL6g800
 *   Pro €3.49/mo:         https://buy.stripe.com/test_00w4gyewTaop1AabYv6g801
 *   Premium €4.99/mo:     https://buy.stripe.com/test_5kQ3cu88vdABgv44w36g802
 *   (These work independently of the API routes below — used on landing page)
 */

// Price IDs: LIVE (acct_1QJAg3BtrTFeYCjz) + TEST fallback (acct_1TInNU...)
// Both are accepted by resolveTierFromPriceId() so either account's events map correctly.
const PRODUCTS = {
  pro_monthly: {
    name: "SecureCall Pro",
    priceId: "price_1TKwynBtrTFeYCjzOcWAenMz",        // LIVE
    priceIdTest: "price_1TInrMBcyoLtm3FAWcdsNUX3",    // TEST
    mode: "subscription",
    tier: "pro"
  },
  premium_monthly: {
    name: "SecureCall Premium",
    priceId: "price_1TKwyoBtrTFeYCjzAPvvfr3j",        // LIVE
    priceIdTest: "price_1TInt2BcyoLtm3FAPOw0vJ5p",    // TEST
    mode: "subscription",
    tier: "premium"
  },
  premium_lifetime: {
    name: "SecureCall Premium Lifetime",
    priceId: "price_1TKwypBtrTFeYCjzME5rbsnv",        // LIVE
    priceIdTest: "price_1TInt8BcyoLtm3FACbPJZZPg",    // TEST
    mode: "payment",
    tier: "premium"
  }
};

/**
 * Create a Stripe Checkout session.
 * @param {object} stripe - initialized Stripe instance
 * @param {string} productKey - one of: pro_monthly, premium_monthly, premium_lifetime
 * @param {string} customerEmail - optional
 */
async function createCheckoutSession(stripe, productKey, customerEmail) {
  const product = PRODUCTS[productKey];
  if (!product) throw new Error(`Unknown product: ${productKey}`);

  const priceId = product.priceId;
  if (!priceId) throw new Error(`Missing priceId for: ${productKey}`);

  const sessionParams = {
    line_items: [{ price: priceId, quantity: 1 }],
    mode: product.mode,
    success_url: "https://stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancel_url: "https://stealthx.tech/#pricing",
    metadata: { tier: product.tier, product: productKey }
  };

  if (customerEmail) sessionParams.customer_email = customerEmail;

  // SEPA + Klarna for EU customers (card is always included)
  if (product.mode === "payment") {
    sessionParams.payment_method_types = ["card", "klarna", "link"];
  }

  const session = await stripe.checkout.sessions.create(sessionParams);
  return session;
}

/**
 * Resolve tier + productKey from Stripe session line_items.
 * Payment Links don't include metadata, so we derive it from the priceId.
 * Returns { tier, productKey } or null if unknown.
 */
function resolveTierFromPriceId(priceId) {
  if (!priceId) return null;
  for (const [key, p] of Object.entries(PRODUCTS)) {
    if (p.priceId === priceId || p.priceIdTest === priceId) {
      return { tier: p.tier, productKey: key };
    }
  }
  // Custom ID purchases — use special productKey, no activation code
  const customIdPrices = {
    // LIVE (acct_1QJAg3...) — EUR prices
    "price_1TLU2wBtrTFeYCjzHdkjKxHQ": "custom_id_standard",  // €1 (10+ chars)
    "price_1TLU34BtrTFeYCjzt86MqEZq": "custom_id_short",     // €2 (5-9 chars)
    "price_1TLU35BtrTFeYCjzXs6Z3QyP": "custom_id_ultra",     // €5 (3-4 chars)
    // OLD USD prices (deactivated, kept for webhook backward compat)
    "price_1TKxNkBtrTFeYCjzl0M45HMg": "custom_id_standard",
    "price_1TKxNmBtrTFeYCjz4goI2OUM": "custom_id_short",
    "price_1TKxNoBtrTFeYCjzsOBMR9VM": "custom_id_ultra",
    // TEST (acct_1TInNU... Sandbox)
    "price_1TJITIBcyoLtm3FA0qZyTL5O": "custom_id_standard",
    "price_1TJITKBcyoLtm3FARalsHHII": "custom_id_short",
    "price_1TJITNBcyoLtm3FAlvw1HlRY": "custom_id_ultra"
  };
  if (customIdPrices[priceId]) {
    return { tier: "custom_id", productKey: customIdPrices[priceId] };
  }
  return null;
}

/**
 * Handle Stripe webhook events.
 * On checkout.session.completed → generate activation code + send email.
 *
 * @param {Object} event   Stripe webhook event
 * @param {Object} stripe  Stripe SDK instance (needed to fetch line_items)
 * @param {Array}  activationCodesRef  Live reference to server.js activationCodes array
 */
// Fix HIGH-003 (2026-04-16): Stripe webhook idempotency.
// Stripe retries webhooks on any non-2xx response or timeout, so a single
// checkout can fire the same event.id multiple times. Without this guard,
// every retry minted a new activation code + recorded a duplicate sale.
//
// We persist processed event ids to data/stripe_processed_events.json (now
// backed by the Railway volume after CRIT-004). Entries older than 14 days
// are pruned on load — Stripe's retry window is ≤72h so 14d is a generous
// safety margin.
const fs = require("fs");
const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");
const PROCESSED_FILE = process.env.STRIPE_PROCESSED_FILE ||
  path.join(__dirname, "..", "..", "data", "stripe_processed_events.json");
const PROCESSED_TTL_MS = 14 * 24 * 60 * 60 * 1000;
const processedEvents = new Map(); // eventId -> processedAtMs

function loadProcessedEvents() {
  try {
    if (!fs.existsSync(PROCESSED_FILE)) return;
    const raw = JSON.parse(fs.readFileSync(PROCESSED_FILE, "utf8"));
    const now = Date.now();
    let pruned = 0;
    for (const [id, ts] of Object.entries(raw || {})) {
      if (typeof ts === "number" && now - ts < PROCESSED_TTL_MS) {
        processedEvents.set(id, ts);
      } else {
        pruned++;
      }
    }
    console.log(`[STRIPE] Loaded ${processedEvents.size} processed event ids (pruned ${pruned} stale)`);
  } catch (e) {
    console.warn("[STRIPE] Could not load processed events:", e.message);
  }
}

function saveProcessedEvents() {
  try {
    const obj = {};
    for (const [id, ts] of processedEvents) obj[id] = ts;
    writeJsonAtomic(PROCESSED_FILE, obj);
  } catch (e) {
    console.error("[STRIPE] Failed to persist processed events:", e.message);
  }
}

loadProcessedEvents();

function isPaidCheckoutEvent(event) {
  if (!event || !["checkout.session.completed", "checkout.session.async_payment_succeeded"].includes(event.type)) {
    return false;
  }
  const session = event.data?.object;
  return Boolean(session && typeof session.id === "string" && session.id.startsWith("cs_") && session.payment_status === "paid");
}

function maskStripeId(value) {
  return typeof value === "string" && value.length >= 8 ? `${value.slice(0, 8)}...` : "***";
}

function resolveProductEmailOptions(productKey) {
  if (productKey?.startsWith("securechat_")) {
    return { productName: "SecureChat", productUrl: "https://securechat.stealthx.tech/" };
  }
  if (productKey?.startsWith("chameleon_")) {
    return { productName: "Chameleon", productUrl: "https://chameleon.stealthx.tech/" };
  }
  if (productKey === "stealthx_suite_lifetime") {
    return { productName: "StealthX Suite", productUrl: "https://stealthx.tech/" };
  }
  return { productName: "SecureCall", productUrl: "https://stealthx.tech/download.html" };
}

async function handleWebhook(event, stripe, activationCodesRef) {
  console.log("[STRIPE] === WEBHOOK RECEIVED ===");
  console.log("[STRIPE] Event type:", event.type, "id:", maskStripeId(event.id));
  console.log("[STRIPE] RESEND_API_KEY set:", !!process.env.RESEND_API_KEY);

  // Idempotency guard — Stripe retries on failure, must not double-process.
  if (event.id && processedEvents.has(event.id)) {
    console.log("[STRIPE] Event already processed (idempotent skip):", event.id);
    return { alreadyProcessed: true, eventId: event.id };
  }

  if (["charge.refunded", "charge.dispute.created"].includes(event.type)) {
    const object = event.data?.object || {};
    const isFullRefund = event.type === "charge.dispute.created"
      || (Number.isSafeInteger(object.amount) && object.amount > 0 && object.amount_refunded >= object.amount);
    if (!isFullRefund) return { ignored: true, reason: "partial_refund_requires_review" };
    const paymentIntent = typeof object.payment_intent === "string" ? object.payment_intent : object.payment_intent?.id;
    if (!paymentIntent) throw new Error("missing_payment_intent");
    const sessions = await stripe.checkout.sessions.list({ payment_intent: paymentIntent, limit: 10 });
    const session = sessions.data?.find(candidate => candidate.metadata?.type === "custom_id");
    if (!session) return { ignored: true, reason: "not_custom_id_payment" };
    const revoked = require("../custom_ids").revokeByStripeSession(session.id);
    const { buildRecord, exportFinanceRecord } = require("./vlabs_finance_export");
    await exportFinanceRecord(buildRecord(
      { ...session, payment_status: "refunded" },
      session.metadata?.product || "custom_id",
      "adjustment",
      event.type === "charge.dispute.created" ? "stripe_dispute" : "stripe_full_refund"
    ));
    if (event.id) {
      processedEvents.set(event.id, Date.now());
      saveProcessedEvents();
    }
    return { revoked, tier: "custom_id", productKey: session.metadata?.product || "custom_id" };
  }

  if (!["checkout.session.completed", "checkout.session.async_payment_succeeded"].includes(event.type)) {
    console.log("[STRIPE] Unhandled event type:", event.type);
    return null;
  }

  if (!isPaidCheckoutEvent(event)) {
    console.warn("[STRIPE] Ignoring unpaid or malformed checkout event:", event.id);
    return { ignored: true, reason: "checkout_not_paid" };
  }

  const session = event.data.object;
  const email = session.customer_email || session.customer_details?.email || null;
  console.log("[STRIPE] Session id:", maskStripeId(session.id), "email:", email ? email.substring(0, 3) + "***" : "none");

  // 1. Resolve tier + productKey — first from metadata (created via API), then from line_items (Payment Links)
  let tier = session.metadata?.tier;
  let productKey = session.metadata?.product;

  if (!tier || !productKey) {
    // Payment Links path — fetch line_items from Stripe API
    try {
      const lineItems = await stripe.checkout.sessions.listLineItems(session.id, { limit: 5 });
      const priceId = lineItems?.data?.[0]?.price?.id;
      console.log("[STRIPE] Resolved priceId from line_items:", maskStripeId(priceId));
      const resolved = resolveTierFromPriceId(priceId);
      if (resolved) {
        tier = resolved.tier;
        productKey = resolved.productKey;
      }
    } catch (e) {
      console.error("[STRIPE] Failed to fetch line_items:", e.message);
    }
  }

  if (!tier) {
    console.warn("[STRIPE] Could not determine tier for session:", session.id);
    return null;
  }

  const licenseTier = session.metadata?.licenseTier ||
    (session.metadata?.type === "lifetime_dynamic" ? productKey : null);

  tier = session.metadata?.activationTier || tier;

  if (tier === "pro_lifetime") {
    tier = "pro";
    productKey = productKey || "pro_lifetime";
  } else if (tier === "premium_lifetime") {
    tier = "premium";
    productKey = productKey || "premium_lifetime";
  }

  console.log("[STRIPE] Tier:", tier, "ProductKey:", productKey, "LicenseTier:", licenseTier || "none");

  // 2. Custom ID purchases are handled separately (no activation code)
  if (tier === "custom_id") {
    const pendingToken = session.metadata?.pending_token;
    const customId = session.metadata?.custom_id;
    const markedPaid = require("../custom_ids").markPendingPaid(pendingToken, customId, session.id);
    if (!markedPaid) throw new Error("custom_id_payment_binding_failed");
    const { buildRecord, exportFinanceRecord } = require("./vlabs_finance_export");
    await exportFinanceRecord(buildRecord(session, productKey));
    console.log("[STRIPE] Custom ID payment confirmed for activation");
    if (event.id) {
      processedEvents.set(event.id, Date.now());
      saveProcessedEvents();
    }
    return { tier, productKey, email };
  }

  // 3. Generate unique activation code
  const code = generateActivationCode(tier);
  console.log("[STRIPE] Activation code generated:", code.substring(0, 4) + "****", "tier:", tier);

  // 4. Record sale + inject into activationCodes array (so ACTIVATE_CODE handler finds it)
  let soldCodeEntry;
  try {
    const soldCodes = require("./sold_codes");
    soldCodeEntry = soldCodes.recordSale({
      code,
      tier,
      stripeSessionId: session.id,
      productKey,
      activationCodesRef
    });
  } catch (err) {
    console.error("[STRIPE] Failed to record sold code:", err.message);
    throw err;
  }

  // 5. Record dynamic-pricing sale if applicable
  if (session.metadata?.type === "lifetime_dynamic" && licenseTier) {
    try {
      const { LICENSES, recordSale } = require("../licenses");
      if (LICENSES[licenseTier]) recordSale(licenseTier);
    } catch (e) { console.error("[STRIPE] licenses.recordSale failed:", e.message); }
  }

  // 6. Send activation code via email
  const emailDelivery = {
    status: email ? "pending" : "missing_email",
    attempts: (soldCodeEntry?.emailDelivery?.attempts || 0) + (email ? 1 : 0),
    lastAttemptAt: email ? new Date().toISOString() : null,
    deliveredAt: null,
    lastError: null
  };

  if (email) {
    try {
      const { sendActivationCode } = require("./email_handler");
      console.log("[STRIPE] Calling sendActivationCode()...");
      const sent = await sendActivationCode(email, code, tier, {
        productKey,
        ...resolveProductEmailOptions(productKey)
      });
      console.log("[STRIPE] Email send result:", sent);
      emailDelivery.status = sent ? "delivered" : "failed";
      emailDelivery.deliveredAt = sent ? new Date().toISOString() : null;
      emailDelivery.lastError = sent ? null : "email_provider_not_configured_or_rejected";
    } catch (err) {
      console.error("[STRIPE] Email delivery failed:", err.message, err.stack);
      emailDelivery.status = "failed";
      emailDelivery.lastError = err.message;
    }
  } else {
    console.warn("[STRIPE] No customer email — code saved but not delivered:", code.substring(0, 4) + "****");
  }

  try {
    const soldCodes = require("./sold_codes");
    soldCodes.updateEmailDelivery(session.id, emailDelivery);
  } catch (err) {
    console.error("[STRIPE] Failed to persist email delivery status:", err.message);
    throw err;
  }

  // Mark event as successfully processed (idempotency guard for Stripe retries).
  if (event.id) {
    processedEvents.set(event.id, Date.now());
    saveProcessedEvents();
  }

  return { code, tier, email, productKey, emailDeliveryStatus: emailDelivery.status };
}

/**
 * Generate a unique activation code in PREM-XXXX-XXXX-XXXX format.
 */
function generateActivationCode(tier) {
  const crypto = require("crypto");
  const normalizedTier = String(tier || "").toLowerCase();
  const prefix = normalizedTier === "premium" ? "PREM" : normalizedTier === "elite" ? "ELIT" : "PRO";
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  const part = () => Array.from({ length: 4 }, () => chars[crypto.randomInt(chars.length)]).join("");
  return `${prefix}-${part()}-${part()}-${part()}`;
}

/**
 * Express route setup — call from server.js:
 *   require('./payments/stripe_handler').setupRoutes(app, activationCodesRef);
 *
 * @param {Object} app  Express app
 * @param {Array}  activationCodesRef  Live reference to server.js activationCodes array
 */
function setupRoutes(app, activationCodesRef) {
  const secretKey = process.env.STRIPE_SECRET_KEY;
  if (!secretKey) {
    console.warn("[STRIPE] STRIPE_SECRET_KEY not set — Stripe routes disabled");
    return;
  }

  const stripe = require("stripe")(secretKey);
  const webhookSecret = process.env.STRIPE_WEBHOOK_SECRET;

  // Rate limit: 5 per IP per 10 minutes
  const checkoutLimits = new Map();
  function checkoutRL(req, res, next) {
    const ip = req.ip || req.connection.remoteAddress;
    const now = Date.now();
    if (!checkoutLimits.has(ip)) checkoutLimits.set(ip, []);
    const a = checkoutLimits.get(ip);
    while (a.length > 0 && now - a[0] > 600000) a.shift();
    if (a.length >= 5) return res.status(429).json({ error: "rate_limited" });
    a.push(now);
    next();
  }
  app.post("/stripe/create-checkout", checkoutRL, async (req, res) => {
    if (process.env.LEGACY_STRIPE_CHECKOUT_ENABLED !== "true") {
      return res.status(410).json({ error: "checkout_moved_to_vlabs" });
    }
    try {
      const { product, email } = req.body;
      const session = await createCheckoutSession(stripe, product || "premium_lifetime", email);
      res.json({ url: session.url, sessionId: session.id });
    } catch (err) {
      console.error("[STRIPE] Checkout error:", err.message);
      res.status(400).json({ error: "checkout_failed" });
    }
  });

  // Webhook (raw body middleware is applied globally in server.js before express.json())
  app.post("/stripe/webhook", async (req, res) => {
    let event;
    try {
      if (!webhookSecret) {
        console.error("[STRIPE] STRIPE_WEBHOOK_SECRET not set — rejecting webhook (fail-closed)");
        return res.status(503).send("Webhook secret not configured");
      }
      event = stripe.webhooks.constructEvent(req.body, req.headers["stripe-signature"], webhookSecret);
    } catch (err) {
      console.error("[STRIPE] Webhook signature verification failed:", err.message);
      return res.status(400).send("Webhook error");
    }

    try {
      const result = await handleWebhook(event, stripe, activationCodesRef);
      if (result?.code) {
        console.log("[STRIPE] Activation code generated:", result.code.substring(0, 4) + "****", "tier:", result.tier || "unknown");
      }
      res.json({ received: true });
    } catch (err) {
      console.error("[STRIPE] Webhook handler error:", err.message, err.stack);
      res.status(500).json({ error: "webhook_handler_failed" });
    }
  });

  // Test email endpoint (admin only)
  app.post("/stripe/test-email", async (req, res) => {
    const adminKey = req.headers["x-admin-key"];
    if (adminKey !== process.env.ADMIN_API_KEY) {
      return res.status(401).json({ error: "Unauthorized" });
    }
    const { email, code } = req.body;
    if (!email) return res.status(400).json({ error: "Missing email" });

    // Return debug info about env vars
    const debug = {
      resendConfigured: Boolean(process.env.RESEND_API_KEY),
      brevoConfigured: Boolean(process.env.BREVO_API_KEY),
      brevoSmtpConfigured: Boolean(process.env.BREVO_SMTP_USER)
    };

    const errors = [];
    let sent = false;

    // Try Resend
    if (process.env.RESEND_API_KEY) {
      try {
        const { Resend } = require("resend");
        const resend = new Resend(process.env.RESEND_API_KEY);
        const result = await resend.emails.send({
          from: "SecureCall <noreply@stealthx.tech>", to: email,
          subject: "SecureCall Test", html: "<p>Code: " + (code || "TEST") + "</p>"
        });
        if (result.error) throw new Error(JSON.stringify(result.error));
        sent = true;
        return res.json({ success: true, provider: "resend", debug, result: result.data });
      } catch (e) { errors.push({ provider: "resend", error: e.message }); }
    }

    // Try Brevo (HTTP REST API)
    if (process.env.BREVO_API_KEY) {
      try {
        const brevoRes = await fetch("https://api.brevo.com/v3/smtp/email", {
          method: "POST",
          headers: { "api-key": process.env.BREVO_API_KEY, "Content-Type": "application/json", "Accept": "application/json" },
          body: JSON.stringify({
            sender: { name: "SecureCall", email: "noreply@stealthx.tech" },
            to: [{ email }], subject: "SecureCall Test",
            htmlContent: "<p>Code: " + (code || "TEST") + "</p>"
          })
        });
        const brevoBody = await brevoRes.text();
        if (!brevoRes.ok) throw new Error(`${brevoRes.status}: ${brevoBody}`);
        sent = true;
        return res.json({ success: true, provider: "brevo-http", debug, result: JSON.parse(brevoBody) });
      } catch (e) { errors.push({ provider: "brevo-http", error: e.message }); }
    }

    res.json({ success: false, debug, errors });
  });

  console.log("[STRIPE] Routes enabled: POST /stripe/create-checkout, POST /stripe/webhook, POST /stripe/test-email");
}

module.exports = { createCheckoutSession, handleWebhook, generateActivationCode, isPaidCheckoutEvent, maskStripeId, setupRoutes, PRODUCTS };
