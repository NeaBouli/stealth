'use strict';

const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const html = readFileSync(join(__dirname, '..', 'index.html'), 'utf8');

function sectionById(id) {
    const section = html.match(new RegExp(`<section\\b[^>]*\\bid="${id}"[^>]*>[\\s\\S]*?</section>`));
    assert.ok(section, `#${id} missing`);
    return section[0];
}

test('bLocX OS card is a planned, non-purchase status', () => {
    const cards = [...html.matchAll(/<article\b[^>]*\bid="blocx"[^>]*>[\s\S]*?<\/article>/g)];
    assert.equal(cards.length, 1, 'exactly one #blocx card is required');
    const card = cards[0][0];
    assert.match(card, /<h3>bLocX OS<\/h3>/);
    assert.match(card, /In development/);
    assert.match(card, /planned, separate Android operating-system project/i);
    assert.match(card, /Not available yet/);
    assert.match(card, /supported devices are not yet confirmed/i);
    assert.doesNotMatch(card, /<(?:a|button|form|input|select|textarea)\b|href=|tabindex=/i);
    assert.doesNotMatch(card, /\b(buy|download|install now|checkout|activate|price|warranty|quantum)\b|[€$£]|\b(?:EUR|USD|GBP)\b/i);
});

test('bLocX OS stays outside pricing and download sections', () => {
    for (const id of ['pricing', 'download']) {
        assert.doesNotMatch(sectionById(id), /blocx/i);
    }
    // #lifetime is a legacy anchor inside the single pricing section.
    assert.match(sectionById('pricing'), /\bid="lifetime"/);
});

test('platform section lists existing products plus bLocX as separate', () => {
    const platform = sectionById('platform');
    assert.equal((platform.match(/<article class="product-card\b/g) || []).length, 4);
    assert.match(platform, /id="blocx"/);
    for (const product of ['SecureCall', 'SecureChat', 'Chameleon']) {
        assert.ok(platform.includes(`<h3>${product}</h3>`), `${product} card missing`);
    }
    assert.match(platform, /bLocX OS is a separate, planned project and is not part of that shared core/);
    assert.doesNotMatch(platform, /Three products\. One security core\./);
});
