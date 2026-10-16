'use strict';

const { readdirSync, readFileSync } = require('node:fs');
const { join, relative } = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');

const websiteRoot = join(__dirname, '..');
const expectedAddress = ['support', '@', ['stealthx', 'tech'].join('.')].join('');
const forbiddenMailto = ['mail', 'to:', 'support'].join('');
const script = readFileSync(join(__dirname, 'mail-assemble.js'), 'utf8');

function containsContactAddress(source) {
    for (let pass = 0; pass < 5; pass++) {
        const lower = source.toLowerCase();
        if (lower.includes(expectedAddress) || lower.includes(forbiddenMailto)) return true;
        const decoded = source
            .replace(/%([0-9a-f]{2})/gi, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
            .replace(/&#(x[0-9a-f]+|\d+);?/gi, (entity, code) => {
                const value = /^x/i.test(code) ? parseInt(code.slice(1), 16) : Number(code);
                return value <= 127 ? String.fromCharCode(value) : entity;
            })
            .replace(/&(commat|period|amp);/gi, (_, name) => ({ commat: '@', period: '.', amp: '&' })[name.toLowerCase()]);
        if (decoded === source) break;
        source = decoded;
    }
    return false;
}

function collectFiles(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
        if (entry.name === '.well-known') return [];
        const path = join(directory, entry.name);
        return entry.isDirectory() ? collectFiles(path) : [path];
    });
}

test('website files outside .well-known contain no plain or encoded contact address', () => {
    const files = collectFiles(websiteRoot);
    assert.ok(files.length > 0);
    const offences = files.filter(path => containsContactAddress(readFileSync(path, 'utf8')))
        .map(path => relative(websiteRoot, path));
    assert.deepEqual(offences, [], `Contact address exposed in:\n${offences.join('\n')}`);
});

test('contact scanner catches plain, entity, percent and nested encodings', () => {
    for (const separator of ['@', '&#64;', '&#x40;', '&#X040;', '%40', '&commat;']) {
        assert.equal(containsContactAddress(['support', separator, 'stealthx', '.', 'tech'].join('')), true);
    }
    const percent = [...expectedAddress].map(char => '%' + char.charCodeAt(0).toString(16)).join('');
    const entities = [...expectedAddress].map(char => '&#' + char.charCodeAt(0) + ';').join('');
    for (const encoded of [percent, entities, percent.replace(/%/g, '%25'), entities.replace(/&/g, '&amp;')]) {
        assert.equal(containsContactAddress(encoded), true);
    }
    assert.equal(containsContactAddress(forbiddenMailto), true);
    assert.equal(containsContactAddress('data-u="troppus" data-d="xhtlaets|hcet"'), false);
    assert.equal(containsContactAddress(['noreply', '@', 'stealthx', '.', 'tech'].join('')), false);
});

function pageContext(html) {
    const elements = [...html.matchAll(/<span class="js-mail" data-u="([^"]+)" data-d="([^"]+)"><\/span>/g)]
        .map(([, u, d]) => ({
            dataset: { u, d },
            children: [],
            appendChild(child) { this.children.push(child); }
        }));
    const document = {
        querySelector(selector) {
            assert.equal(selector, '.js-mail');
            return elements[0];
        },
        querySelectorAll(selector) { return selector === '.js-mail' ? elements : []; },
        createElement(tag) {
            assert.equal(tag, 'a');
            return {
                handlers: {},
                addEventListener(type, handler) { this.handlers[type] = handler; }
            };
        }
    };
    return { elements, document, window: {} };
}

test('both pages assemble the exact address and build mail links at runtime', () => {
    for (const [page, count] of [['impressum.html', 3], ['success.html', 1]]) {
        const html = readFileSync(join(websiteRoot, page), 'utf8');
        assert.match(html, /<script src="js\/mail-assemble\.js" defer><\/script>/);
        const context = pageContext(html);
        assert.equal(context.elements.length, count);
        vm.runInNewContext(script, context);
        assert.equal(context.window.stxMail(), expectedAddress);
        for (const element of context.elements) {
            assert.equal(context.window.stxMail(element), expectedAddress);
            assert.equal(element.children.length, 1);
            const link = element.children[0];
            assert.equal(link.textContent, expectedAddress);
            assert.equal(link.href, ['mail', 'to:', expectedAddress].join(''));
        }
    }
});

test('each contact has a noscript PNG fallback with a generic localized alt', () => {
    for (const [page, count] of [['impressum.html', 3], ['success.html', 1]]) {
        const html = readFileSync(join(websiteRoot, page), 'utf8');
        const fallbacks = [...html.matchAll(/<noscript>([\s\S]*?)<\/noscript>/g)];
        assert.equal(fallbacks.length, count);
        for (const [, fallback] of fallbacks) {
            assert.match(fallback, /<img src="img\/contact-mail\.png" alt="[^"]+"/);
            assert.doesNotMatch(fallback, /support|stealthx|\[at\]|\[dot\]/i);
        }
    }
    const png = readFileSync(join(websiteRoot, 'img/contact-mail.png'));
    assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    for (let offset = 8; offset < png.length;) {
        const length = png.readUInt32BE(offset);
        assert.ok(['IHDR', 'IDAT', 'IEND'].includes(png.toString('ascii', offset + 4, offset + 8)), 'PNG must contain only pixels, with no text metadata');
        offset += length + 12;
    }
});

test('contact form reuses the assembler and preserves message encoding and bot checks', () => {
    const html = readFileSync(join(websiteRoot, 'impressum.html'), 'utf8');
    const context = pageContext(html);
    const fields = {
        cfName: { value: ' Ada & Bob ' },
        cfEmail: { value: ' sender@example.invalid ' },
        cfMessage: { value: ' Hello & thanks?\nΚαλημέρα ' },
        cfStatus: { style: {} },
        contactForm: {
            addEventListener(type, handler) { assert.equal(type, 'submit'); this.submit = handler; },
            querySelector() { return this.honeypot; },
            honeypot: { value: '' }
        }
    };
    context.document.getElementById = id => fields[id] || null;
    context.window.location = { hostname: 'stealthx.tech' };
    context.window.addEventListener = () => {};
    const inline = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)]
        .map(([, source]) => source).find(source => source.includes('contactForm'));
    assert.ok(inline);
    vm.runInNewContext(inline, context);
    vm.runInNewContext(script, context);
    const event = { preventDefault() {} };
    fields.contactForm.submit(event);
    const target = new URL(context.window.location.href);
    assert.equal(target.protocol, ['mail', 'to:'].join(''));
    assert.equal(target.pathname, expectedAddress);
    assert.equal(target.searchParams.get('subject'), 'Contact from Ada & Bob via Impressum');
    assert.equal(target.searchParams.get('body'), 'From: Ada & Bob (sender@example.invalid)\n\nHello & thanks?\nΚαλημέρα');
    assert.equal(fields.cfStatus.style.display, 'block');
    for (const blocked of ['honeypot', 'empty']) {
        context.window.location.href = '';
        fields.contactForm.honeypot.value = blocked === 'honeypot' ? 'bot' : '';
        fields.cfMessage.value = blocked === 'empty' ? ' ' : 'hello';
        fields.contactForm.submit(event);
        assert.equal(context.window.location.href, '');
    }
    assert.doesNotMatch(html, /proton|kaspar|emailText|email-canvas|emailEN|emailDE|emailEL/i);
});
