'use strict';

const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const html = readFileSync(join(__dirname, '..', 'index.html'), 'utf8');

function articleById(id) {
    const start = html.indexOf(`id="${id}"`);
    assert.notEqual(start, -1, `#${id} missing`);
    const open = html.lastIndexOf('<article', start);
    return html.slice(open, html.indexOf('</article>', start));
}

test('bLocX OS card is a planned, non-purchase status', () => {
    const card = articleById('blocx');
    assert.match(card, /In development/);
    assert.match(card, /Not available yet/);
    assert.match(card, /supported devices are not yet confirmed/i);
    assert.doesNotMatch(card, /<a\s|<button|<form|<input|href=/i);
    assert.doesNotMatch(card, /\b(buy|download|install now|checkout|activate|price|warranty|quantum)\b/i);
});

test('bLocX OS stays outside the Suite band', () => {
    const start = html.indexOf('class="suite-band');
    const suite = html.slice(start, html.indexOf('</div>\n            </div>', start));
    assert.match(suite, /Sales not yet available/);
    assert.doesNotMatch(suite, /bLocX/i);
});

test('platform section lists existing products plus bLocX as separate', () => {
    const grid = html.slice(html.indexOf('class="product-grid"'), html.indexOf('class="suite-band'));
    assert.equal((grid.match(/<article class="product-card/g) || []).length, 4);
    assert.doesNotMatch(html, /Three products\. One security core\./);
});
