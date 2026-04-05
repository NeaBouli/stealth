# Stripe Setup — SecureCall

## Produkte anlegen (Stripe Dashboard)

### Sandbox: https://dashboard.stripe.com/test/products
### Live: https://dashboard.stripe.com/products

| # | Produkt | Preis | Typ | Env Variable |
|---|---------|-------|-----|-------------|
| 1 | SecureCall Pro | €3.49/Monat | recurring | `STRIPE_PRO_PRICE_ID` |
| 2 | SecureCall Premium | €4.99/Monat | recurring | `STRIPE_PREMIUM_PRICE_ID` |
| 3 | SecureCall Premium Lifetime | €49.00 einmalig | one_time | `STRIPE_LIFETIME_PRICE_ID` |

## Schritt-für-Schritt

1. **Stripe Account erstellen** (falls noch nicht vorhanden):
   - https://dashboard.stripe.com/register
   - Business: Vendetta Labs, Greece

2. **Produkt 1: SecureCall Pro**
   - Products → + Add Product
   - Name: `SecureCall Pro`
   - Description: `Pro subscription — unlimited calls, no ads`
   - Pricing: €3.49, Recurring, Monthly
   - → Notiere die `price_...` ID

3. **Produkt 2: SecureCall Premium**
   - Products → + Add Product
   - Name: `SecureCall Premium`
   - Description: `Premium subscription — maximum security + VPN`
   - Pricing: €4.99, Recurring, Monthly
   - → Notiere die `price_...` ID

4. **Produkt 3: SecureCall Premium Lifetime**
   - Products → + Add Product
   - Name: `SecureCall Premium Lifetime`
   - Description: `One-time payment for lifetime Premium access. Generates activation code.`
   - Pricing: €49.00, One time
   - → Notiere die `price_...` ID

5. **Webhook erstellen**
   - Developers → Webhooks → + Add endpoint
   - URL: `https://protective-healing-production.up.railway.app/stripe/webhook`
   - Events: `checkout.session.completed`
   - → Notiere das `whsec_...` Signing Secret

6. **Railway Environment Variables setzen**
   ```
   STRIPE_SECRET_KEY=sk_test_...       (Sandbox) oder sk_live_... (Production)
   STRIPE_WEBHOOK_SECRET=whsec_...
   STRIPE_PRO_PRICE_ID=price_...
   STRIPE_PREMIUM_PRICE_ID=price_...
   STRIPE_LIFETIME_PRICE_ID=price_...
   ```

7. **Server.js einbinden** (nach Stripe-Konfiguration):
   ```js
   // Am Ende von server.js, vor app.listen():
   require('../payments/stripe_handler').setupRoutes(app);
   ```

## API Endpoints

### POST /stripe/create-checkout
```json
{
  "product": "premium_lifetime",  // oder "pro_monthly", "premium_monthly"
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
- Automatisch von Stripe aufgerufen
- Verifiziert Signatur via `STRIPE_WEBHOOK_SECRET`
- Generiert Activation Code bei `checkout.session.completed`

## Zahlungsmethoden
- Kreditkarte (weltweit)
- SEPA-Lastschrift (EU)
- Klarna (EU — Rechnung, Ratenzahlung)

## Nächste Schritte
1. Kaspartizan: Stripe Account + Produkte anlegen
2. Env Variables in Railway setzen
3. `setupRoutes(app)` in server.js einbinden
4. Website: "Buy" Button → POST /stripe/create-checkout → redirect to session.url
5. payment-success.html Seite erstellen (zeigt Code an)
6. Email-Versand des Codes (SendGrid/Resend)
