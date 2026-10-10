'use strict';

const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const { parseInvite, render } = require('./invite-receiver.js');

const websiteRoot = join(__dirname, '..');
const b64 = (n, c) => c.repeat(n);
// Synthetic, non-real bundle: sx id of 9 Base58 chars, filler key/signature material.
const SX_ID = 'sx_AAAAAAAAA';
const BUNDLE = 'stealthx://add/' + SX_ID + '?x=' + b64(43, 'x') + '&e=' + b64(43, 'e') + '&s=' + b64(86, 's') + '&c=1700000000000&h=%40demo_user';
const enc = encodeURIComponent;
const at = (pathname, search, hash) => ({ pathname, search: search || '', hash: hash || '' });

for (const [label, pathname] of [['canonical /invite/', '/invite/'], ['legacy /invite.html', '/invite.html']]) {
    test(label + ': fragment bundle accepted and passed byte-for-byte', () => {
        for (const app of ['chameleon', 'securechat']) {
            const r = parseInvite(at(pathname, '', '#app=' + app + '&link=' + enc(BUNDLE)));
            assert.equal(r.kind, 'bundle');
            assert.equal(r.app, app);
            assert.equal(r.sxId, SX_ID);
            assert.equal(r.link, BUNDLE);
            assert.equal(r.viaFragment, true);
        }
    });

    test(label + ': old query format still works', () => {
        const r = parseInvite(at(pathname, '?app=chameleon&link=' + enc(BUNDLE)));
        assert.equal(r.kind, 'bundle');
        assert.equal(r.link, BUNDLE);
        assert.equal(r.viaFragment, false);
    });

    test(label + ': SecureCall id invitations unchanged', () => {
        assert.deepEqual(parseInvite(at(pathname, '?id=SC-1234&name=Alice')), { kind: 'securecall', id: 'SC-1234', name: 'Alice' });
        assert.deepEqual(parseInvite(at(pathname, '?id=abc_DEF-9')), { kind: 'securecall', id: 'abc_DEF-9', name: null });
        assert.deepEqual(parseInvite(at(pathname, '?app=securecall&id=abc')), { kind: 'securecall', id: 'abc', name: null });
        assert.deepEqual(parseInvite(at(pathname, '', '#legacyId9')), { kind: 'securecall', id: 'legacyId9', name: null });
        assert.equal(parseInvite(at(pathname)).kind, 'empty');
    });
}

test('legacy /invite/<id> path form still works', () => {
    assert.deepEqual(parseInvite(at('/invite/abc123')), { kind: 'securecall', id: 'abc123', name: null });
});

test('SecureCall id with reserved characters is kept verbatim (no guessed charset)', () => {
    const r = parseInvite(at('/invite/', '?id=' + enc('a.b@c+d&e=f')));
    assert.equal(r.id, 'a.b@c+d&e=f');
});

test('conflicting fragment and query fail closed, no fallback to query', () => {
    const other = BUNDLE.replace('sx_AAAAAAAAA', 'sx_BBBBBBBBB');
    assert.equal(parseInvite(at('/invite/', '?app=chameleon&link=' + enc(other), '#app=chameleon&link=' + enc(BUNDLE))).kind, 'invalid');
    // invalid fragment must not fall back to a valid query
    assert.equal(parseInvite(at('/invite/', '?app=chameleon&link=' + enc(BUNDLE), '#app=chameleon&link=' + enc('stealthx://add/bad'))).kind, 'invalid');
    assert.equal(parseInvite(at('/invite/', '?app=chameleon&link=' + enc(BUNDLE), '#SC-1')).kind, 'invalid');
    assert.equal(parseInvite(at('/invite/', '?id=SC-1', '#app=chameleon&link=' + enc(BUNDLE))).kind, 'invalid');
    assert.equal(parseInvite(at('/invite/', '?id=SC-1&app=chameleon&link=' + enc(BUNDLE))).kind, 'invalid');
    assert.equal(parseInvite(at('/invite/abc', '?app=chameleon&link=' + enc(BUNDLE))).kind, 'invalid');
    assert.equal(parseInvite(at('/invite/', '', '#foo=bar')).kind, 'invalid');
});

test('invalid app, missing or duplicate parameters are rejected', () => {
    const f = (h) => parseInvite(at('/invite/', '', h)).kind;
    assert.equal(f('#app=evil&link=' + enc(BUNDLE)), 'invalid');
    assert.equal(f('#app=securecall&link=' + enc(BUNDLE)), 'invalid');
    assert.equal(f('#app=chameleon'), 'invalid');
    assert.equal(f('#link=' + enc(BUNDLE)), 'invalid');
    assert.equal(f('#app=chameleon&app=securechat&link=' + enc(BUNDLE)), 'invalid');
    assert.equal(f('#app=chameleon&link=' + enc(BUNDLE) + '&link=' + enc(BUNDLE)), 'invalid');
    assert.equal(f('#app=chameleon&link=' + enc(BUNDLE) + '&extra=1'), 'invalid');
    assert.equal(parseInvite(at('/invite/', '?id=a&id=b')).kind, 'invalid');
    assert.equal(parseInvite(at('/invite/', '?app=chameleon&id=a')).kind, 'invalid');
});

test('non-stealthx schemes, hosts, paths and userinfo are rejected', () => {
    const bad = [
        'javascript:alert(1)',
        'https://evil.example/?x=1',
        'intent://add/' + SX_ID + '#Intent;scheme=stealthx;end',
        'data:text/html,hi',
        'stealthx://evil/' + SX_ID + BUNDLE.substring(BUNDLE.indexOf('?')),
        'stealthx://user@add/' + SX_ID + BUNDLE.substring(BUNDLE.indexOf('?')),
        'stealthx://add/../' + SX_ID + BUNDLE.substring(BUNDLE.indexOf('?')),
        'stealthx://add/x/' + SX_ID + BUNDLE.substring(BUNDLE.indexOf('?')),
        ' ' + BUNDLE,
        'STEALTHX://add/' + SX_ID + BUNDLE.substring(BUNDLE.indexOf('?')),
        BUNDLE + '#frag'
    ];
    for (const link of bad) {
        assert.equal(parseInvite(at('/invite/', '', '#app=chameleon&link=' + enc(link))).kind, 'invalid', link);
        assert.equal(parseInvite(at('/invite/', '?app=securechat&link=' + enc(link))).kind, 'invalid', link);
    }
});

test('malformed, overlong, control-char and double-encoded bundles are rejected', () => {
    const q = BUNDLE.substring(BUNDLE.indexOf('?'));
    const bad = [
        BUNDLE.replace('sx_AAAAAAAAA', 'sx_AAAAAAAA0'),          // base58 excludes 0
        BUNDLE.replace('sx_AAAAAAAAA', 'sx_AAAAAAAAAA'),         // too long
        BUNDLE.replace('sx_AAAAAAAAA', 'sx_AAAAAAAA'),           // too short
        'stealthx://add/' + SX_ID,                               // no query
        BUNDLE.replace('x=' + b64(43, 'x'), 'x=' + b64(42, 'x')),
        BUNDLE.replace('s=' + b64(86, 's'), 's=' + b64(87, 's')),
        BUNDLE.replace('c=1700000000000', 'c=12ab'),
        BUNDLE.replace('&c=1700000000000', ''),
        BUNDLE + '&x=' + b64(43, 'x'),                           // duplicate key
        BUNDLE + '&z=1',                                         // unknown key
        BUNDLE.replace('%40demo_user', '%2540demo_user'),        // double-encoded handle
        BUNDLE.replace('%40demo_user', '%40' + 'a'.repeat(21)),
        BUNDLE.replace('%40demo_user', '%40ab'),
        BUNDLE.replace('x=' + b64(43, 'x'), 'x=%78' + b64(42, 'x')),
        BUNDLE + '\u0000',
        BUNDLE + '\n',
        BUNDLE + '&h=' + 'a'.repeat(600),
        'stealthx://add/' + SX_ID + q + '"'
    ];
    for (const link of bad) {
        assert.equal(parseInvite(at('/invite/', '', '#app=chameleon&link=' + enc(link))).kind, 'invalid', JSON.stringify(link).slice(0, 80));
    }
    // double-decoding: the outer value is decoded exactly once
    assert.equal(parseInvite(at('/invite/', '', '#app=chameleon&link=' + enc(enc(BUNDLE)))).kind, 'invalid');
});

test('SecureCall input with control chars, bidi marks or excess size is rejected; bad name dropped', () => {
    for (const id of ['a\u0000b', 'a b', 'a\nb', 'a‮b', 'x'.repeat(65), '']) {
        assert.equal(parseInvite(at('/invite/', '?id=' + enc(id))).kind, id === '' ? 'empty' : 'invalid', JSON.stringify(id));
    }
    assert.equal(parseInvite(at('/invite/', '?id=ok&name=' + enc('x'.repeat(101)))).name, null);
    assert.equal(parseInvite(at('/invite/', '?id=ok&name=' + enc('a‮b'))).name, null);
});

function fakeDom() {
    const els = {};
    const mk = (id) => (els[id] = { id, textContent: '', style: {}, attrs: {}, listeners: {}, href: undefined,
        addEventListener(t, fn) { this.listeners[t] = fn; }, removeAttribute(a) { delete this.attrs[a]; if (a === 'href') this.href = undefined; },
        setAttribute(a, v) { this.attrs[a] = v; } });
    ['title', 'subtitle', 'secureId', 'openAppLink', 'downloadLink', 'playLink'].forEach(mk);
    const label = { textContent: '' };
    return { els, doc: { title: '', getElementById: (i) => els[i], querySelector: () => label }, label };
}

test('render: bundle sets href to the exact validated link; fixed download hosts', () => {
    const { els, doc, label } = fakeDom();
    const win = { location: at('/invite/', '', '#app=chameleon&link=' + enc(BUNDLE)) };
    render(doc, win);
    assert.equal(els.openAppLink.href, BUNDLE);
    assert.equal(els.secureId.textContent, SX_ID);
    assert.equal(label.textContent, 'Contact ID');
    assert.equal(els.downloadLink.href, 'https://chameleon.stealthx.tech/');
    els.openAppLink.listeners.click({ preventDefault() {} });
    assert.equal(win.location, BUNDLE);
});

test('render: invalid input exposes no link and echoes nothing', () => {
    const { els, doc } = fakeDom();
    const evil = 'javascript:alert(1)';
    render(doc, { location: at('/invite/', '', '#app=chameleon&link=' + enc(evil)) });
    assert.equal(els.openAppLink.href, undefined);
    assert.equal(els.openAppLink.attrs['aria-disabled'], 'true');
    assert.ok(!els.secureId.textContent.includes('javascript'));
    assert.equal(els.openAppLink.listeners.click, undefined);
});

test('render: SecureCall links percent-encode id and name', () => {
    const { els, doc } = fakeDom();
    render(doc, { location: at('/invite/', '?id=' + enc('a&name=evil') + '&name=' + enc('Al ice')) });
    assert.equal(els.openAppLink.href, 'securecall://add-contact?id=a%26name%3Devil&name=Al%20ice');
});

test('both pages: no-referrer, shared controller, no inline invite parsing, no storage/network/analytics', () => {
    const controller = readFileSync(join(__dirname, 'invite-receiver.js'), 'utf8');
    const pages = { 'invite.html': 'js/invite-receiver.js', 'invite/index.html': '../js/invite-receiver.js' };
    for (const [page, src] of Object.entries(pages)) {
        const html = readFileSync(join(websiteRoot, page), 'utf8');
        assert.match(html, /<meta name="referrer" content="no-referrer">/, page);
        assert.ok(html.includes('<script src="' + src + '"></script>'), page);
        assert.doesNotMatch(html, /URLSearchParams|location\.(search|hash)|localStorage|sessionStorage|<img|\.href\s*\+?=/, page);
        for (const m of html.matchAll(/target="_blank"[^>]*/g)) assert.match(m[0], /noreferrer/, page);
        assert.doesNotMatch(html, /<script[^>]+src="https?:/, page);
    }
    assert.doesNotMatch(controller, /localStorage|sessionStorage|document\.cookie|fetch\(|XMLHttpRequest|sendBeacon|new Image|WebSocket|eval\(|innerHTML|document\.write|referrer=/);
});
