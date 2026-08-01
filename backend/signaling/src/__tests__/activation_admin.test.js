"use strict";

const assert = require("assert");
const { setupActivationAdminRoutes } = require("../services/activation_admin");

function response() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; },
  };
}

let registered;
const app = {
  post(path, ...handlers) { registered = { path, handlers }; },
};
const requireAdmin = () => {};
let nextResult = { success: true };
let receivedCode;
setupActivationAdminRoutes(app, requireAdmin, code => {
  receivedCode = code;
  return nextResult;
});

assert.strictEqual(registered.path, "/admin/activation-codes/revoke");
assert.strictEqual(registered.handlers[0], requireAdmin, "admin authentication gates the revoke handler");
const handler = registered.handlers[1];

let res = response();
handler({ body: { code: "ELIT-REVIEW-TEST-0001" } }, res);
assert.strictEqual(receivedCode, "ELIT-REVIEW-TEST-0001");
assert.deepStrictEqual(res.body, { ok: true }, "successful response does not echo credential material");

for (const [error, statusCode] of [["invalid_code", 400], ["not_found", 404], ["persistence_failed", 503]]) {
  nextResult = { success: false, error };
  res = response();
  handler({ body: { code: "ELIT-REVIEW-TEST-0001" } }, res);
  assert.strictEqual(res.statusCode, statusCode);
  assert.deepStrictEqual(res.body, { error });
}

console.log("activation_admin.test.js ok");
