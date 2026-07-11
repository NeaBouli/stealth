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
  assert.strictEqual(record.fields.amountTotal, 500);
  assert.strictEqual(record.fields.docType, "invoice");
  assert.strictEqual(record.fields.taxId, "EL123456789");

  delete process.env.VLABS_FINANCE_INGEST_URL;
  delete process.env.VLABS_FINANCE_INGEST_SECRET;
  assert.deepStrictEqual(await exportFinanceRecord(record), { disabled: true });

  process.env.VLABS_FINANCE_INGEST_URL = "https://vlabs.example.test/api/finance/ingest";
  process.env.VLABS_FINANCE_INGEST_SECRET = "test-secret";
  let request;
  const result = await exportFinanceRecord(record, async (url, options) => {
    request = { url, options };
    return { ok: true, status: 200 };
  });
  assert.deepStrictEqual(result, { disabled: false });
  assert.strictEqual(request.options.headers["x-vlabs-source"], "securecall");
  const timestamp = request.options.headers["x-vlabs-timestamp"];
  const expected = crypto.createHmac("sha256", "test-secret")
    .update(`${timestamp}.${request.options.body}`).digest("hex");
  assert.strictEqual(request.options.headers["x-vlabs-signature"], expected);

  delete process.env.VLABS_FINANCE_INGEST_URL;
  delete process.env.VLABS_FINANCE_INGEST_SECRET;
}

run().then(() => console.log("vlabs_finance_export.test.js ok"));
