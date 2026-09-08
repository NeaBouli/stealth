'use strict';

const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');

const html = readFileSync(join(__dirname, '../wiki/custom-id.html'), 'utf8');

test('closed Custom ID form cannot collect input or register checkout requests', () => {
    const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)]
        .map(match => match[1]).find(source => source.includes('CUSTOM_ID_SALES_ENABLED'));
    assert.ok(script);
    const controls = Array.from({ length: 8 }, () => ({ disabled: false }));
    let queries = 0;
    vm.runInNewContext(script, {
        document: {
            querySelectorAll(selector) {
                assert.equal(selector, '.id-generator input, .id-generator select, .id-generator button');
                queries++;
                return controls;
            },
            getElementById() { assert.fail('Closed form must not install input or purchase handlers'); }
        },
        fetch() { assert.fail('Closed form must not contact the API'); }
    });
    assert.equal(queries, 1);
    assert.ok(controls.every(control => control.disabled));
});

test('Custom ID page is unavailable even before JavaScript executes', () => {
    assert.match(html, /<input[^>]*id="idInput"[^>]*disabled/);
    assert.match(html, /<button[^>]*id="purchaseBtn"[^>]*disabled>Not available yet/);
    assert.doesNotMatch(html, /Live checkout/);
    assert.match(html, /Custom ID purchases are not available/);
});
