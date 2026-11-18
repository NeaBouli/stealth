# Google Play Billing Setup

> Launch gate: Play Billing and activation-code redemption are disabled in the
> current build. The entries below are candidate configuration only. Do not
> activate them until SecureCall PRODUCT_READY and matching VLABS FINANCE_READY
> are recorded for catalog version securecall-play-v1.

The signaling backend is independently closed unless
`PLAY_BILLING_ENABLED=true`. Do not set this variable until both gates are
recorded for `securecall-play-v1`; changing only the Android build flag is not
sufficient. RTDN processing remains available while sales are closed so that
historical revocations can still be applied.

## In-App Products to create in Play Console

### No Recurring Offers

SecureCall sells one-time lifetime licences, not monthly or yearly plans. Do not
create or reactivate recurring offers. The empty recurring verifier catalog
intentionally refuses those receipts; general licence APIs named "subscription"
do not authorize subscription sales.

### One-Time Products

1. **securecall_pro_lifetime**
   - Type: One-time product (managed/non-consumable)
   - Price: Not approved for Google Play; do not copy the separate direct-sale
     VLABS price into Play Console without a version-bound approval
   - Description: "Pro access for supported SecureCall versions"

2. **securecall_premium_lifetime**
   - Type: One-time product (managed/non-consumable)
   - Price: Not approved for Google Play
   - Description: "Premium access for supported SecureCall versions"

3. **securecall_premium_activation_code**
   - Type: One-time product (managed/non-consumable)
   - Price: Not approved for Google Play
   - Description: "Premium activation code for supported SecureCall versions"

## Service Account for Purchase Verification

Follow the [official API setup guide](https://developers.google.com/android-publisher/getting_started).
Reuse the approved Cloud project and a dedicated service account; enable the
Google Play Android Developer API. In Play Console **Users & Permissions**, invite
that account with the required Billing permissions, restricted to SecureCall:
"View financial data, orders, and cancellation survey responses" and "Manage
orders and subscriptions". Project-wide "Service Account User" is not a purchase
verification requirement; do not grant unrelated project or app access.

Provision credentials only through the protected server workflow and inject
`GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64` into the actual signaling runtime. Never
print the key/base64 value, put it in Git/CI, or leave an unprotected download.
A Console browser login alone does not configure server-side verification.

## Real-Time Developer Notifications

1. In Play Console, select the app and configure a Google Cloud Pub/Sub topic for real-time developer notifications, including one-time products.
2. Create a push subscription for `https://<signaling-host>/billing/google-play-rtdn` and enable authenticated push.
3. Configure a dedicated push-auth service account and grant the Pub/Sub service agent permission to create OIDC tokens for it.
4. Set the exact push endpoint (or chosen audience override) as `GOOGLE_PLAY_RTDN_AUDIENCE`.
5. Set the push-auth service account email as `GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL`.
6. Send a Play Console test notification before release. The endpoint must return HTTP 204.

The endpoint verifies the Google-signed OIDC token, audience, service-account email, package allowlist and Pub/Sub message ID. Recognized one-time purchases are verified against the Google Play Developer API; the empty recurring catalog cannot grant subscription access. Notifications alone never grant access. Full and quantity-based voided-purchase notifications revoke matching entitlements and activation codes.

The Android client never acknowledges a purchase before verification. The backend
acknowledges supported one-time products only after the corresponding
entitlement has been durably persisted; acknowledgement failure returns a closed
verification result and remains retryable.

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
  "package_name": "com.securecall.app.free",
  "catalog_version": "securecall-play-v1"
}
```

Response:
```json
{
  "code": "PREM-A1B2C3D4",
  "tier": "premium",
  "expires": "2027-03-23T...",
  "product_id": "securecall_premium_activation_code",
  "catalog_version": "securecall-play-v1"
}
```

## Testing

1. Add License Testers in Play Console → Settings → License testing
2. Add test account email addresses
3. Test purchases will be free but go through the full billing flow
4. Use `adb logcat | grep -i billing` to debug

## Important Notes

- Billing library version: 8.2.1 (`billing`)
- Only the FREE flavor contains the dormant Billing client; the release gate keeps it disabled
- Purchased activation codes are stored in the server-side activation registry so refunds can revoke them
- Codes are redeemed via WebSocket `ACTIVATE_CODE` message
- Without `GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64`, purchase verification fails closed
- Without the RTDN audience and service-account email, the RTDN endpoint returns HTTP 503
