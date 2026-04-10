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

const PRODUCTS = {
  pro_monthly: {
    name: "SecureCall Pro",
    priceId: "price_1TInrMBcyoLtm3FAWcdsNUX3",
    mode: "subscription",
    tier: "pro"
  },
  premium_monthly: {
    name: "SecureCall Premium",
    priceId: "price_1TInt2BcyoLtm3FAPOw0vJ5p",
    mode: "subscription",
    tier: "premium"
  },
  premium_lifetime: {
    name: "SecureCall Premium Lifetime",
    priceId: "price_1TInt8BcyoLtm3FACbPJZZPg",
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
    sessionParams.payment_method_types = ["card", "klarna", "sepa_debit"];
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
    if (p.priceId === priceId) {
      return { tier: p.tier, productKey: key };
    }
  }
  // Custom ID purchases — use special productKey, no activation code
  const customIdPrices = {
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
async function handleWebhook(event, stripe, activationCodesRef) {
  console.log("[STRIPE] === WEBHOOK RECEIVED ===");
  console.log("[STRIPE] Event type:", event.type);
  console.log("[STRIPE] RESEND_API_KEY set:", !!process.env.RESEND_API_KEY);

  if (event.type !== "checkout.session.completed") {
    console.log("[STRIPE] Unhandled event type:", event.type);
    return null;
  }

  const session = event.data.object;
  const email = session.customer_email || session.customer_details?.email || null;
  console.log("[STRIPE] Session id:", session.id, "email:", email);

  // 1. Resolve tier + productKey — first from metadata (created via API), then from line_items (Payment Links)
  let tier = session.metadata?.tier;
  let productKey = session.metadata?.product;

  if (!tier || !productKey) {
    // Payment Links path — fetch line_items from Stripe API
    try {
      const lineItems = await stripe.checkout.sessions.listLineItems(session.id, { limit: 5 });
      const priceId = lineItems?.data?.[0]?.price?.id;
      console.log("[STRIPE] Resolved priceId from line_items:", priceId);
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

  console.log("[STRIPE] Tier:", tier, "ProductKey:", productKey);

  // 2. Custom ID purchases are handled separately (no activation code)
  if (tier === "custom_id") {
    console.log("[STRIPE] Custom ID purchase — handled by custom_ids.js flow");
    return { tier, productKey, email };
  }

  // 3. Generate unique activation code
  const code = generateActivationCode(tier);
  console.log("[STRIPE] Activation code generated:", code);

  // 4. Record sale + inject into activationCodes array (so ACTIVATE_CODE handler finds it)
  try {
    const soldCodes = require("./sold_codes");
    soldCodes.recordSale({
      code,
      tier,
      email: email || "unknown",
      stripeSessionId: session.id,
      productKey,
      activationCodesRef
    });
  } catch (err) {
    console.error("[STRIPE] Failed to record sold code:", err.message);
  }

  // 5. Record dynamic-pricing sale if applicable
  if (session.metadata?.type === "lifetime_dynamic" && (tier === "pro_lifetime" || tier === "premium_lifetime")) {
    try {
      const { recordSale } = require("../licenses");
      recordSale(tier);
    } catch (e) { console.error("[STRIPE] licenses.recordSale failed:", e.message); }
  }

  // 6. Send activation code via email
  if (email) {
    try {
      const { sendActivationCode } = require("./email_handler");
      console.log("[STRIPE] Calling sendActivationCode()...");
      const sent = await sendActivationCode(email, code, tier);
      console.log("[STRIPE] Email send result:", sent);
    } catch (err) {
      console.error("[STRIPE] Email delivery failed:", err.message, err.stack);
    }
  } else {
    console.warn("[STRIPE] No customer email — code saved but not delivered:", code);
  }

  return { code, tier, email, productKey };
}

/**
 * Generate a unique activation code in PREM-XXXX-XXXX-XXXX format.
 */
function generateActivationCode(tier) {
  const crypto = require("crypto");
  const prefix = tier === "premium" ? "PREM" : "PRO";
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

  // Create checkout session
  app.post("/stripe/create-checkout", async (req, res) => {
    try {
      const { product, email } = req.body;
      const session = await createCheckoutSession(stripe, product || "premium_lifetime", email);
      res.json({ url: session.url, sessionId: session.id });
    } catch (err) {
      console.error("[STRIPE] Checkout error:", err.message);
      res.status(400).json({ error: err.message });
    }
  });

  // Webhook (raw body middleware is applied globally in server.js before express.json())
  app.post("/stripe/webhook", async (req, res) => {
    let event;
    try {
      if (webhookSecret) {
        event = stripe.webhooks.constructEvent(req.body, req.headers["stripe-signature"], webhookSecret);
      } else {
        event = JSON.parse(req.body);
      }
    } catch (err) {
      console.error("[STRIPE] Webhook signature verification failed:", err.message);
      return res.status(400).send("Webhook error");
    }

    try {
      const result = await handleWebhook(event, stripe, activationCodesRef);
      if (result?.code) {
        console.log("[STRIPE] Activation code generated:", result.code, "sent to:", result.email);
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
      RESEND_API_KEY: process.env.RESEND_API_KEY ? `set (${process.env.RESEND_API_KEY.substring(0, 10)}...)` : "NOT SET",
      BREVO_API_KEY: process.env.BREVO_API_KEY ? `set (${process.env.BREVO_API_KEY.substring(0, 12)}...)` : "NOT SET",
      BREVO_SMTP_USER: process.env.BREVO_SMTP_USER || "NOT SET"
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

module.exports = { createCheckoutSession, handleWebhook, generateActivationCode, setupRoutes, PRODUCTS };
