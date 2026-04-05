/**
 * Stripe + PayPal Checkout Handler (Grundstruktur)
 *
 * TODO: Connect to live Stripe account before production.
 *
 * Environment Variables needed:
 *   STRIPE_SECRET_KEY        — sk_live_... or sk_test_...
 *   STRIPE_WEBHOOK_SECRET    — whsec_...
 *   STRIPE_PREMIUM_PRICE_ID  — price_... (€49 one-time Premium Activation Code)
 *
 * Flow:
 *   1. User clicks "Buy Premium Code" on stealthx.tech
 *   2. POST /api/create-checkout → Stripe Checkout Session
 *   3. User pays via card/PayPal/SEPA
 *   4. Stripe fires webhook → generate activation code
 *   5. Redirect to success page with code
 */

// const stripe = require('stripe')(process.env.STRIPE_SECRET_KEY);

/**
 * Create a Stripe Checkout session for a Premium Activation Code purchase.
 */
async function createCheckoutSession(customerEmail) {
  /* --- UNCOMMENT WHEN STRIPE IS CONFIGURED ---
  const session = await stripe.checkout.sessions.create({
    payment_method_types: ['card'],
    line_items: [{
      price: process.env.STRIPE_PREMIUM_PRICE_ID,
      quantity: 1,
    }],
    mode: 'payment',
    success_url: 'https://stealthx.tech/payment-success?session_id={CHECKOUT_SESSION_ID}',
    cancel_url: 'https://stealthx.tech/#pricing',
    customer_email: customerEmail,
    metadata: { tier: 'premium', type: 'activation_code' }
  });
  return session;
  */
  throw new Error('Stripe not yet configured');
}

/**
 * Handle Stripe webhook events.
 * On checkout.session.completed → generate activation code + email to customer.
 */
async function handleWebhook(event) {
  if (event.type === 'checkout.session.completed') {
    const session = event.data.object;
    const tier = session.metadata.tier || 'premium';
    const email = session.customer_email;

    // Generate unique activation code
    const code = generateActivationCode(tier);

    console.log(`[STRIPE] Payment completed: ${email} → ${tier} code: ${code}`);

    // TODO: Send code via email (SendGrid, SES, etc.)
    // TODO: Store in database for tracking

    return { code, tier, email };
  }
}

/**
 * Generate a unique activation code in PREM-XXXX-XXXX-XXXX format.
 */
function generateActivationCode(tier) {
  const prefix = tier === 'premium' ? 'PREM' : 'PRO';
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  const part = () => Array.from({ length: 4 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
  return `${prefix}-${part()}-${part()}-${part()}`;
}

module.exports = { createCheckoutSession, handleWebhook, generateActivationCode };
