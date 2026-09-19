const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const repositoryRoot = path.resolve(__dirname, "..", "..");

const currentProductSurfaces = [
  "README.md",
  "docs/ARCHITECTURE_OVERVIEW.md",
  "docs/FINAL_QA_CHECKLIST.md",
  "docs/GITHUB_RELEASES.md",
  "docs/GOOGLE_PLAY_BILLING_SETUP.md",
  "docs/Matrix-Integration.md",
  "docs/PLAY_STORE_UPLOAD_CHECKLIST.md",
  "docs/PRICING.md",
  "docs/PRIVACY_POLICY.md",
  "docs/RELAY_ARCHITECTURE.md",
  "docs/SECURITY_DESIGN.md",
  "docs/WIKI/Encryption-Architecture.md",
  "docs/WIKI/FAQ.md",
  "docs/WIKI/Roadmap.md",
  "docs/WIKI/Security-Design.md",
  "docs/WIKI/User-Manual.md",
  "docs/user-manual.md",
  "fastlane/metadata/android/en-US/full_description.txt",
  "fastlane/metadata/android/en-US/short_description.txt",
  "marketing/launch_plan.md",
  "marketing/play_store_de.txt",
  "marketing/play_store/de/store_listing.md",
  "marketing/play_store/de/full_description.txt",
  "marketing/play_store/de/release_notes.txt",
  "marketing/play_store/en/full_description.txt",
  "marketing/play_store/en/release_notes.txt",
  "marketing/play_store/en/short_description.txt",
  "website/assets/og-image.svg",
  "website/faq.html",
  "website/disclaimer.html",
  "website/ifr.html",
  "website/index.html",
  "website/llms.txt",
  "website/privacy.html",
  "website/return.html",
  "website/return/index.html",
  "website/return/chameleon/index.html",
  "website/return/securechat/index.html",
  "website/security.html",
  "website/siwe.html",
  "website/terms.html",
  "website/wiki/faq.html",
  "website/wiki/architecture.html",
  "website/wiki/getting-started.html",
  "website/wiki/ifr-unlock.html",
  "website/wiki/index.html",
  "website/wiki/matrix-integration.html",
  "website/wiki/privacy-policy.html",
  "website/wiki/roadmap.html",
  "website/wiki/security-design.html",
  "website/wiki/user-manual.html",
];

const retiredClaims = [
  /double[ -]ratchet protocol for perfect forward secrecy/i,
  /x25519 key exchange with double ratchet/i,
  /forward secrecy:\s*double ratchet/i,
  /ghostnet[ -]relay[ -](?:network|netzwerk)\s*\(premium\)/i,
  /military-grade/i,
  /intelligence agencies cannot break/i,
  /encrypted calls\. zero metadata/i,
  /with zero metadata(?: collection)?/i,
  /limited time offer/i,
  /zeitlich begrenztes angebot/i,
  /100 (?:available|verfuegbar|verfügbar)/i,
  /price increases automatically/i,
  /der preis steigt automatisch/i,
  /next buyer pays/i,
  /€\s*49(?:\D|$)/i,
  /€\s*3[.,]49/i,
  /€\s*4[.,]99/i,
  /always available, no license limits/i,
  /then complete discounted stripe checkout/i,
  /verified against live checkout sessions/i,
  /walletconnect v2 for ifr token verification/i,
  /unlock with activation code or ifr token lock/i,
  /locking ifr tokens via walletconnect/i,
  /1[.,]000 ifr\s*=\s*pro/i,
  /5[.,]000 ifr\s*=\s*premium/i,
];

function read(relativePath) {
  return fs.readFileSync(path.join(repositoryRoot, relativePath), "utf8");
}

test("current SecureCall surfaces contain no retired security, scarcity, or app-wallet claims", () => {
  for (const relativePath of currentProductSurfaces) {
    const source = read(relativePath);
    for (const claim of retiredClaims) {
      assert.doesNotMatch(source, claim, `${relativePath} contains retired claim ${claim}`);
    }
  }
});

test("browser-only IFR surface matches the accepted closed-gate VLABS offer", () => {
  const source = read("website/ifr.html");

  assert.match(source, /data-ifr-enabled="false"/);
  assert.match(source, /data-ifr-connect disabled/);
  assert.match(source, /data-ifr-disconnect disabled/);
  assert.match(source, /Pro EUR 15\.00, or EUR 7\.50/);
  assert.match(source, /Premium EUR 25\.00, or EUR 12\.50/);
  assert.match(source, /Any positive verified IFR balance qualifies/);
  assert.match(source, /no token threshold or lifetime redemption cap/);
  assert.match(source, /never enters the Android app/);
  assert.match(source, /No payment can be made on this page/);
});

test("landing and pricing record the same closed direct-channel prices", () => {
  const landing = read("website/index.html");
  const pricing = read("docs/PRICING.md");

  for (const amount of ["€15", "€25", "€7.50", "€12.50"]) {
    assert.ok(
      landing.includes(amount) || landing.includes(amount.replace("€", "&euro;")),
      `landing is missing ${amount}`,
    );
    assert.ok(pricing.includes(amount), `pricing is missing ${amount}`);
  }

  assert.match(landing, /Sales not yet available/);
  assert.match(pricing, /PRODUCT_READY/);
  assert.match(pricing, /FINANCE_READY/);
});

test("edited landing JSON-LD remains valid", () => {
  const landing = read("website/index.html");
  const blocks = [...landing.matchAll(
    /<script\s+type="application\/ld\+json">([\s\S]*?)<\/script>/g,
  )];

  assert.ok(blocks.length > 0, "landing has no JSON-LD block");
  for (const [, payload] of blocks) JSON.parse(payload);
});
