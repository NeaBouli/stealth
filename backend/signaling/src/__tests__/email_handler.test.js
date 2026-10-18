const assert = require("assert");
const fs = require("fs");
const vm = require("vm");

// Isolate module-load configuration from the host's deployment overrides.
function loadEmailHandler(env = {}) {
  const context = { module: { exports: {} }, process: { env }, require };
  const source = fs.readFileSync(require.resolve("../payments/email_handler"), "utf8");
  vm.runInNewContext(source, context);
  return context.module.exports;
}

const {
  generateEmailHTML,
  resolveDownloadLinks
} = loadEmailHandler();

const premiumLinks = resolveDownloadLinks("premium");
assert.strictEqual(premiumLinks.primary, "https://github.com/NeaBouli/stealth/releases/latest/download/SecureCall-Premium-LATEST.apk");
assert.strictEqual(premiumLinks.armeabi, premiumLinks.primary, "Premium ARMv7 should use the same universal APK");

const premiumHtml = generateEmailHTML("PREM-ABCD-EFGH-IJKL", "premium");
assert.ok(premiumHtml.includes("Download Premium APK"), "premium email should show direct APK CTA");
assert.ok(premiumHtml.includes(premiumLinks.primary), "premium email should include direct Premium APK link");
assert.ok(!premiumHtml.includes("/releases/download/v1.0.35/"), "email must not link the retired release");

const proLinks = resolveDownloadLinks("pro");
assert.strictEqual(proLinks.primary, "https://github.com/NeaBouli/stealth/releases/latest/download/SecureCall-Pro-LATEST.apk");
assert.strictEqual(proLinks.armeabi, proLinks.primary, "Pro ARMv7 should use the same universal APK");

const proHtml = generateEmailHTML("PRO-ABCD-EFGH-IJKL", "pro");
assert.ok(proHtml.includes("Download Pro APK"), "pro email should show direct APK CTA");
assert.ok(proHtml.includes(proLinks.primary), "pro email should include direct Pro APK link");

const overrides = {
  SECURECALL_PRO_APK_URL: "https://downloads.example.invalid/pro.apk",
  SECURECALL_PRO_APK_ARMEABI_URL: "https://downloads.example.invalid/pro-armv7.apk",
  SECURECALL_PREMIUM_APK_URL: "https://downloads.example.invalid/premium.apk",
  SECURECALL_PREMIUM_APK_ARMEABI_URL: "https://downloads.example.invalid/premium-armv7.apk"
};
const configured = loadEmailHandler(overrides);
assert.strictEqual(configured.resolveDownloadLinks("pro").primary, overrides.SECURECALL_PRO_APK_URL);
assert.strictEqual(configured.resolveDownloadLinks("pro").armeabi, overrides.SECURECALL_PRO_APK_ARMEABI_URL);
assert.strictEqual(configured.resolveDownloadLinks("premium").primary, overrides.SECURECALL_PREMIUM_APK_URL);
assert.strictEqual(configured.resolveDownloadLinks("premium").armeabi, overrides.SECURECALL_PREMIUM_APK_ARMEABI_URL);
for (const tier of ["pro", "premium"]) {
  assert.ok(configured.generateEmailHTML("TEST-CODE", tier).includes(configured.resolveDownloadLinks(tier).primary),
    `${tier} email should use the configured primary APK link`);
}

const secureChatHtml = generateEmailHTML("ELIT-ABCD-EFGH-IJKL", "elite", {
  productName: "SecureChat",
  productUrl: "https://securechat.stealthx.tech/"
});
assert.ok(secureChatHtml.includes(">SecureChat<"), "SecureChat email should use product name");
assert.ok(secureChatHtml.includes("https://securechat.stealthx.tech/"), "SecureChat email should link product site");
assert.ok(!secureChatHtml.includes("Download Elite APK"), "SecureChat email must not show SecureCall APK CTA");
assert.ok(!secureChatHtml.includes("play.google.com/store/apps/details?id=com.securecall.app.free"), "SecureChat email must not link SecureCall Play listing");

console.log("email_handler.test.js ok");
