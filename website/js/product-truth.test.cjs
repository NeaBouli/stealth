'use strict';

const assert = require('node:assert/strict');
const { readFileSync, readdirSync } = require('node:fs');
const { join } = require('node:path');
const test = require('node:test');

const websiteRoot = join(__dirname, '..');
const read = (name) => readFileSync(join(websiteRoot, name), 'utf8');

const htmlPages = ['index.html', 'privacy.html', 'faq.html', 'security.html', 'disclaimer.html',
    'download.html', 'terms.html', 'impressum.html', 'llms.txt'];
const wikiPages = readdirSync(join(websiteRoot, 'wiki'))
    .filter((f) => f.endsWith('.html')).map((f) => `wiki/${f}`);
const allPages = [...htmlPages, ...wikiPages];

test('WEB-05/H-CRYPTO-07: no page markets the transport as SRTP', () => {
    for (const name of allPages) {
        assert.doesNotMatch(read(name), /SRTP/i, `${name} mentions SRTP`);
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
            assert.doesNotMatch(read(name), pattern, `${name} contains ${pattern}`);
        }
    }
});

test('WEB-02: every privacy.html language block states the hashed discovery flow', () => {
    const privacy = read('privacy.html');
    const langs = ['en', 'de', 'el', 'it', 'es', 'ru', 'zh'];
    for (const lang of langs) {
        const block = privacy.split(`class="lang-content" data-lang="${lang}"`)[1];
        assert.ok(block, `privacy.html missing ${lang} block`);
        const text = block.split('class="lang-content"')[0];
        assert.match(text, /SHA-256/, `privacy.html ${lang} block lacks the hashed-discovery statement`);
    }
});

test('WEB-03: wiki privacy page is a summary linking the canonical policy', () => {
    const wiki = read('wiki/privacy-policy.html');
    assert.ok(wiki.includes('href="../privacy.html"'));
    assert.doesNotMatch(wiki, /completely disabled at the build level/i);
    assert.doesNotMatch(wiki, /never uploaded/i);
    assert.doesNotMatch(wiki, /Last updated (April|June) 2026/);
});

test('WEB-04: crash-reporting copy matches the runtime consent model', () => {
    for (const name of allPages) {
        const html = read(name);
        assert.doesNotMatch(html, /build level/i, `${name} claims build-level disabling`);
        assert.doesNotMatch(html, /opt-in Crashlytics/i, `${name} claims opt-in Crashlytics`);
    }
});

test('WEB-08: impressum renders provider and address as text, no dead ODR link', () => {
    const html = read('impressum.html');
    assert.ok((html.match(/Vendetta Labs/g) || []).length >= 3);
    assert.ok(html.includes('Lakonikis 127, 24100 Kalamata'));
    assert.doesNotMatch(html, /<canvas id="owner/);
    assert.doesNotMatch(html, /ec\.europa\.eu\/consumers\/odr/);
});

test('WEB-09: privacy policy names the website third-party origins', () => {
    const privacy = read('privacy.html');
    assert.match(privacy, /<h2>This Website<\/h2>/);
    for (const origin of ['GitHub Pages', 'fonts.googleapis.com', 'cdn.jsdelivr.net', 'esm.sh']) {
        assert.ok(privacy.includes(origin), `privacy.html does not name ${origin}`);
    }
});

test('WEB-13: excluded pricing features carry a screen-reader prefix', () => {
    const items = read('index.html').match(/<li class="excluded">[\s\S]*?<\/li>/g) || [];
    assert.ok(items.length >= 4, 'expected excluded pricing items on index.html');
    for (const item of items) {
        assert.match(item, /<span class="sr-only">Not included: <\/span>/);
    }
});

test('WEB-01: Pages deploy includes hidden files and smoke-checks .well-known', () => {
    const wf = readFileSync(join(websiteRoot, '..', '.github', 'workflows', 'deploy-pages.yml'), 'utf8');
    assert.match(wf, /include-hidden-files:\s*true/);
    assert.match(wf, /\.well-known\/assetlinks\.json/);
    assert.match(wf, /\.well-known\/security\.txt/);
});
