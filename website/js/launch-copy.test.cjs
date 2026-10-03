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
