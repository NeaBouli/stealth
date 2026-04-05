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
 * Handle Stripe webhook events.
 * On checkout.session.completed → generate activation code.
 */
function handleWebhook(event) {
  if (event.type === "checkout.session.completed") {
    const session = event.data.object;
    const tier = session.metadata.tier || "premium";
    const productKey = session.metadata.product || "premium_lifetime";
    const email = session.customer_email || "unknown";

    // Generate unique activation code
    const code = generateActivationCode(tier);

    console.log(`[STRIPE] Payment completed: ${email} → ${tier} code: ${code} (product: ${productKey})`);

    // TODO: Send code via email (SendGrid, Resend, etc.)
    // TODO: Store in activation_codes for server-side validation

    return { code, tier, email, productKey };
  }
  return null;
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
 *   require('./payments/stripe_handler').setupRoutes(app);
 */
function setupRoutes(app) {
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

  // Webhook (Stripe sends raw body)
  app.post("/stripe/webhook", require("express").raw({ type: "application/json" }), (req, res) => {
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

    const result = handleWebhook(event);
    if (result) {
      // TODO: Store code in activationCodes array + save to file
      console.log("[STRIPE] Activation code generated:", result.code);
    }

    res.json({ received: true });
  });

  console.log("[STRIPE] Routes enabled: POST /stripe/create-checkout, POST /stripe/webhook");
}

module.exports = { createCheckoutSession, handleWebhook, generateActivationCode, setupRoutes, PRODUCTS };
