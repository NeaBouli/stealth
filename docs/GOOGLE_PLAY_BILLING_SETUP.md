# Google Play Billing Setup

## In-App Products to create in Play Console

### Subscriptions

1. **securecall_pro_monthly**
   - Type: Recurring subscription
   - Price: €3.49/month
   - Grace period: 3 days
   - Description: "Pro plan — unlimited calls, anti-recording, priority connection"

2. **securecall_pro_yearly**
   - Type: Recurring subscription
   - Price: €34.99/year (save 16%)
   - Grace period: 3 days

3. **securecall_premium_monthly**
   - Type: Recurring subscription
   - Price: €4.99/month
   - Grace period: 3 days
   - Description: "Premium plan — all features, WireGuard VPN, zero telemetry"

4. **securecall_premium_yearly**
   - Type: Recurring subscription
   - Price: €49.99/year (save 16%)
   - Grace period: 3 days

### One-Time Products

5. **securecall_pro_lifetime**
   - Type: One-time product (managed/non-consumable)
   - Price: Dynamic ($15–$50, set initial at $15)
   - Description: "Pro forever — one-time purchase, limited to 100 licenses"

6. **securecall_premium_lifetime**
   - Type: One-time product (managed/non-consumable)
   - Price: Dynamic ($25–$100, set initial at $25)
   - Description: "Premium forever — one-time purchase, limited to 100 licenses"

7. **securecall_premium_activation_code**
   - Type: One-time product (managed/non-consumable)
   - Price: €25.00
   - Description: "Premium activation code for supported SecureCall versions"

## Service Account for Purchase Verification

1. Go to [Google Cloud Console](https://console.cloud.google.com) → IAM → Service Accounts
2. Create new Service Account: `securecall-billing@your-project.iam.gserviceaccount.com`
3. Grant role: "Service Account User"
4. Enable **Google Play Android Developer API**
5. In [Play Console](https://play.google.com/console) → Settings → API access → Link the service account
6. Grant "View financial data" + "Manage orders" permissions
7. Download JSON key file
8. Base64 encode: `base64 -i service-account-key.json | tr -d '\n'`
9. Set in Railway: `GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64=<base64 string>`

## Real-Time Developer Notifications

1. In Play Console, select the app and configure a Google Cloud Pub/Sub topic for real-time developer notifications, including one-time products.
2. Create a push subscription for `https://<signaling-host>/billing/google-play-rtdn` and enable authenticated push.
3. Configure a dedicated push-auth service account and grant the Pub/Sub service agent permission to create OIDC tokens for it.
4. Set the exact push endpoint (or chosen audience override) as `GOOGLE_PLAY_RTDN_AUDIENCE`.
5. Set the push-auth service account email as `GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL`.
6. Send a Play Console test notification before release. The endpoint must return HTTP 204.

The endpoint verifies the Google-signed OIDC token, audience, service-account email, package allowlist and Pub/Sub message ID. It then calls the Google Play Developer API for subscription state. Notifications alone never grant access. Full voided-purchase notifications revoke matching subscriptions and activation codes.

For private accounting handoff, configure `VLABS_FINANCE_INGEST_URL` and `VLABS_FINANCE_INGEST_SECRET` only in the runtime environment. The shared secret must match the private VLABS `FINANCE_INGEST_SECURECALL_SECRET`; never place either value in this public repository.

## Backend Endpoint

`POST /billing/verify-purchase` is called by the purchasing Android app. It is public by design,
rate limited, restricted to allowlisted package/product pairs and grants a code only after the
Google Play Developer API confirms the purchase token. Never place an admin key in the app.

Request:
```json
{
  "purchase_token": "...",
  "product_id": "securecall_premium_activation_code",
  "package_name": "com.securecall.app.free"
}
```

Response:
```json
{
  "code": "PREM-A1B2C3D4",
  "tier": "premium",
  "expires": "2027-03-23T...",
  "product_id": "securecall_premium_activation_code"
}
```

## Testing

1. Add License Testers in Play Console → Settings → License testing
2. Add test account email addresses
3. Test purchases will be free but go through the full billing flow
4. Use `adb logcat | grep -i billing` to debug

## Important Notes

- Billing library version: 8.2.1 (`billing`)
- Only the FREE flavor includes billing (Pro/Premium are pre-activated)
- Purchased activation codes are stored in the signed activation-code registry so refunds can revoke them
- Codes are redeemed via WebSocket `ACTIVATE_CODE` message
- Without `GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64`, purchase and subscription verification fail closed
- Without the RTDN audience and service-account email, the RTDN endpoint returns HTTP 503
