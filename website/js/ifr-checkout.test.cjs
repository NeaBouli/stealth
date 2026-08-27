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
