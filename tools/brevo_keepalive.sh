#!/usr/bin/env bash
set -euo pipefail

if [ -z "${BREVO_API_KEY:-}" ]; then
  echo "BREVO_API_KEY is required." >&2
  exit 2
fi

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

http_code="$(
  curl --silent --show-error \
    --output "$response_file" \
    --write-out "%{http_code}" \
    "https://api.brevo.com/v3/account" \
    --header "accept: application/json" \
    --header "api-key: ${BREVO_API_KEY}"
)"

if [ "$http_code" = "200" ]; then
  echo "Brevo keepalive probe succeeded (HTTP 200)."
  exit 0
fi

echo "Brevo keepalive probe failed (HTTP ${http_code})." >&2

node - "$response_file" <<'NODE' || true
const fs = require("fs");
const file = process.argv[2];

try {
  const raw = fs.readFileSync(file, "utf8");
  const body = JSON.parse(raw);
  const summary = {};

  for (const key of ["code", "message"]) {
    if (body[key]) summary[key] = body[key];
  }

  if (Object.keys(summary).length > 0) {
    console.error(`Brevo error summary: ${JSON.stringify(summary)}`);
  }
} catch {
  // Do not print arbitrary response bodies because they can contain account data.
}
NODE

exit 1
