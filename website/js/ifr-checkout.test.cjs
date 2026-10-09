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

const ACCOUNT = "0x1111111111111111111111111111111111111111";
const tick = () => new Promise((resolve) => setImmediate(resolve));

function harness(enabled) {
  const handlers = new Map();
  const element = (extra = {}) => ({
    disabled: false,
    textContent: "",
    dataset: {},
    addEventListener(name, fn) { handlers.set(this, fn); },
    ...extra,
  });
  const connect = element({ textContent: "Connect wallet" });
  const disconnect = element();
  const address = element({ value: "" });
  const status = element();
  const tiers = ["pro_lifetime", "premium_lifetime"].map((tier) =>
    element({ dataset: { ifrTier: tier }, textContent: tier }));
  const root = {
    dataset: { ifrEnabled: enabled ? "true" : "false", ifrProduct: "SecureCall" },
    querySelector: (selector) => ({
      "[data-ifr-connect]": connect,
      "[data-ifr-disconnect]": disconnect,
      "[data-ifr-address]": address,
      "[data-ifr-status]": status,
    })[selector] || null,
    querySelectorAll: () => tiers,
  };
  const events = {};
  const wallet = {
    isMetaMask: true,
    requests: [],
    signGate: null,
    request(args) {
      this.requests.push(args.method);
      if (args.method === "eth_requestAccounts") return Promise.resolve([ACCOUNT]);
      if (args.method === "personal_sign") return this.signGate || Promise.resolve("0xsig");
      return Promise.resolve(null);
    },
    on(name, fn) { events[name] = fn; },
    removeListener(name, fn) { if (events[name] === fn) delete events[name]; },
  };
  const calls = [];
  let assigned = null;
  const sandbox = {
    document: { querySelector: () => root },
    window: {
      ethereum: wallet,
      setTimeout, clearTimeout,
      location: { assign: (url) => { assigned = url; } },
    },
    AbortController,
    fetch: (url) => {
      calls.push(url);
      const body = url.endsWith("/ifr-discount-challenge")
        ? { message: "msg", nonce: "n1" }
        : { url: "https://checkout.invalid/session" };
      return Promise.resolve({ ok: true, json: () => Promise.resolve(body) });
    },
  };
  const source = fs.readFileSync(path.join(__dirname, "ifr-checkout.js"), "utf8");
  vm.runInNewContext(source, sandbox);
  const click = async (el) => { handlers.get(el)(); await tick(); await tick(); };
  return { connect, disconnect, address, status, tiers, wallet, events, calls, click,
    assigned: () => assigned, sandbox };
}

test("enabled gate: connect usable, purchase actions locked until wallet connected", async () => {
  const h = harness(true);
  assert.equal(h.connect.disabled, false);
  assert.equal(h.disconnect.disabled, true);
  assert.ok(h.tiers.every((tier) => tier.disabled));
  assert.equal(h.calls.length, 0);
  assert.doesNotMatch(h.status.textContent, /currently disabled/);
  await h.click(h.connect);
  assert.equal(h.address.value, ACCOUNT);
  assert.equal(h.disconnect.disabled, false);
  assert.ok(h.tiers.every((tier) => !tier.disabled));
  await h.click(h.disconnect);
  assert.equal(h.address.value, "");
  assert.ok(h.tiers.every((tier) => tier.disabled));
  assert.equal(h.connect.disabled, false);
  assert.equal(h.disconnect.disabled, true);
  assert.equal(Object.keys(h.events).length, 0);
});

test("accountsChanged and chainChanged drop wallet state without reconnecting", async () => {
  for (const name of ["accountsChanged", "chainChanged", "disconnect"]) {
    const h = harness(true);
    await h.click(h.connect);
    const before = h.wallet.requests.length;
    h.events[name]([ "0x2222222222222222222222222222222222222222" ]);
    assert.equal(h.address.value, "", name);
    assert.ok(h.tiers.every((tier) => tier.disabled), name);
    assert.equal(h.disconnect.disabled, true, name);
    assert.equal(h.connect.disabled, false, name);
    assert.equal(h.wallet.requests.length, before, name);
    assert.equal(h.calls.length, 0, name);
    await h.click(h.tiers[0]);
    assert.equal(h.calls.length, 0, name);
  }
});

test("checkout locks all tier buttons while pending, then opens checkout", async () => {
  const h = harness(true);
  await h.click(h.connect);
  let release;
  h.wallet.signGate = new Promise((resolve) => { release = resolve; });
  await h.click(h.tiers[0]);
  assert.ok(h.tiers.every((tier) => tier.disabled));
  const before = h.calls.length;
  await h.click(h.tiers[1]);
  assert.equal(h.calls.length, before);
  release("0xsig");
  await tick(); await tick(); await tick();
  assert.equal(h.assigned(), "https://checkout.invalid/session");
});

test("stale signature result after account change is ignored", async () => {
  const h = harness(true);
  await h.click(h.connect);
  let release;
  h.wallet.signGate = new Promise((resolve) => { release = resolve; });
  await h.click(h.tiers[0]);
  h.events.accountsChanged([]);
  release("0xsig");
  await tick(); await tick(); await tick();
  assert.equal(h.calls.filter((url) => url.endsWith("/create-dynamic-checkout")).length, 0);
  assert.equal(h.assigned(), null);
  assert.ok(h.tiers.every((tier) => tier.disabled));
  assert.equal(h.address.value, "");
});
