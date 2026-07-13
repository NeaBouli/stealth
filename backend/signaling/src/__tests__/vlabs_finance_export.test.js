"use strict";

const assert = require("assert");
const crypto = require("crypto");
const { buildRecord, exportFinanceRecord } = require("../payments/vlabs_finance_export");

async function run() {
  const session = {
    id: "cs_test_custom_finance",
    amount_total: 500,
    currency: "eur",
    payment_status: "paid",
    customer_details: { email: "billing@example.test", address: { country: "GR" } },
    metadata: {
      doc_type: "invoice",
      billing_country: "GR",
      company_name: "Example SA",
      tax_id: "EL123456789",
      customer_email: "billing@example.test"
    }
  };
  const record = buildRecord(session, "custom_id_ultra");
  assert.strictEqual(record.kind, "invoice");
  assert.strictEqual(record.fields.amountMinor, 500);
  assert.strictEqual(record.fields.documentIntent, "invoice");
  assert.strictEqual(record.fields.taxIdRequired, true);
  assert.match(record.fields.paymentReferenceHash, /^[a-f0-9]{64}$/);
  const serialized = JSON.stringify(record);
  for (const forbidden of [session.id, "billing@example.test", "Example SA", "EL123456789"]) {
    assert.strictEqual(serialized.includes(forbidden), false, `record must not contain ${forbidden}`);
  }

  delete process.env.VLABS_FINANCE_INGEST_URL;
  delete process.env.VLABS_FINANCE_INGEST_ALLOWED_HOST;
  delete process.env.VLABS_FINANCE_INGEST_SECRET;
  assert.deepStrictEqual(await exportFinanceRecord(record), { disabled: true });

  process.env.VLABS_FINANCE_INGEST_URL = "https://vlabs.example.test/api/finance/ingest";
  process.env.VLABS_FINANCE_INGEST_ALLOWED_HOST = "vlabs.example.test";
  process.env.VLABS_FINANCE_INGEST_SECRET = "f".repeat(32);
  let request;
  const result = await exportFinanceRecord(record, async (url, options) => {
    request = { url, options };
    return {
      ok: true,
      status: 200,
      json: async () => ({ ok: true, acceptedIds: [record.id] }),
    };
  });
  assert.deepStrictEqual(result, { disabled: false });
  assert.strictEqual(request.options.headers["x-vlabs-source"], "securecall");
  const timestamp = request.options.headers["x-vlabs-timestamp"];
  const expected = crypto.createHmac("sha256", "f".repeat(32))
    .update(`${timestamp}.${request.options.body}`).digest("hex");
  assert.strictEqual(request.options.headers["x-vlabs-signature"], expected);

  await assert.rejects(exportFinanceRecord(record, async () => ({
    ok: true,
    status: 200,
    json: async () => ({ ok: true, acceptedIds: [] }),
  })), /finance_export_invalid_ack/);

  const unsafeUrls = [
    "http://vlabs.example.test/api/finance/ingest",
    "https://vlabs.example.test/wrong",
    "https://vlabs.example.test/api/finance/ingest?debug=1",
    "https://user:pass@vlabs.example.test/api/finance/ingest",
  ];
  for (const url of unsafeUrls) {
    process.env.VLABS_FINANCE_INGEST_URL = url;
    await assert.rejects(exportFinanceRecord(record, async () => {
      throw new Error("must not call");
    }), /finance_export_invalid_config/);
  }

  assert.throws(() => buildRecord({ ...session, amount_total: 0 }, "custom_id_ultra"), /invalid_amount/);
  assert.throws(() => buildRecord({ ...session, currency: "" }, "custom_id_ultra"), /invalid_currency/);
  assert.throws(() => buildRecord(session, "Custom ID"), /invalid_product/);
  assert.throws(() => buildRecord(session, "custom_id_ultra", "sale"), /invalid_kind/);
  assert.throws(() => buildRecord(session, "custom_id_ultra", "invoice", "checkout.session.completed"), /invalid_event_type/);
  process.env.VLABS_FINANCE_INGEST_URL = "not a url";
  await assert.rejects(exportFinanceRecord(record, async () => {
    throw new Error("must not call");
  }), /finance_export_invalid_config/);

  delete process.env.VLABS_FINANCE_INGEST_URL;
  delete process.env.VLABS_FINANCE_INGEST_ALLOWED_HOST;
  delete process.env.VLABS_FINANCE_INGEST_SECRET;
}

run().then(() => console.log("vlabs_finance_export.test.js ok")).catch(error => {
  console.error(error);
  process.exit(1);
});
