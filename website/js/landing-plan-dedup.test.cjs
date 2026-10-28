'use strict';

// Issue #208: the landing page must offer the paid plans exactly once
// (lifetime cards) while keeping the IFR holder section and legacy anchors.

const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const html = readFileSync(join(__dirname, '..', 'index.html'), 'utf8');
const SHOP = 'https://vlabs.gr/en/shop?focus=one-click-apps';

function pricingSection() {
    const sections = [...html.matchAll(/<section\b[^>]*\bid="pricing"[^>]*>[\s\S]*?<\/section>/g)];
    assert.equal(sections.length, 1, 'exactly one #pricing section is required');
    return sections[0][0];
}

function cards(source) {
    return [...source.matchAll(/<article class="price-card[^"]*"[^>]*>([\s\S]*?)<\/article>/g)].map(m => m[1]);
}

test('landing renders a single plan grid with Free plus the two lifetime licences', () => {
    assert.equal((html.match(/class="pricing-grid"/g) || []).length, 1);
    assert.doesNotMatch(html, />Access levels</);
    const titles = cards(html).map(card => card.match(/<h3>([^<]+)<\/h3>/)[1]);
    assert.deepEqual(titles, ['Free', 'Pro Lifetime', 'Premium Lifetime']);
    assert.equal((html.match(/>Lifetime licences\.</g) || []).length, 1);
});

test('lifetime cards keep prices, IFR holder prices, terms and shop links', () => {
    const [free, pro, premium] = cards(pricingSection());
    assert.match(free, /€0<\/span><span class="period">incl\. VAT · forever/);
    assert.match(free, /<a href="#download" class="btn line block">Get started<\/a>/);
    for (const [card, full, holder] of [[pro, '€15', '&euro;7.50'], [premium, '€25', '&euro;12.50']]) {
        assert.ok(card.includes(`${full}</span><span class="period">incl. VAT · one-time`));
        assert.ok(card.includes(`IFR-holder price ${holder} incl. VAT`));
        for (const term of ['Pay once', 'Access for supported versions', 'Signed activation after confirmed payment']) {
            assert.ok(card.includes(term), `lifetime term missing: ${term}`);
        }
        assert.ok(card.includes(`<a href="${SHOP}" class="btn primary block">Buy in VLABS shop</a>`));
    }
    // Feature information formerly in the removed plan grid stays on the lifetime cards.
    assert.match(pro, /Anti-recording detection[\s\S]*Not included: <\/span>Automatic threat termination/);
    assert.match(premium, /Threat auto-termination[\s\S]*Optional app-managed WireGuard in the direct Premium APK/);
});

test('IFR holder section stays inside pricing with its launch gates closed', () => {
    const pricing = pricingSection();
    assert.equal((html.match(/\bid="ifr"/g) || []).length, 1);
    assert.match(pricing, /<div class="ifr-box reveal" id="ifr" data-ifr-checkout data-ifr-product="SecureCall" data-ifr-enabled="false">/);
    assert.ok(pricing.includes('src="https://ifrunit.tech/assets/ifr_icon_256.png" alt="IFR token logo"'));
    for (const control of ['data-ifr-connect disabled', 'data-ifr-disconnect disabled',
        'data-ifr-tier="pro_lifetime" disabled', 'data-ifr-tier="premium_lifetime" disabled']) {
        assert.ok(pricing.includes(control), `IFR control changed: ${control}`);
    }
    assert.ok(pricing.includes('Pro IFR price &euro;7.50 incl. VAT'));
    assert.ok(pricing.includes('Premium IFR price &euro;12.50 incl. VAT'));
});

test('#pricing and #lifetime still resolve to the single pricing section', () => {
    for (const id of ['pricing', 'lifetime']) {
        assert.equal((html.match(new RegExp(`\\bid="${id}"`, 'g')) || []).length, 1, `#${id} must exist once`);
    }
    assert.match(pricingSection(), /<div class="section-head" id="lifetime">/);
    assert.match(html, /<a href="#pricing">Pricing<\/a>/);
    assert.match(html, /<a href="#lifetime">Lifetime<\/a>/);
});

test('direct lifetime links reserve space for the fixed navigation header', () => {
    const css = readFileSync(join(__dirname, '..', 'css', 'landing.css'), 'utf8');
    assert.match(css, /\.section-head#lifetime \{ scroll-margin-top: 148px; \}/);
    assert.equal((css.match(/\.section-head#lifetime/g) || []).length, 1);
    assert.match(html, /css\/landing\.css\?v=20261028-plans/);
});
