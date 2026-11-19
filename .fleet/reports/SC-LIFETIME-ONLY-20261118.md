id: SC-LIFETIME-ONLY-20261118
status: ok
worker: claude
branch: agent/claude/SC-LIFETIME-ONLY-20261118
summary: Requirement correction (owner: no subscription was ever sold). base f3300a8992b9cced1d2ec802f3dccfb1c6f32564,
  code head ba879285ebd4bcef8446b44ec7b48318d984b989 tree 504a82c82283adc5d6d8a1aa7657c408e12f297d
  (this report is a separate follow-up commit). Mapped hop (MAP.md "Lifetime-only Play catalog"):
  build.gradle SKU constants -> Free BillingManager queryInAppProducts/purchaseReceipt -> UpgradeActivity
  -> SUBSCRIPTION_VERIFY -> google_play_billing catalog. Removed SKU_{PRO,PREMIUM}_{MONTHLY,YEARLY},
  SUBS product query, SUBS offer-token launchPurchaseFlow, client recurring SKU list; backend
  SUBSCRIPTION_PRODUCTS = {} so every recurring verify/ack/token lookup refuses. No new product,
  price, flag, module or migration rule.
files: client_android/app/build.gradle; client_android/app/src/free/.../billing/BillingManager.kt;
  testFree/.../billing/BillingManagerTest.kt, UpgradeActivityTest.kt;
  backend/signaling/src/payments/google_play_billing.js; src/__tests__/google_play_billing.test.js,
  google_play_lifetime.test.js; docs/architecture/MAP.md (append); BRIDGE.md (append).
tests: Gradle focused (--offline) :app:testFreeDebugUnitTest BillingManagerTest 7/7 (new: exact INAPP
  catalog = pro/premium lifetime + activation code, no month/year/SUBS BuildConfig field or member,
  single launch = launchInAppPurchaseFlow, one INAPP product query, 6 historical recurring ids refused
  in purchase+restore), UpgradeActivityTest 8/8, SubscriptionManagerTest 26/26.
  node (NODE_PATH=read-only existing node_modules, no npm ci): google_play_billing ok (empty frozen
  catalog; verify/ack reject before provider), subscriptions_payment ok, google_play_rtdn ok,
  subscription_webrtc 301/301, handlers 62/62, google_play_lifetime 75/75 (test 13 inverted to
  "recurring SUBSCRIPTION_VERIFY refused", tier stays FREE, 0 ack), rtdn_retry 1/1; node --check;
  git diff --check PASS. UI: NOT_RUN/not required - no layout/string change (UI already lifetime-only).
  NOT_RUN: full Gradle/npm/CI, artifact VPN guards (parent exact-head CI).
risks: Lifetime-only removal of the SUBS restore half was not done: SUBS purchase-history query kept
  inside the atomic restore (neutral plumbing; receipts dropped by purchaseReceipt). Retained active
  subscription references, intentionally: subscription parser/verify/ack/RTDN code (neutral, now
  refuses via empty catalog), SubscriptionManager/subscriptions store naming and its synthetic
  "securecall_pro_monthly" test strings (general licence store, no rename per brief),
  subscription.js LEGACY_UNSIGNED_PRODUCTS and stripe_handler pro_monthly (activation-code/Stripe
  rights, outside this hop). Requirement correction only; no evidence any Play product was deactivated.
security: no finding. No secrets, server, device, provider or signing access. Commerce flags closed.
next: parent review + one exact-head CI (incl. Free/Pro VPN guards); optionally decide whether to drop
  the SUBS restore query and Stripe monthly keys in a separate scoped brief.
