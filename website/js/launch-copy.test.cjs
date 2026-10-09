'use strict';

const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const test = require('node:test');

const websiteRoot = join(__dirname, '..');
const read = (name) => readFileSync(join(websiteRoot, name), 'utf8');
const pages = { index: read('index.html'), download: read('download.html'), faq: read('faq.html') };
const PLAY_LISTING = 'https://play.google.com/store/apps/details?id=com.securecall.app.free';

test('launch copy makes no live-sales or unsubstantiated availability claims', () => {
    const forbidden = [
        /Always available/i,
        /\byearly\b/i,
        /(?:&euro;|€)\s?(?:34|49)\.99/,
        /currently listed at/i,
        /IFR holders can verify a browser wallet/i,
        /benefits are available as browser/i,
        /matching your purchased tier/i,
        /Upgrade for advanced protection/i,
        /One-time purchase, no subscription stack/i,
    ];
    for (const [name, html] of Object.entries(pages)) {
        for (const pattern of forbidden) {
            assert.doesNotMatch(html, pattern, `${name}.html contains ${pattern}`);
        }
    }
});

test('FAQ and download page state that paid sales are closed', () => {
    assert.ok((pages.faq.match(/sales are currently closed/gi) || []).length >= 4);
    assert.match(pages.download, /data-sales-closed[\s\S]*?purchases are currently closed/i);
});

test('download page links the verified Google Play listing', () => {
    assert.ok(pages.download.includes(`href="${PLAY_LISTING}"`));
});

test('every paid index price card is labelled Planned with a disabled CTA', () => {
    const cards = pages.index.match(/<article class="price-card[^"]*">[\s\S]*?<\/article>/g) || [];
    const paid = cards.filter((card) => !/<h3>Free<\/h3>/.test(card));
    assert.equal(paid.length, 5);
    for (const card of paid) {
        assert.match(card, /<span class="price-badge"[^>]*>Planned<\/span>/);
        assert.match(card, /Sales (?:are )?closed/);
        assert.match(card, /aria-disabled="true">Sales not yet available</);
        assert.doesNotMatch(card, /<a\b/);
    }
});

test('suite band has no dead-end pricing CTA', () => {
    const band = (pages.index.match(/<div class="suite-band[\s\S]*?<\/div>\s*<\/div>/) || [''])[0];
    assert.ok(band);
    assert.doesNotMatch(band, /href="#pricing"/);
    assert.match(band, /aria-disabled="true">Sales not yet available</);
});

test('IFR checkout stays closed and release labels are preserved', () => {
    assert.match(pages.index, /data-ifr-enabled="false"/);
    for (const control of pages.index.match(/<button[^>]*data-ifr-[^>]*>/g) || []) {
        assert.match(control, /\bdisabled\b/);
    }
    for (const name of ['index', 'download']) {
        assert.match(pages[name], /Published v1\.0\.48/);
        assert.match(pages[name], /v1\.0\.50 candidate|candidate v1\.0\.50/);
        assert.doesNotMatch(pages[name], /1\.0\.51/);
    }
});

test('FAQ emphasis on light answer cards uses a readable dark colour', () => {
    assert.match(pages.faq, /\.faq-refresh \.faq-answer-inner strong \{ color: #111827; \}/);
});

const aspidaCard = (pages.index.match(/<article class="product-card product-card-aspida[^"]*"[\s\S]*?<\/article>/) || [''])[0];

test('ASPIDA listing is informational, in development and planned-only', () => {
    assert.ok(aspidaCard, 'ASPIDA card missing');
    assert.match(aspidaCard, /<span class="tag">In development<\/span>/);
    assert.match(aspidaCard, /Planned: one license/);
    assert.match(aspidaCard, /Planned: sensor-privacy controls/);
    assert.match(aspidaCard, /manually in device or OEM developer settings, not by ASPIDA/);
    assert.match(aspidaCard, /<span class="btn line block" aria-disabled="true">Not available yet<\/span>/);
});

test('ASPIDA listing has no commerce, download, activation or unsupported promise', () => {
    assert.doesNotMatch(aspidaCard, /<(?:a|button|form|input)\b|\son[a-z]+=|href=|data-/i);
    assert.doesNotMatch(aspidaCard, /<img/i);
    assert.doesNotMatch(aspidaCard, /\b(?:buy|purchase|checkout|download|activate|activation|subscription|lifetime|tiers?|plans?|Suite|InStock|Offer)\b/i);
    assert.doesNotMatch(aspidaCard, /[€$£]\s?\d|\d\s?(?:EUR|USD)|\bdevices?\s+(?:count|limit)|\bper device\b/i);
    assert.doesNotMatch(aspidaCard, /\b(?:blocks? (?:all )?sensors|automatic(?:ally)?|master switch|guarantee[ds]?|all devices|every device)\b/i);
    assert.doesNotMatch(pages.index, /aspida\.license|aspida[^"\n]*(?:checkout|download|\.apk)/i);
    const jsonLd = (pages.index.match(/<script type="application\/ld\+json">[\s\S]*?<\/script>/g) || []).join('');
    assert.doesNotMatch(jsonLd, /aspida/i);
});
