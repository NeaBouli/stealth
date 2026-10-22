# VLABS fulfillment next — draft

Status: local design and default-off Custom-ID reservation implementation. No deployment,
live configuration, signing-key generation, Android change, or subscription implementation
is part of this work.

**Owner decision 2026-10-17 (price model A):** one-time purchase / lifetime licence only, end prices incl. VAT
(SecureCall Pro 15 EUR, Premium 25 EUR; SecureChat Pro 9 / Elite 19 EUR; Chameleon Pro 9 / Elite 19 EUR),
VAT computed on the VLABS side, GB blocked on the VLABS side. No subscriptions: section 3 is kept
for reference only and will not be implemented. The legacy monthly Stripe prices stay closed.
The six existing lifetime offers and their immutable contracts remain unchanged.

## 1. Existing trust and delivery boundary

VLABS owns checkout, Stripe webhook verification, VAT calculation, billing documents,
customer communication, and retry/reconciliation jobs. StealthX trusts only authenticated
VLABS server requests, never a browser redirect or a client-supplied paid claim.

Both existing POST endpoints retain their authentication and response conventions:

- `/internal/vlabs/fulfill`
- `/internal/vlabs/revoke`
- `x-vlabs-timestamp`: decimal Unix seconds, exactly ten digits, within 300 seconds of
  the receiver's clock.
- `x-vlabs-signature`: hex HMAC-SHA256 of `timestamp + "." + JSON.stringify(body)`, using
  the existing `VLABS_FULFILLMENT_SECRET` of at least 32 characters. The receiver signs
  the parsed body's JavaScript serialization, not arbitrary raw HTTP formatting. VLABS
  must serialize the same object consistently and sign every field.

The existing shared, bounded per-IP limiter applies before authentication: 60 requests
per 60 seconds across fulfill and revoke. Error bodies remain
`{"ok":false,"error":"<message>"}`. A fresh signature/timestamp is needed for a later
retry; the order identity stays the same. VLABS must retain its own verified Stripe proof.
StealthX does not query Stripe to independently verify the signed paid assertion.

## 2. Custom-ID fulfillment contract

### Draft catalog and switch

The product ID selects `kind: "custom_id"` in a separate frozen `CUSTOM_ID_PRODUCTS`
table. There is no client-controlled kind override. `PRODUCTS` and
`CATALOG_VERSION = stealthx-lifetime-v1` remain unchanged for lifetime applications.

All Custom-ID entries have `catalogVersion: stealthx-custom-id-v1`,
`releaseId: stealthx-custom-id-reservation-v1`, `tier: custom_id`, and `currency: eur`.
This release ID names the reservation protocol, not an Android artifact.

| Product ID | Product key | Normalized length | Amount, EUR cents | Offer version |
|---|---|---|---|---|
| `stealthx-custom-id-standard` | `custom_id_standard` | 10–30 | 100 | `custom-id-standard-eur-100-v1` |
| `stealthx-custom-id-short` | `custom_id_short` | 5–9 | 200 | `custom-id-short-eur-200-v1` |
| `stealthx-custom-id-ultra` | `custom_id_ultra` | 3–4 | 500 | `custom-id-ultra-eur-500-v1` |

Only the exact string `VLABS_CUSTOM_ID_FULFILLMENT_ENABLED=true` permits new Custom-ID
fulfillment. Unset, `false`, and other values return 409 after HMAC authentication,
without writing an order or reservation. Existing lifetime sales are unaffected.
Custom-ID refunds and disputes remain enabled when this sales switch is off.
`ID_HASH_PEPPER` is also required for new reservations; absence returns 503. No flag
or secret is configured by this draft.

### Fulfill request

Synthetic example, using the same proof fields as existing lifetime purchases:

```json
{
  "externalOrderId": "cs_test_draft_custom_001",
  "productId": "stealthx-custom-id-standard",
  "catalogVersion": "stealthx-custom-id-v1",
  "offerVersion": "custom-id-standard-eur-100-v1",
  "releaseId": "stealthx-custom-id-reservation-v1",
  "paymentReference": "pi_test_draft_custom_001",
  "paymentEventId": "evt_test_draft_custom_paid_001",
  "paymentStatus": "paid",
  "amount": 100,
  "currency": "eur",
  "customerEmail": "buyer@example.invalid",
  "requestedId": "example-long-id"
}
```

The field is a bounded string, trimmed and converted to lowercase. After normalization,
IDs have 3–30 ASCII letters/digits/hyphens, with a letter/digit at each end. One and two
character IDs stay reserved. The selected product's length tier and exact gross amount
must both match. Currency is compared case-insensitively. Existing length, email,
`cs_`, `pi_`, and `evt_` proof validation remains in force.

Success and replay keep the existing response shapes; no token, password, or routing
identifier is returned:

```json
{"ok":true,"fulfilled":true,"productId":"stealthx-custom-id-standard"}
```

```json
{"ok":true,"duplicate":true,"productId":"stealthx-custom-id-standard"}
```

`FULFILLED` means the paid ID is durably reserved for this order. It does not activate
an Android app tier or bind an ID to a device. VLABS sends the reservation confirmation;
the activation-code email template is not used for Custom IDs. `customerEmail` remains
required for envelope compatibility but is not persisted or used for delivery here.

### Availability, ownership, persistence, and idempotency

The existing `GET /custom-id/check?id=...` can display indicative availability and price.
It is not a pre-payment hold. The authenticated fulfillment performs the authoritative
check and reservation synchronously under the existing ownership-checked orders lock:

1. Validate the signed product, paid proof, amount, currency, and requested ID.
2. Reuse an exact existing order or persist a recoverable order intent before touching
   the Custom-ID registry. The immutable order tuple additionally binds the normalized
   ID through `HMAC-SHA256(ID_HASH_PEPPER, normalizedId)`.
3. Reject another owner, a live legacy pending activation, or a reversed payment.
   Reload persisted registries strictly; malformed JSON and failed writes propagate.
4. Reserve the HMAC-keyed entry in the existing `custom_ids.json` registry. The entry
   records `source: vlabs`, `stripeSessionId`, length, timestamp, and a null device ID.
   Neither the raw ID nor the customer email is stored in the fulfillment registry.
5. Persist `FULFILLED`. A replay of the same normalized ID and contract returns duplicate
   success. A changed ID, product, payment reference, version, amount, or currency is 409.
   A different payment event for the same order and immutable tuple is still a replay.

The state names remain `PENDING_EMAIL`, `FULFILLED`, `REVOKED`, and `TOMBSTONED`.
For Custom IDs, the historical `PENDING_EMAIL` name is the recoverable intent marker;
no SMTP delivery occurs. There is no new state. A failed registry write leaves the intent
retryable. A crash after reservation but before finalization reuses that order's
reservation. Another order cannot acquire the same ID. Completed reservations have no
automatic TTL; release requires an authenticated refund/dispute.

The canonical Custom-ID API and its JSON stores currently assume one signaling runtime.
The orders lock serializes VLABS writers, but public Custom-ID operations use an in-memory
registry. Multi-process serving, shared-network storage, and changing the pepper while
orders exist need a separate coordinated storage/migration design before activation.

### Revoke request

The existing endpoint, reasons, proof fields, and immutable tuple remain unchanged:

```json
{
  "externalOrderId": "cs_test_draft_custom_001",
  "productId": "stealthx-custom-id-standard",
  "catalogVersion": "stealthx-custom-id-v1",
  "offerVersion": "custom-id-standard-eur-100-v1",
  "releaseId": "stealthx-custom-id-reservation-v1",
  "paymentReference": "pi_test_draft_custom_001",
  "adjustmentEventId": "evt_test_draft_custom_refunded_001",
  "reason": "stripe_full_refund"
}
```

`stripe_dispute` is the other accepted reason. Partial refunds are rejected. As before,
`externalOrderId` may be omitted for a payment-reference-only reversal. The receiver
resolves the canonical order; ambiguous/conflicting matches return 409. `requestedId`
is unnecessary: release uses the stored order ownership, not a caller-supplied ID.

The reversal first records the existing durable payment/session tombstone in `sold_codes`,
without creating an activation code. Then it durably removes only the matching VLABS
reservation before marking the order `REVOKED`. A pre-fulfillment reversal creates
`TOMBSTONED` and blocks later fulfill attempts, including payment-reference-only reversals.
The ID becomes available again after successful release. Retry after a failed release
or terminal order write remains safe; it cannot remove a later buyer's reservation.
Terminal orders never become active again through a delayed paid event.

```json
{"ok":true,"revoked":true,"productId":"stealthx-custom-id-standard"}
```

Before fulfillment the response has `tombstoned:true`; a repeated completed reversal has
`duplicate:true`, following the existing contract.

### Status codes and retry policy

| Status | Meaning and VLABS action |
|---|---|
| 200 | Fulfilled, revoked, tombstoned, or duplicate; accept the corresponding response flag. |
| 400 | Malformed/missing proof or field, invalid/reserved ID, invalid email, partial/unsupported reversal reason; fix the request. |
| 401 | Invalid/missing/stale signature; correct authentication and regenerate the signature. |
| 409 | Default-off product, unavailable ID, wrong length tier, catalog/amount/currency/proof drift, changed order tuple, terminal/reversed order, ambiguous reversal, or fulfillment already in progress. Reconcile the reason before retrying. |
| 429 | Existing shared request budget exceeded; retry with backoff and a fresh signature. |
| 503 | Missing server configuration or failed/busy/corrupt durable storage; retry the identical order with backoff and alert on persistent failure. |

An availability race after payment can produce 409. VLABS must durably queue fulfillment,
stop retrying permanent conflicts as though they were delivery failures, and provide a
refund/reselection path. A pre-checkout hold with expiry and order binding is a separate
future contract; this draft reserves only after a verified paid event. A changed ID
requires a new order; replay cannot rewrite the original order's bound ID.

### Remaining Custom-ID launch work

This draft implements paid reservation and refund/dispute release only. A future
authenticated claim flow must bind the reserved order to its owner/device and collect
transfer credentials on StealthX. The legacy token-activation endpoint cannot claim this
reservation: no legacy pending token is minted here. Do not invent a browser token,
password, or device ID in VLABS metadata to bypass that binding. Customer confirmation,
claim/recovery UX, pre-payment holds or collision compensation, and VLABS's catalog
checker updates must be integrated and tested before enabling real Custom-ID sales.

## 3. Monthly subscription lifecycle — not needed (owner chose lifetime, 2026-10-17)

The owner must first choose lifetime versus monthly offers, cancellation timing, and
any grace period. Existing lifetime fulfill/revoke cannot represent a paid-through date;
do not reinterpret a renewal as another lifetime order or treat cancellation as a refund.

If monthly plans are chosen, introduce a separately versioned signed lifecycle contract,
for example `POST /internal/vlabs/subscription-events`, using the same HMAC header scheme.
No such route is added by this draft. A proposed renewal payload is:

```json
{
  "schemaVersion": "stealthx-subscriptions-v1",
  "eventId": "evt_test_draft_renewal_001",
  "eventType": "renewal",
  "externalSubscriptionId": "sub_test_draft_001",
  "externalOrderId": "cs_test_draft_initial_001",
  "productId": "OWNER_APPROVED_MONTHLY_PRODUCT",
  "catalogVersion": "OWNER_APPROVED_MONTHLY_CATALOG",
  "offerVersion": "OWNER_APPROVED_MONTHLY_OFFER",
  "releaseId": "OWNER_APPROVED_ANDROID_RELEASE",
  "paymentReference": "pi_test_draft_renewal_001",
  "paymentStatus": "paid",
  "amount": 1500,
  "currency": "eur",
  "periodStart": 1790812800,
  "periodEnd": 1793491200,
  "effectiveAt": 1790812800
}
```

Amounts/product names above are illustrative, not approved monthly pricing. VLABS verifies
Stripe invoice/subscription events and sends the canonical paid interval, not “add one
month now.” Initial checkout binds the subscription to an entitlement/device through the
authenticated activation path; VLABS does not choose a replacement entitlement subject.

| Event type | VLABS evidence | Proposed StealthX behavior |
|---|---|---|
| `renewal` | Settled invoice, immutable product/price, payment reference, paid interval | Extend paid-through to the maximum verified period end; never double-add time on replay. |
| `cancellation` | Verified cancellation, cancellation mode and effective end | Record non-renewing state; retain access until paid-through for end-of-period cancellation. Immediate cancellation requires an explicit policy. |
| `payment_failure` | Verified invoice failure and affected interval | Record failure; grant no extension. Keep already-paid time; grace, if any, is owner-defined. |
| `period_end` | Canonical interval end and subscription state | Expire access when the paid-through/grace boundary is reached, unless a newer paid renewal exists. |

Use a durable idempotency ledger keyed by source plus `eventId` with a canonical payload
digest. Identical event replay returns 200 duplicate; a changed body for the same event is
409. Validate transitions and subscription ownership under a transaction/lock. Serialize
each subscription's state changes, persist the event and entitlement together, and commit
before acknowledging. Out-of-order failures, cancellations, or period-end events cannot
truncate newer paid intervals. A scheduled server expiry check is also needed when a
period-end notification is missing. Refund/dispute precedence stays terminal for the
affected payment; an unrelated later paid period needs an explicit subscription policy.

Token issuance/refresh must consult subscription state and cap `exp` at the earlier of
paid-through (plus approved grace) and the existing 30-day proof TTL. Current commercial
v2 token/product validation is lifetime-specific and needs a versioned subscription
extension in both backend and Android. A lifetime proof's 30-day refresh TTL is not a
monthly subscription. These changes and lifecycle tests await the owner's decision.

## 4. Android entitlement verifier provisioning for a later build

Current source contracts are `payments/entitlement_tokens.js`,
`client_android/app/build.gradle`, `DirectEntitlementVerifier.kt`, and
`DirectEntitlementStore.kt`. The Gradle property is `-PentitlementPublicKey`; when supplied,
it must match `[A-Za-z0-9_-]{43}`: an unpadded base64url encoding of exactly 32 raw Ed25519
public bytes, not a PEM/SPKI document. It becomes `BuildConfig.ENTITLEMENT_PUBLIC_KEY`.
An empty verifier stays fail-closed for commercial direct entitlement verification.

Provisioning sequence for an authorized future release, without doing it in this task:

1. The owner provisions an Ed25519 signing key through the approved secret-management
   process. Reuse the intended commercial signer when one already exists; do not rotate
   it casually. Keep it separate from the VLABS HMAC secret, ID pepper, and tester signer.
2. Supply only the backend with `ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM_BASE64` (standard
   base64 of the PEM text), or the existing alternative `ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM`.
   The base64 variable takes precedence. Use a deployment secret, never a repository file,
   build argument containing a private key, customer response, Bridge entry, or log.
3. Derive/export only the public verifier in the trusted provisioning environment. For
   Ed25519, the public JWK `x` member is the required raw 32-byte base64url representation.
   Check curve/type, canonical encoding, decoded length, and correspondence to the signer.
   Publish only that public value to the Android build configuration.
4. Pass `-PentitlementPublicKey=<public verifier>` to the next authorized internal Pro and
   Premium builds. Match the backend catalog/offer/release tuple and Android's verifier
   mappings/`ENTITLEMENT_RELEASE_ID` to that exact build. A changed Android release identity
   needs a coordinated versioned sales contract, not silent drift of an existing offer.
5. In isolated validation, prove backend-issued v2 tokens are accepted only with the
   matching public verifier, subject, product/tier, release, signature, and valid time.
   Prove wrong-key, tampered, expired, rollback, revoked-refresh, and missing-signer cases
   fail closed. Never use live customer data or expose a private key for compatibility tests.

The backend signs the base64url payload text with Ed25519 and returns
`encodedPayload.encodedSignature`. `DirectEntitlementStore` revalidates stored proof on
every access through `DirectEntitlementVerifier`; it does not trust a persisted paid-tier
string. Current commercial direct proofs are limited to 30 days and refresh checks the
server's still-valid activation binding. Offline revocation is bounded by token expiry,
not immediate. Custom-ID reservations do not use these app-tier tokens.

The existing distribution split remains mandatory: Play uses only `freeRelease` /
`com.securecall.app.free` / `app-free-release.aab`; direct Pro and Premium use their
separate internal packages/APKs. The commercial `DirectEntitlementStore` currently accepts
Pro/Premium flavors only. A Play feature unlock cannot acquire the direct Premium VPN
runtime. Future Android releases must run both applicable distribution gates in
`DISTRIBUTION_MATRIX.md`. No Android build, key provisioning, or artifact validation is
claimed by this backend-only draft.

## 5. Gross pricing, VAT, and geography

Every catalog price is the final customer price including VAT: Custom IDs cost EUR 1, 2,
or 5; existing lifetime prices remain their frozen catalog amounts. VLABS calculates VAT,
configures Stripe prices with `tax_behavior=inclusive`, and owns tax invoices/accounting.
VAT must be included within the displayed price, not added on top at checkout.

VLABS blocks GB on its checkout/billing-country side before taking payment. This draft
does not add a StealthX country filter or recompute tax from customer location. A signed
fulfill must report the exact gross catalog amount and EUR currency. Any discount,
exclusive-tax uplift, stale price, or length-tier mismatch producing a different amount
receives 409. The receiver never accepts a caller's amount as a new price. Dynamic legacy
`licenses.js` pricing does not change the immutable VLABS catalog.

## 6. Local verification and owner decisions

The new Node test is included in both `npm test` and `npm run test:payments`. It covers
default-off behavior, HMAC rejection, schema/price/tier boundaries, ownership collisions,
pending legacy activations, normalized replay and tuple drift, both reversal reasons,
payment-only and pre-fulfillment tombstones, restarts, replacement ownership, store
corruption/locks, and injected persistence failures between registry/order writes.
The full existing `npm test` also runs its pretest hooks. See the task handover for the
actual command/result; source-level test coverage is not a deployment result.

Open decisions before launch:

- Lifetime or monthly products? If monthly: prices, grace, cancellation, and refund policy.
- How should a buyer securely claim/recover a reserved Custom ID and bind a device?
- Pre-checkout hold versus automatic refund/reselection on a paid ID collision?
- Who provisions the commercial signer and passes its matching public verifier to the
  next exact Android build, including coordinated contract versions?

Keep the Custom-ID switch off until the claim/delivery and VLABS integration work is
complete. No production enablement or owner product decision is implied by this draft.

## Known limitations

- **B2 — Free cannot redeem direct entitlements:** activation remains disabled in Free
  (`client_android/app/build.gradle:108`), and the direct proof store accepts only Pro/Premium
  flavors (`client_android/app/src/main/java/com/securecall/app/billing/DirectEntitlementStore.kt:36`).
- **B4 — Release-ID pin:** the signed release claim must exactly match the APK's
  `ENTITLEMENT_RELEASE_ID` (`client_android/app/build.gradle:66`;
  `client_android/app/src/main/java/com/securecall/app/billing/DirectEntitlementVerifier.kt:127`).
  Future release identities require coordinated backend catalog and Android changes.
- **B5 — 30-day proof TTL:** lifetime purchases still need periodic server refresh; commercial
  proofs expire after 30 days (`backend/signaling/src/payments/entitlement_tokens.js:6`), and
  Android rejects expired proofs or longer lifetimes
  (`client_android/app/src/main/java/com/securecall/app/billing/DirectEntitlementVerifier.kt:134`).
