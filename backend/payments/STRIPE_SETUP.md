# Stripe Setup — SecureCall

## Create Products (Stripe Dashboard)

### Sandbox: https://dashboard.stripe.com/test/products
### Live: https://dashboard.stripe.com/products

| # | Product | Price | Type | Env Variable |
|---|---------|-------|-----|-------------|
| 1 | SecureCall Pro | €3.49/month | recurring | `STRIPE_PRO_PRICE_ID` |
| 2 | SecureCall Premium | €4.99/month | recurring | `STRIPE_PREMIUM_PRICE_ID` |
| 3 | SecureCall Premium Lifetime | €49.00 one-time | one_time | `STRIPE_LIFETIME_PRICE_ID` |

## Step by Step

1. **Create Stripe account** (if not already existing):
   - https://dashboard.stripe.com/register
   - Business: Vendetta Labs, Greece

2. **Product 1: SecureCall Pro**
   - Products → + Add Product
   - Name: `SecureCall Pro`
   - Description: `Pro subscription — unlimited calls, no ads`
   - Pricing: €3.49, Recurring, Monthly
   - → Note the `price_...` ID

3. **Product 2: SecureCall Premium**
   - Products → + Add Product
   - Name: `SecureCall Premium`
   - Description: `Premium subscription — maximum security + VPN`
   - Pricing: €4.99, Recurring, Monthly
   - → Note the `price_...` ID

4. **Product 3: SecureCall Premium Lifetime**
   - Products → + Add Product
   - Name: `SecureCall Premium Lifetime`
   - Description: `One-time payment for lifetime Premium access. Generates activation code.`
   - Pricing: €49.00, One time
   - → Note the `price_...` ID

5. **Create Webhook**
   - Developers → Webhooks → + Add endpoint
   - URL: `https://protective-healing-production.up.railway.app/stripe/webhook`
   - Events: `checkout.session.completed`
   - → Note the `whsec_...` signing secret

6. **Set Railway Environment Variables**
   ```
   STRIPE_SECRET_KEY=sk_test_...       (Sandbox) or sk_live_... (Production)
   STRIPE_WEBHOOK_SECRET=whsec_...
   STRIPE_PRO_PRICE_ID=price_...
   STRIPE_PREMIUM_PRICE_ID=price_...
   STRIPE_LIFETIME_PRICE_ID=price_...
   ```

7. **Integrate into server.js** (after Stripe configuration):
   ```js
   // At the end of server.js, before app.listen():
   require('../payments/stripe_handler').setupRoutes(app);
   ```

## API Endpoints

### POST /stripe/create-checkout
```json
{
  "product": "premium_lifetime",  // or "pro_monthly", "premium_monthly"
  "email": "user@example.com"     // optional
}
```
Response:
```json
{
  "url": "https://checkout.stripe.com/c/pay/...",
  "sessionId": "cs_test_..."
}
```

### POST /stripe/webhook
- Called automatically by Stripe
- Verifies signature via `STRIPE_WEBHOOK_SECRET`
- Generates activation code on `checkout.session.completed`

## Payment Methods
- Credit card (worldwide)
- SEPA direct debit (EU)
- Klarna (EU — invoice, installment payments)

## Email Delivery (Resend + Brevo Fallback)

**Order:** Resend (primary) → Brevo (backup)

### Resend Setup
1. **Account:** https://resend.com
2. **Verify domain:** stealthx.tech (DNS TXT)
3. **API Key** → `RESEND_API_KEY`
4. **Status:** Account suspended (support ticket open)

### Brevo Setup (Backup)
1. **Account:** https://brevo.com (free, 300 emails/day)
2. **Settings → SMTP & API → API Keys → Generate**
3. **Verify domain:** stealthx.tech
4. **API Key** → `BREVO_API_KEY=xkeysib-...`

### Flow After Payment:
```
User pays → Stripe webhook → Code generated
  → Resend email (primary)
  → if Resend fails → Brevo email (backup)
  → Customer receives code in inbox
  → Redirect to payment-success.html
```

### Railway Environment Variables (complete):
```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
RESEND_API_KEY=re_...          (primary — currently suspended)
BREVO_API_KEY=xkeysib-...     (backup — ready to use immediately)
```

## Payment Links (Stripe-hosted, no backend needed)
- Premium Lifetime €49: `https://buy.stripe.com/test_28E3cu3Sf545baKgeL6g800`
- Pro €3.49/mo: `https://buy.stripe.com/test_00w4gyewTaop1AabYv6g801`
- Premium €4.99/mo: `https://buy.stripe.com/test_5kQ3cu88vdABgv44w36g802`

## Status
- [x] Stripe products + prices created
- [x] Payment links on landing page (test mode)
- [x] stripe_handler.js with routes
- [x] server.js: setupRoutes() integrated
- [x] payment-success.html created
- [x] email_handler.js with Resend
- [ ] Set RESEND_API_KEY in Railway (Kaspartizan)
- [ ] Verify domain stealthx.tech in Resend (Kaspartizan)
- [ ] Perform test payment + verify email receipt
- [ ] Live mode: sk_test → sk_live, payment links → live links
