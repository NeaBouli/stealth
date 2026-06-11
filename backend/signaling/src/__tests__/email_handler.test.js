const assert = require("assert");
const {
  generateEmailHTML,
  resolveDownloadLinks
} = require("../payments/email_handler");

const premiumLinks = resolveDownloadLinks("premium");
assert.ok(premiumLinks.primary.endsWith("/app-premium-arm64-v8a-release.apk"), "premium link should point to uploaded Premium ARM64 APK");
assert.ok(premiumLinks.armeabi.endsWith("/app-premium-armeabi-v7a-release.apk"), "premium alternate link should point to uploaded Premium ARMv7 APK");

const premiumHtml = generateEmailHTML("PREM-ABCD-EFGH-IJKL", "premium");
assert.ok(premiumHtml.includes("Download Premium APK"), "premium email should show direct APK CTA");
assert.ok(premiumHtml.includes(premiumLinks.primary), "premium email should include direct Premium APK link");
assert.ok(!premiumHtml.includes("github.com/NeaBouli/stealth/releases/latest"), "email must not fall back to generic latest release link");

const proLinks = resolveDownloadLinks("pro");
assert.ok(proLinks.primary.endsWith("/app-pro-arm64-v8a-release.apk"), "pro link should point to uploaded Pro ARM64 APK");
assert.ok(proLinks.armeabi.endsWith("/app-pro-armeabi-v7a-release.apk"), "pro alternate link should point to uploaded Pro ARMv7 APK");

const proHtml = generateEmailHTML("PRO-ABCD-EFGH-IJKL", "pro");
assert.ok(proHtml.includes("Download Pro APK"), "pro email should show direct APK CTA");
assert.ok(proHtml.includes(proLinks.primary), "pro email should include direct Pro APK link");

const secureChatHtml = generateEmailHTML("ELIT-ABCD-EFGH-IJKL", "elite", {
  productName: "SecureChat",
  productUrl: "https://securechat.stealthx.tech/"
});
assert.ok(secureChatHtml.includes(">SecureChat<"), "SecureChat email should use product name");
assert.ok(secureChatHtml.includes("https://securechat.stealthx.tech/"), "SecureChat email should link product site");
assert.ok(!secureChatHtml.includes("Download Elite APK"), "SecureChat email must not show SecureCall APK CTA");
assert.ok(!secureChatHtml.includes("play.google.com/store/apps/details?id=com.securecall.app.free"), "SecureChat email must not link SecureCall Play listing");

console.log("email_handler.test.js ok");
