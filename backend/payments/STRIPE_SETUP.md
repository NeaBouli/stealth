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

## Resend Email Setup (Activation Code Delivery)

1. **Account erstellen:** https://resend.com
2. **Domain verifizieren:** stealthx.tech (DNS TXT record)
3. **API Key generieren** → Railway env var: `RESEND_API_KEY`
4. **Absender:** `SecureCall <noreply@stealthx.tech>`

### Flow nach Zahlung:
```
User zahlt → Stripe Webhook → Code generiert → Resend Email → Kunde erhält Code
                                                                    ↓
                                              payment-success.html zeigt Anleitung
```

### Railway Environment Variables (komplett):
```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
RESEND_API_KEY=re_...
```

## Payment Links (Stripe-hosted, kein Backend nötig)
- Premium Lifetime €49: `https://buy.stripe.com/test_28E3cu3Sf545baKgeL6g800`
- Pro €3.49/mo: `https://buy.stripe.com/test_00w4gyewTaop1AabYv6g801`
- Premium €4.99/mo: `https://buy.stripe.com/test_5kQ3cu88vdABgv44w36g802`

## Status
- [x] Stripe Produkte + Preise angelegt
- [x] Payment Links auf Landing Page (Test-Modus)
- [x] stripe_handler.js mit Routes
- [x] server.js: setupRoutes() eingebunden
- [x] payment-success.html erstellt
- [x] email_handler.js mit Resend
- [ ] RESEND_API_KEY in Railway setzen (Kaspartizan)
- [ ] Domain stealthx.tech in Resend verifizieren (Kaspartizan)
- [ ] Test-Zahlung durchführen + Email-Empfang prüfen
- [ ] Live-Modus: sk_test → sk_live, Payment Links → Live Links
