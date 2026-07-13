"use strict";

const crypto = require("crypto");

function hashReference(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function cleanProductId(value) {
  const productId = String(value || "").trim();
  if (!/^[a-z0-9_-]{1,100}$/.test(productId)) throw new Error("finance_export_invalid_product");
  return productId;
}

function positiveAmount(value) {
  if (!Number.isSafeInteger(value) || value <= 0 || value > 100_000_000) {
    throw new Error("finance_export_invalid_amount");
  }
  return value;
}

function currencyCode(value) {
  const currency = String(value || "").trim().toUpperCase();
  if (!/^[A-Z]{3}$/.test(currency)) throw new Error("finance_export_invalid_currency");
  return currency;
}

function billingCountry(session) {
  const country = String(session.metadata?.billing_country || session.customer_details?.address?.country || "")
    .trim().toUpperCase();
  return /^[A-Z]{2}$/.test(country) ? country : null;
}

function buildRecord(session, productId, kind = "invoice", eventType = "sale") {
  if (!session || typeof session.id !== "string" || !/^cs_[A-Za-z0-9_]+$/.test(session.id)) {
    throw new Error("finance_export_invalid_payment_reference");
  }
  if (kind !== "invoice" && kind !== "adjustment") throw new Error("finance_export_invalid_kind");
  if (!/^[a-z_]{1,40}$/.test(eventType)) throw new Error("finance_export_invalid_event_type");
  const metadata = session.metadata || {};
  const referenceHash = hashReference(session.id);
  const idPrefix = kind === "adjustment" ? `adj-${eventType}` : "inv";
  return {
    id: `${idPrefix}-${referenceHash.slice(0, 32)}`,
    kind,
    source: "securecall",
    fields: {
      processor: "stripe",
      eventType,
      productId: cleanProductId(productId),
      amountMinor: positiveAmount(session.amount_total),
      currency: currencyCode(session.currency),
      considerationProvided: true,
      requiresManualReview: true,
      documentIntent: metadata.doc_type === "invoice" ? "invoice" : "receipt",
      billingCountry: billingCountry(session),
      billingCustomerType: metadata.doc_type === "invoice" ? "company" : "private",
      taxIdRequired: metadata.doc_type === "invoice",
      paymentState: kind === "invoice" ? "paid" : "reversal_reported",
      paymentReferenceHash: referenceHash,
    }
  };
}

function readConfig() {
  const rawUrl = String(process.env.VLABS_FINANCE_INGEST_URL || "").trim();
  const allowedHost = String(process.env.VLABS_FINANCE_INGEST_ALLOWED_HOST || "").trim().toLowerCase();
  const secret = String(process.env.VLABS_FINANCE_INGEST_SECRET || "").trim();
  if (!rawUrl && !allowedHost && !secret) return null;
  if (!rawUrl || !allowedHost || secret.length < 32) throw new Error("finance_export_invalid_config");

  let url;
  try {
    url = new URL(rawUrl);
  } catch {
    throw new Error("finance_export_invalid_config");
  }
  if (url.protocol !== "https:" || url.hostname.toLowerCase() !== allowedHost
      || url.pathname !== "/api/finance/ingest" || url.port || url.username || url.password
      || url.search || url.hash) {
    throw new Error("finance_export_invalid_config");
  }
  return { url: url.toString(), secret };
}

async function exportFinanceRecord(record, fetchImpl = global.fetch) {
  const config = readConfig();
  if (!config) return { disabled: true };
  if (typeof fetchImpl !== "function") throw new Error("finance_export_fetch_unavailable");

  const body = JSON.stringify({ schema: "vlabs.finance.ingest.v1", source: "securecall", records: [record] });
  const timestamp = Date.now();
  const signature = crypto.createHmac("sha256", config.secret).update(`${timestamp}.${body}`).digest("hex");
  const response = await fetchImpl(config.url, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-vlabs-source": "securecall",
      "x-vlabs-timestamp": String(timestamp),
      "x-vlabs-signature": signature
    },
    body,
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) throw new Error(`finance_export_failed_${response.status}`);
  const ack = await response.json().catch(() => null);
  if (!ack || ack.ok !== true || !Array.isArray(ack.acceptedIds)
      || ack.acceptedIds.length !== 1 || ack.acceptedIds[0] !== record.id) {
    throw new Error("finance_export_invalid_ack");
  }
  return { disabled: false };
}

module.exports = { buildRecord, exportFinanceRecord, hashReference, readConfig };
