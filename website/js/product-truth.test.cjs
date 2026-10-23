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
  assert.match(source, /Pro EUR 15\.00 incl\. VAT, or EUR 7\.50 incl\. VAT/);
  assert.match(source, /Premium EUR 25\.00 incl\. VAT, or EUR 12\.50 incl\. VAT/);
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

test("landing advertises only the Free offer while paid sales stay closed", () => {
  const landing = read("website/index.html");
  const structured = [...landing.matchAll(
    /<script\s+type="application\/ld\+json">([\s\S]*?)<\/script>/g,
  )].map(([, payload]) => JSON.parse(payload));

  for (const type of ["SoftwareApplication", "Product"]) {
    const offers = structured.find(item => item["@type"] === type).offers;
    assert.deepEqual(offers.map(offer => offer.name), ["Free"],
      `${type} must not advertise paid offers while sales are closed`);
    assert.equal(offers[0].price, "0");
    assert.equal(offers[0].priceCurrency, "EUR");
  }

  const cards = [...landing.matchAll(/<article class="price-card[^>]*>([\s\S]*?)<\/article>/g)]
    .map(([, card]) => card).filter(card => !card.includes("<h3>Free</h3>"));
  assert.equal(cards.length, 4);
  for (const card of cards) {
    assert.match(card, /incl\. VAT · one-time/);
    assert.match(card, /aria-disabled="true">Sales not yet available/);
    assert.doesNotMatch(card, /<a\b/);
  }
});

const htmlPages = ['index.html', 'privacy.html', 'faq.html', 'security.html', 'disclaimer.html',
    'download.html', 'terms.html', 'impressum.html', 'llms.txt'];
const wikiPages = fs.readdirSync(path.join(repositoryRoot, 'website', 'wiki'))
    .filter((f) => f.endsWith('.html')).map((f) => `wiki/${f}`);
const allPages = [...htmlPages, ...wikiPages];
const readWebsite = (name) => read(`website/${name}`);

test('WEB-05/H-CRYPTO-07: no page markets the transport as SRTP', () => {
    for (const name of allPages) {
        assert.doesNotMatch(readWebsite(name), /SRTP/i, `${name} mentions SRTP`);
    }
});

test('WEB-02: contact-data claims match the implemented flows', () => {
    const forbidden = [
        /no contact uploads/i,
        /contacts?[^.]{0,160}never uploaded/i,
        /contact data is stored exclusively on your device/i,
        /Verlassen niemals Ihr Gerät/i,
        /Non lasciano mai il vostro dispositivo/i,
        /Nunca salen de su dispositivo/i,
    ];
    for (const name of allPages) {
        for (const pattern of forbidden) {
            assert.doesNotMatch(readWebsite(name), pattern, `${name} contains ${pattern}`);
        }
    }
});

test('WEB-02: every privacy.html language block states the hashed discovery flow', () => {
    const privacy = readWebsite('privacy.html');
    const langs = ['en', 'de', 'el', 'it', 'es', 'ru', 'zh'];
    for (const lang of langs) {
        const block = privacy.split(`class="lang-content" data-lang="${lang}"`)[1];
        assert.ok(block, `privacy.html missing ${lang} block`);
        const text = block.split('class="lang-content"')[0];
        assert.match(text, /SHA-256/, `privacy.html ${lang} block lacks the hashed-discovery statement`);
    }
});

test('WEB-03: wiki privacy page is a summary linking the canonical policy', () => {
    const wiki = readWebsite('wiki/privacy-policy.html');
    assert.ok(wiki.includes('href="../privacy.html"'));
    assert.doesNotMatch(wiki, /completely disabled at the build level/i);
    assert.doesNotMatch(wiki, /never uploaded/i);
    assert.doesNotMatch(wiki, /Last updated (April|June) 2026/);
});

test('WEB-04: crash-reporting copy matches the runtime consent model', () => {
    for (const name of allPages) {
        const html = readWebsite(name);
        assert.doesNotMatch(html, /build level/i, `${name} claims build-level disabling`);
        assert.doesNotMatch(html, /opt-in Crashlytics/i, `${name} claims opt-in Crashlytics`);
    }
});

test('WEB-08: impressum renders provider and address as text, no dead ODR link', () => {
    const html = readWebsite('impressum.html');
    assert.ok((html.match(/Vendetta Labs/g) || []).length >= 3);
    assert.ok(html.includes('Lakonikis 127, 24100 Kalamata'));
    assert.doesNotMatch(html, /<canvas id="owner/);
    assert.doesNotMatch(html, /ec\.europa\.eu\/consumers\/odr/);
});

test('WEB-09: privacy policy names the website third-party origins', () => {
    const privacy = readWebsite('privacy.html');
    assert.match(privacy, /<h2>This Website<\/h2>/);
    for (const origin of ['GitHub Pages', 'fonts.googleapis.com', 'cdn.jsdelivr.net', 'esm.sh']) {
        assert.ok(privacy.includes(origin), `privacy.html does not name ${origin}`);
    }
});

test('WEB-13: excluded pricing features carry a screen-reader prefix', () => {
    const items = readWebsite('index.html').match(/<li class="excluded">[\s\S]*?<\/li>/g) || [];
    assert.ok(items.length >= 4, 'expected excluded pricing items on index.html');
    for (const item of items) {
        assert.match(item, /<span class="sr-only">Not included: <\/span>/);
    }
    const css = readWebsite('css/landing.css');
    assert.match(css, /\.sr-only\s*\{[^}]*position:\s*absolute;[^}]*clip:\s*rect\(0, 0, 0, 0\)/);
});

test('WEB-01: Pages deploy includes hidden files and smoke-checks .well-known', () => {
    const wf = read(".github/workflows/deploy-pages.yml");
    assert.match(wf, /include-hidden-files:\s*true/);
    assert.match(wf, /\.well-known\/assetlinks\.json/);
    assert.match(wf, /\.well-known\/security\.txt/);
    assert.ok(JSON.parse(readWebsite('.well-known/assetlinks.json')).length > 0);
    assert.match(readWebsite('.well-known/security.txt'), /Canonical: https:\/\/stealthx\.tech\/\.well-known\/security\.txt/);
});

test('media copy describes application encryption over the DataChannel', () => {
    for (const name of ['index.html', 'security.html', 'wiki/user-manual.html']) {
        assert.match(readWebsite(name), /WebRTC DataChannel/);
        assert.match(readWebsite(name), /XChaCha20-Poly1305-encrypted Opus/);
    }
});

test('current direct release and Free-only Play distribution survive the port', () => {
    assert.match(readWebsite('download.html'), /v1\.0\.51 \(versionCode 78019\)/);
    assert.match(readWebsite('wiki/user-manual.html'), /Published APK 1\.0\.51 \(78019\)/);
    for (const name of ['index.html', 'download.html', 'faq.html', 'wiki/faq.html']) {
        const html = readWebsite(name);
        assert.match(html, /Google Play distributes (?:the Free edition only|only the Free edition)/);
        assert.match(html, /one-time lifetime purchases through the VLABS shop/);
        assert.doesNotMatch(html, /Google Play Billing|Play billing|\/(?:month|year)\b/i);
    }
});

test('current Google Play version claims match the Free production release', () => {
    assert.match(readWebsite('index.html'), /"softwareVersion": "1\.0\.51-free"/);
    for (const name of ['index.html', 'download.html', 'llms.txt', 'wiki/index.html', 'wiki/user-manual.html']) {
        const versions = [...readWebsite(name).matchAll(/Google Play v(\d+\.\d+\.\d+)/g)]
            .map(([, version]) => version);
        assert.ok(versions.length > 0, `${name} is missing the current Google Play version`);
        assert.ok(versions.every(version => version === '1.0.51'), `${name} has a stale Google Play version`);
    }
    const llms = readWebsite('llms.txt');
    assert.match(llms, /Google Play v1\.0\.51; package com\.securecall\.app\.free/);
    assert.match(llms, /Google Play v1\.0\.51 \(versionCode 78019\) live in production since 2026-10-23\./);
});

test('offline lifetime licence FAQ preserves the 30-day proof and 7-day recovery', () => {
    const faq = readWebsite('index.html').match(
        /Does my Pro or Premium licence work offline\?[\s\S]*?<div class="faq-answer-inner">([\s\S]*?)<\/div>/,
    );
    assert.ok(faq, 'offline licence FAQ is missing');
    assert.match(faq[1], /licence stays valid for life/);
    assert.match(faq[1], /more than 30 days/);
    assert.match(faq[1], /within the next 7 days/);
    assert.match(faq[1], /enter your activation code again/);
    assert.match(faq[1], /does not use up an extra device slot/);
});
