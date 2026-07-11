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

runDynamicLifetimeWebhookTest()
  .then(runProductLifetimeWebhookTest)
  .then(runEmailDeliveryStatusTest)
  .then(() => console.log("stripe_handler.test.js ok"))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
