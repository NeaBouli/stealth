"use strict";

const crypto = require("crypto");

function buildRecord(session, productId, kind = "invoice", eventType = "sale") {
  const metadata = session.metadata || {};
  const idPrefix = kind === "adjustment" ? "adj" : "inv";
  return {
    id: `${idPrefix}-${String(session.id).replace(/[^A-Za-z0-9_-]/g, "")}`,
    kind,
    source: "securecall",
    fields: {
      eventType,
      externalOrderId: session.id,
      productId,
      amountTotal: Number.isSafeInteger(session.amount_total) ? session.amount_total : 0,
      currency: String(session.currency || "eur").toUpperCase(),
      docType: metadata.doc_type === "invoice" ? "invoice" : "receipt",
      country: String(metadata.billing_country || session.customer_details?.address?.country || "").toUpperCase(),
      companyName: metadata.company_name || "",
      taxId: metadata.tax_id || "",
      customerEmail: metadata.customer_email || session.customer_details?.email || session.customer_email || "",
      paymentStatus: session.payment_status || "paid"
    }
  };
}

async function exportFinanceRecord(record, fetchImpl = global.fetch) {
  const url = String(process.env.VLABS_FINANCE_INGEST_URL || "").trim();
  const secret = String(process.env.VLABS_FINANCE_INGEST_SECRET || "").trim();
  if (!url || !secret) return { disabled: true };
  if (typeof fetchImpl !== "function") throw new Error("finance_export_fetch_unavailable");

  const body = JSON.stringify({ schema: "vlabs.finance.ingest.v1", source: "securecall", records: [record] });
  const timestamp = Date.now();
  const signature = crypto.createHmac("sha256", secret).update(`${timestamp}.${body}`).digest("hex");
  const response = await fetchImpl(url, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-vlabs-source": "securecall",
      "x-vlabs-timestamp": String(timestamp),
      "x-vlabs-signature": signature
    },
    body
  });
  if (!response.ok) throw new Error(`finance_export_failed_${response.status}`);
  return { disabled: false };
}

module.exports = { buildRecord, exportFinanceRecord };
