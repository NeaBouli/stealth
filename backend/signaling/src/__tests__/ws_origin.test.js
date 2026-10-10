"use strict";

// STX-15: the WebSocket origin check must not switch off when ALLOWED_ORIGINS is unset.
const assert = require("assert");
const { isWebSocketOriginAllowed, DEFAULT_ALLOWED_ORIGINS } = require("../middleware/cors");

const defaults = ["https://stealthx.tech", "https://securechat.stealthx.tech"];

// Native clients send no Origin.
assert.strictEqual(isWebSocketOriginAllowed(undefined, [], defaults), true);
assert.strictEqual(isWebSocketOriginAllowed("", [], defaults), true);

// Unset allowlist: defaults apply, foreign pages are rejected.
assert.strictEqual(isWebSocketOriginAllowed("https://stealthx.tech", [], defaults), true);
assert.strictEqual(isWebSocketOriginAllowed("https://evil.example", [], defaults), false);
assert.strictEqual(isWebSocketOriginAllowed("null", [], defaults), false);
assert.strictEqual(isWebSocketOriginAllowed("https://evil.example", undefined), false);
assert.ok(DEFAULT_ALLOWED_ORIGINS.length > 0);

// Configured allowlist replaces the defaults.
assert.strictEqual(isWebSocketOriginAllowed("https://ops.example", ["https://ops.example"], defaults), true);
assert.strictEqual(isWebSocketOriginAllowed("https://stealthx.tech", ["https://ops.example"], defaults), false);

console.log("ws_origin.test.js: PASS");
