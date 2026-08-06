const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const testDir = path.join(os.tmpdir(), "securecall-stripe-handler-test");
fs.rmSync(testDir, { recursive: true, force: true });
fs.mkdirSync(testDir, { recursive: true });
process.env.SOLD_CODES_FILE = path.join(testDir, "sold_codes.json");
process.env.STRIPE_PROCESSED_FILE = path.join(testDir, "stripe_processed_events.json");
process.env.LICENSES_FILE = path.join(testDir, "licenses.json");
process.env.IDS_FILE = path.join(testDir, "custom_ids.json");
process.env.ID_HASH_PEPPER = "test-custom-id-pepper-32-characters-minimum";
process.env.PENDING_FILE = path.join(testDir, "pending_activations.json");
fs.writeFileSync(process.env.IDS_FILE, "{}");
fs.writeFileSync(process.env.PENDING_FILE, JSON.stringify({
  custom_paid_token: {
    customId: "example",
    passwordHash: "hash",
    passwordSalt: "salt",
    createdAt: Date.now(),
    stripeSessionId: "cs_custom_id_paid_test",
    paidAt: null
  }
}));
delete process.env.BREVO_API_KEY;
delete process.env.RESEND_API_KEY;

const {
  generateActivationCode,
  handleWebhook,
  isPaidCheckoutEvent,
  maskStripeId
} = require("../payments/stripe_handler");

assert.ok(generateActivationCode("premium").startsWith("PREM-"), "premium code uses PREM prefix");
assert.ok(generateActivationCode("pro").startsWith("PRO-"), "pro code uses PRO prefix");
assert.ok(generateActivationCode("elite").startsWith("ELIT-"), "elite code uses ELIT prefix");
assert.strictEqual(maskStripeId("cs_test_sensitive"), "cs_test_...", "Stripe IDs are masked in logs");
assert.strictEqual(isPaidCheckoutEvent({
  type: "checkout.session.completed",
  data: { object: { id: "cs_test_paid", payment_status: "paid" } }
}), true, "paid completed checkout is accepted");
assert.strictEqual(isPaidCheckoutEvent({
  type: "checkout.session.completed",
  data: { object: { id: "cs_test_unpaid", payment_status: "unpaid" } }
}), false, "unpaid completed checkout is rejected");

async function runDynamicLifetimeWebhookTest() {
  const activationCodes = [];
  const stripe = {
    checkout: {
      sessions: {
        listLineItems: async () => ({ data: [] })
      }
    }
  };

  const result = await handleWebhook({
    id: "evt_dynamic_premium_test",
    type: "checkout.session.completed",
    data: {
      object: {
        id: "cs_dynamic_premium_test",
        payment_status: "paid",
        metadata: {
          type: "lifetime_dynamic",
          tier: "premium",
          product: "premium_lifetime",
          licenseTier: "premium_lifetime"
        }
      }
    }
  }, stripe, activationCodes);

  assert.strictEqual(result.tier, "premium", "dynamic premium lifetime activates Premium tier");
  assert.strictEqual(result.productKey, "premium_lifetime", "dynamic premium lifetime keeps product key");
  assert.ok(result.code.startsWith("PREM-"), "dynamic premium lifetime code uses PREM prefix");
  assert.strictEqual(activationCodes[0].tier, "premium", "stored activation code activates Premium");
}

async function runProductLifetimeWebhookTest() {
  const activationCodes = [];
  const stripe = {
    checkout: {
      sessions: {
        listLineItems: async () => ({ data: [] })
      }
    }
  };

  const result = await handleWebhook({
    id: "evt_securechat_elite_test",
    type: "checkout.session.completed",
    data: {
      object: {
        id: "cs_securechat_elite_test",
        payment_status: "paid",
        metadata: {
          type: "lifetime_dynamic",
          tier: "elite",
          product: "securechat_elite_lifetime",
          licenseTier: "securechat_elite_lifetime"
        }
      }
    }
  }, stripe, activationCodes);

  assert.strictEqual(result.tier, "elite", "SecureChat Elite lifetime activates Elite tier");
  assert.ok(result.code.startsWith("ELIT-"), "SecureChat Elite lifetime code uses ELIT prefix");
  assert.strictEqual(activationCodes[0].tier, "elite", "stored SecureChat code activates Elite");
}

async function runEmailDeliveryStatusTest() {
  const activationCodes = [];
  const stripe = {
    checkout: {
      sessions: {
        listLineItems: async () => ({ data: [] })
      }
    }
  };

  const result = await handleWebhook({
    id: "evt_email_delivery_failed_test",
    type: "checkout.session.completed",
    data: {
      object: {
        id: "cs_email_delivery_failed_test",
        payment_status: "paid",
        customer_email: "buyer@example.com",
        metadata: {
          tier: "pro",
          product: "pro_monthly"
        }
      }
    }
  }, stripe, activationCodes);

  assert.strictEqual(result.emailDeliveryStatus, "failed", "webhook returns failed email delivery status");

  const soldCodes = JSON.parse(fs.readFileSync(process.env.SOLD_CODES_FILE, "utf8")).codes;
  const stored = soldCodes.find(c => c.stripeSessionId === "cs_email_delivery_failed_test");
  assert.ok(stored, "sold code was persisted");
  assert.strictEqual(stored.emailDelivery.status, "failed", "email delivery failure is persisted");
  assert.strictEqual(stored.emailDelivery.attempts, 1, "email delivery attempt count is persisted");
}

async function runCustomIdBindingFailureTest() {
  const stripe = { checkout: { sessions: { listLineItems: async () => ({ data: [] }) } } };
  await assert.rejects(
    handleWebhook({
      id: "evt_custom_id_unbound_test",
      type: "checkout.session.completed",
      data: { object: {
        id: "cs_custom_id_unbound_test",
        payment_status: "paid",
        metadata: { tier: "custom_id", product: "custom_id_standard", custom_id: "example", pending_token: "unknown" }
      } }
    }, stripe, []),
    /custom_id_payment_binding_failed/,
    "custom ID webhook rejects a payment that is not bound to the pending checkout"
  );
}

async function runCustomIdFinanceAndRefundTest() {
  const stripe = { checkout: { sessions: {
    listLineItems: async () => ({ data: [] }),
    list: async () => ({ data: [{
      id: "cs_custom_id_paid_test",
      amount_total: 100,
      currency: "eur",
      payment_status: "paid",
      metadata: { type: "custom_id", tier: "custom_id", product: "custom_id_standard", doc_type: "receipt", billing_country: "GR" }
    }] })
  } } };
  const paid = await handleWebhook({
    id: "evt_custom_id_paid_test",
    type: "checkout.session.completed",
    data: { object: {
      id: "cs_custom_id_paid_test",
      amount_total: 100,
      currency: "eur",
      payment_status: "paid",
      metadata: {
        type: "custom_id", tier: "custom_id", product: "custom_id_standard",
        custom_id: "example", pending_token: "custom_paid_token", doc_type: "receipt", billing_country: "GR"
      }
    } }
  }, stripe, []);
  assert.strictEqual(paid.tier, "custom_id");

  const refunded = await handleWebhook({
    id: "evt_custom_id_refund_test",
    type: "charge.refunded",
    data: { object: { amount: 100, amount_refunded: 100, payment_intent: "pi_custom_refund" } }
  }, stripe, []);
  assert.strictEqual(refunded.tier, "custom_id");
  assert.strictEqual(refunded.revoked.revokedPending, 1, "full refund removes pending Custom ID activation");
}

async function runActivationCodeRefundTest() {
  const activationCodes = [];
  let persistedActivations = 0;
  const deps = {
    persistActivationCodes: () => {
      persistedActivations += 1;
    },
  };
  const soldCodes = require("../payments/sold_codes");
  soldCodes.recordSale({
    code: "PRO-REFUND-TEST-01",
    tier: "pro",
    stripeSessionId: "cs_activation_refund_test",
    productKey: "securechat_pro_lifetime",
    activationCodesRef: activationCodes,
  });
  const stripe = { checkout: { sessions: {
    list: async () => ({ data: [{
      id: "cs_activation_refund_test",
      amount_total: 900,
      currency: "eur",
      payment_status: "paid",
      metadata: { type: "lifetime_dynamic", tier: "pro", product: "securechat_pro_lifetime", doc_type: "receipt", billing_country: "GR" },
    }] }),
    listLineItems: async () => ({ data: [] }),
  } } };

  const refunded = await handleWebhook({
    id: "evt_activation_refund_test",
    type: "charge.refunded",
    data: { object: { amount: 900, amount_refunded: 900, payment_intent: "pi_activation_refund" } },
  }, stripe, activationCodes, deps);

  assert.strictEqual(refunded.productKey, "securechat_pro_lifetime");
  assert.strictEqual(refunded.revoked.found, true, "full refund revokes a direct activation-code entitlement");
  assert.strictEqual(activationCodes.some(entry => entry.stripeSessionId === "cs_activation_refund_test"), false);
  assert.strictEqual(soldCodes.findByStripeSession("cs_activation_refund_test").revoked, true);
  assert.strictEqual(persistedActivations, 1, "full refund persists activation-code removal");

  activationCodes.push({
    code: "PRO-REFUND-TEST-01",
    tier: "pro",
    stripeSessionId: "cs_activation_refund_test",
    productKey: "securechat_pro_lifetime",
  });
  const duplicateRefund = await handleWebhook({
    id: "evt_activation_refund_retry_test",
    type: "charge.refunded",
    data: { object: { amount: 900, amount_refunded: 900, payment_intent: "pi_activation_refund" } },
  }, stripe, activationCodes, deps);
  assert.strictEqual(duplicateRefund.revoked.duplicate, true);
  assert.strictEqual(
    activationCodes.some(entry => entry.stripeSessionId === "cs_activation_refund_test"),
    false,
    "refund retry repairs a stale activation loaded after restart",
  );
  assert.strictEqual(persistedActivations, 2, "refund retry persists the repaired store");

  const lateCheckout = await handleWebhook({
    id: "evt_activation_late_checkout_test",
    type: "checkout.session.async_payment_succeeded",
    data: { object: (await stripe.checkout.sessions.list()).data[0] },
  }, stripe, activationCodes, deps);
  assert.strictEqual(lateCheckout.reason, "payment_reversed", "later checkout event cannot restore a revoked code");
  assert.strictEqual(activationCodes.some(entry => entry.stripeSessionId === "cs_activation_refund_test"), false);
}

async function runRefundBeforeCheckoutTest() {
  const activationCodes = [];
  const session = {
    id: "cs_refund_before_checkout_test",
    amount_total: 900,
    currency: "eur",
    payment_status: "paid",
    metadata: {
      type: "lifetime_dynamic",
      tier: "pro",
      product: "securechat_pro_lifetime",
      licenseTier: "securechat_pro_lifetime",
      doc_type: "receipt",
      billing_country: "GR",
    },
  };
  const stripe = { checkout: { sessions: {
    list: async () => ({ data: [session] }),
    listLineItems: async () => ({ data: [] }),
  } } };

  const refunded = await handleWebhook({
    id: "evt_refund_before_checkout_test",
    type: "charge.refunded",
    data: { object: { amount: 900, amount_refunded: 900, payment_intent: "pi_refund_before_checkout_test" } },
  }, stripe, activationCodes);
  assert.strictEqual(refunded.revoked.tombstoned, true, "refund before checkout fulfillment creates a tombstone");
  assert.strictEqual(refunded.productKey, "securechat_pro_lifetime", "refund before checkout keeps the direct entitlement product");

  const completed = await handleWebhook({
    id: "evt_late_checkout_after_refund_test",
    type: "checkout.session.completed",
    data: { object: session },
  }, stripe, activationCodes);
  assert.strictEqual(completed.reason, "payment_reversed", "late checkout cannot fulfill a reversed payment");
  assert.strictEqual(activationCodes.length, 0, "reversed payment never creates an activation code");
  const storedReversal = fs.readFileSync(process.env.SOLD_CODES_FILE, "utf8");
  assert.strictEqual(storedReversal.includes("pi_refund_before_checkout_test"), false, "payment intent is stored only as a hash");
}

runDynamicLifetimeWebhookTest()
  .then(runProductLifetimeWebhookTest)
  .then(runEmailDeliveryStatusTest)
  .then(runCustomIdBindingFailureTest)
  .then(runCustomIdFinanceAndRefundTest)
  .then(runActivationCodeRefundTest)
  .then(runRefundBeforeCheckoutTest)
  .then(() => console.log("stripe_handler.test.js ok"))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
