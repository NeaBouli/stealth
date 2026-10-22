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

const offlineLicenceEn = "Your licence is valid for life. The app confirms it automatically whenever the device is online (no login needed). After more than 30 days offline it temporarily runs as the free version; if the device is back online within a further 7 days, Pro/Premium returns automatically. After that, simply enter the same code again &ndash; on the same device this uses no extra device slot and needs no new purchase.";
const offlineLicenceDe = "Die Lizenz gilt dauerhaft. Die App bestätigt sie automatisch, sobald das Gerät online ist (kein Login nötig). Nach mehr als 30 Tagen offline läuft sie vorübergehend als Gratis-Version; ist das Gerät innerhalb weiterer 7 Tage wieder online, ist Pro/Premium automatisch zurück. Danach einfach denselben Code erneut eingeben &ndash; auf demselben Gerät ohne zusätzlichen Geräteplatz und ohne neuen Kauf.";
for (const [tier, html] of [["pro", proHtml], ["premium", premiumHtml]]) {
  const paragraphs = [...html.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/g)].map(match => match[1]);
  assert.ok(paragraphs.includes(offlineLicenceEn), `${tier} email should include the English offline licence paragraph`);
  assert.ok(paragraphs.includes(offlineLicenceDe), `${tier} email should include the German offline licence paragraph`);
  assert.strictEqual(paragraphs.indexOf(offlineLicenceDe), paragraphs.indexOf(offlineLicenceEn) + 1,
    `${tier} email should place the German offline licence paragraph immediately after the English paragraph`);
}

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
assert.ok(!secureChatHtml.includes(offlineLicenceEn), "SecureChat email must not include the English SecureCall offline licence paragraph");
assert.ok(!secureChatHtml.includes(offlineLicenceDe), "SecureChat email must not include the German SecureCall offline licence paragraph");

console.log("email_handler.test.js ok");
