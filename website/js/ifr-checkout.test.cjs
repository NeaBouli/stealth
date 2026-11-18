const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

test("closed checkout gate binds no handlers or network path", () => {
  const listeners = [];
  const button = () => ({
    disabled: false,
    addEventListener: (...args) => listeners.push(args),
  });
  const connect = button();
  const disconnect = button();
  const tiers = [button(), button()];
  const status = { textContent: "" };
  const root = {
    dataset: { ifrEnabled: "false", ifrProduct: "SecureCall" },
    querySelector: (selector) => ({
      "[data-ifr-connect]": connect,
      "[data-ifr-disconnect]": disconnect,
      "[data-ifr-address]": { value: "" },
      "[data-ifr-status]": status,
    })[selector] || null,
    querySelectorAll: () => tiers,
  };
  let fetchCount = 0;
  const source = fs.readFileSync(path.join(__dirname, "ifr-checkout.js"), "utf8");

  vm.runInNewContext(source, {
    document: { querySelector: () => root },
    fetch: () => { fetchCount += 1; },
  });

  assert.equal(listeners.length, 0);
  assert.equal(fetchCount, 0);
  assert.equal(connect.disabled, true);
  assert.equal(disconnect.disabled, true);
  assert.ok(tiers.every((tier) => tier.disabled));
  assert.match(status.textContent, /currently disabled/);
});

// Enabled gate (synthetic): wallet proof -> eligibility -> central shop handoff in the
// URL fragment only. No network: fetch, wallet and navigation are fakes.
const SHOP = "https://vlabs.gr/en/shop?focus=security";
const WALLET = "0x" + "12".repeat(20);
const ELIGIBILITY = Object.freeze({
  source: "stealthx", keyId: "sx-ifr-fixture-1", timestamp: "1800000000",
  signature: "a".repeat(64), payload: Buffer.from(JSON.stringify({ schema: "fixture" })).toString("base64url"),
});

function enabledHarness({ responses, provider = "ok" }) {
  const handlers = {};
  const button = (name) => ({
    disabled: true, textContent: name, dataset: {},
    addEventListener: (type, handler) => { handlers[name] = handler; },
  });
  const connect = button("connect");
  const disconnect = button("disconnect");
  const pro = button("pro");
  pro.dataset.ifrTier = "pro_lifetime";
  const status = { textContent: "" };
  const root = {
    dataset: { ifrEnabled: "true", ifrProduct: "SecureCall" },
    querySelector: (selector) => ({
      "[data-ifr-connect]": connect,
      "[data-ifr-disconnect]": disconnect,
      "[data-ifr-address]": { value: "" },
      "[data-ifr-status]": status,
    })[selector] || null,
    querySelectorAll: () => [pro],
  };
  const requests = [];
  const navigations = [];
  const queue = responses.slice();
  const ethereum = provider === "none" ? undefined : {
    isMetaMask: true,
    request: async ({ method }) => {
      if (method === "eth_requestAccounts") return [WALLET];
      if (method === "wallet_switchEthereumChain") return null;
      if (method === "personal_sign") {
        if (provider === "reject") { const error = new Error("User rejected"); error.code = 4001; throw error; }
        return "0x" + "1".repeat(130);
      }
      throw new Error("unexpected " + method);
    },
  };
  const window = {
    ethereum,
    location: { origin: "https://stealthx.tech", assign: (url) => navigations.push(url) },
    setTimeout, clearTimeout,
  };
  const source = fs.readFileSync(path.join(__dirname, "ifr-checkout.js"), "utf8");
  vm.runInNewContext(source, {
    document: { querySelector: () => root },
    window, AbortController, TextEncoder, btoa, JSON, Promise, Error, String, Array,
    fetch: async (url, options) => {
      requests.push({ url, options, body: JSON.parse(options.body) });
      const next = queue.shift();
      return { ok: next.status < 400, status: next.status, json: async () => next.body };
    },
  });
  const settle = () => new Promise((resolve) => setImmediate(resolve));
  return {
    status, connect, disconnect, pro, requests, navigations,
    async buyPro(clicks = 1) {
      for (let i = 0; i < clicks; i += 1) handlers.pro();
      for (let i = 0; i < 20; i += 1) await settle();
    },
  };
}

test("enabled flow: the signed eligibility reaches the central shop only in the URL fragment", async () => {
  const harness = enabledHarness({ responses: [
    { status: 200, body: { nonce: "n".repeat(64), message: "fixture message", expiresAt: "x" } },
    { status: 200, body: { eligibility: ELIGIBILITY, expiresAt: 1800000300 } },
  ] });
  assert.equal(harness.connect.disabled, false, "enabled mode unlocks initially disabled HTML controls");
  assert.equal(harness.pro.disabled, false);
  assert.equal(harness.disconnect.disabled, true, "disconnect stays disabled without a wallet");
  await harness.buyPro(2);
  assert.deepEqual(harness.requests.map((r) => r.url), [
    "https://api.stealthx.tech/vlabs/ifr-eligibility/challenge",
    "https://api.stealthx.tech/vlabs/ifr-eligibility",
  ]);
  assert.deepEqual(harness.requests[0].body, { productId: "stealthx-securecall-pro-lifetime", walletAddress: WALLET });
  assert.equal(harness.requests[1].body.nonce, "n".repeat(64));
  assert.ok(harness.requests.every((r) => r.options.credentials === "omit"));
  assert.equal(harness.navigations.length, 1);
  const target = new URL(harness.navigations[0]);
  assert.equal(target.origin + target.pathname + target.search, SHOP, "path and query carry no eligibility");
  assert.ok(target.hash.startsWith("#stealthx-ifr="));
  const carried = JSON.parse(Buffer.from(target.hash.slice("#stealthx-ifr=".length), "base64url").toString("utf8"));
  assert.deepEqual(carried, ELIGIBILITY, "carried exactly");
  assert.ok(!harness.navigations[0].includes("stripe"), "no legacy checkout route");
});

for (const [name, setup, expected] of [
  ["rejected signature", { provider: "reject", responses: [{ status: 200, body: { nonce: "n".repeat(64), message: "m" } }] }, /Signature rejected/],
  ["zero balance", { responses: [{ status: 200, body: { nonce: "n".repeat(64), message: "m" } }, { status: 403, body: { error: "ifr_not_eligible" } }] }, /does not currently hold IFR/],
  ["provider unavailable", { responses: [{ status: 200, body: { nonce: "n".repeat(64), message: "m" } }, { status: 503, body: { error: "ifr_provider_unavailable" } }] }, /balance check is unavailable/],
  ["configuration closed", { responses: [{ status: 503, body: { error: "ifr_eligibility_unavailable" } }] }, /currently unavailable/],
  ["missing wallet", { provider: "none", responses: [] }, /No browser wallet is available/],
]) {
  test(`enabled flow: ${name} shows an explicit message and never navigates`, async () => {
    const harness = enabledHarness(setup);
    await harness.buyPro();
    assert.equal(harness.navigations.length, 0);
    assert.match(harness.status.textContent, expected);
    assert.equal(harness.pro.disabled, false, "the user can retry");
  });
}
