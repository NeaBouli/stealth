'use strict';

const { readdirSync, readFileSync, statSync } = require('node:fs');
const { join, relative, sep } = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const websiteRoot = join(__dirname, '..');
const thisTest = join(__dirname, 'no-google-analytics.test.cjs');
const SCANNED_EXTENSIONS = new Set(['.html', '.js', '.mjs', '.cjs', '.jsx', '.ts', '.tsx']);

function extensionOf(fileName) {
    const dot = fileName.lastIndexOf('.');
    return dot === -1 ? '' : fileName.slice(dot);
}

function collectWebsiteSources(directory) {
    const found = [];
    for (const entry of readdirSync(directory).sort()) {
        const path = join(directory, entry);
        if (statSync(path).isDirectory()) {
            found.push(...collectWebsiteSources(path));
        } else if (path !== thisTest && SCANNED_EXTENSIONS.has(extensionOf(entry))) {
            found.push(path);
        }
    }
    return found;
}

const websiteSources = collectWebsiteSources(websiteRoot);

const FORBIDDEN = [
    ['Google Analytics loader', /googletagmanager\.com|google-analytics\.com|analytics\.js|gtag\/js/i],
    ['Google Analytics property', /G-V2L60E8E7R/i],
    ['gtag initialization or call', /\bgtag\s*\(|\bwindow\.dataLayer\b|\bdataLayer\s*(?:=|\.push)/i]
];

test('website sources contain no Google Analytics loader, property or gtag usage', () => {
    assert.ok(websiteSources.length > 0, 'expected to scan at least one website source file');
    const offences = [];
    for (const path of websiteSources) {
        const source = readFileSync(path, 'utf8');
        for (const [label, pattern] of FORBIDDEN) {
            if (pattern.test(source)) {
                offences.push(`${relative(websiteRoot, path).split(sep).join('/')}: ${label}`);
            }
        }
    }
    assert.deepEqual(offences, [], `Google Analytics must stay removed:\n${offences.join('\n')}`);
});

const SCRIPT_TAG = /<script\b[^>]*>/gi;
const SRC_ATTRIBUTE = /\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))/i;
const EXACT_VERSION = /(?:@|\/)v?\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?\//;
const DYNAMIC_LOADERS = [
    ['dynamic script element', /createElement\s*\(\s*["'`]script["'`]\s*\)/i],
    ['document.write script', /document\.write(?:ln)?\s*\([^)]*<\s*script/i],
    ['HTML injection of script', /(?:innerHTML|outerHTML|insertAdjacentHTML)[^;]*<\s*script/i],
    ['importScripts', /\bimportScripts\s*\(/],
    ['remote dynamic import', /\bimport\s*\(\s*["'`](?:[a-z][a-z0-9+.-]*:|\/\/)/i],
    ['remote script src assignment', /\.src\s*=\s*["'`](?:[a-z][a-z0-9+.-]*:|\/\/)/i]
];

function scriptSourceOffences(html) {
    const offences = [];
    for (const tag of html.match(SCRIPT_TAG) || []) {
        const match = tag.match(SRC_ATTRIBUTE);
        if (!match) continue;
        const src = (match[1] ?? match[2] ?? match[3]).trim();
        if (!/^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(src)) continue;
        if (!/^https:\/\/[^/]+\//i.test(src)) {
            offences.push(`non-https or protocol-relative script: ${src}`);
            continue;
        }
        if (!EXACT_VERSION.test(new URL(src).pathname + '/')) {
            offences.push(`script without exact version: ${src}`);
        }
        if (!/\bintegrity\s*=\s*["']?sha(?:256|384|512)-[A-Za-z0-9+/=]+/i.test(tag)) {
            offences.push(`missing SRI: ${src}`);
        }
        if (!/\bcrossorigin\s*=\s*["']?anonymous\b/i.test(tag)) {
            offences.push(`missing anonymous CORS: ${src}`);
        }
    }
    return offences;
}

function dynamicLoaderOffences(source) {
    return DYNAMIC_LOADERS.filter(([, pattern]) => pattern.test(source)).map(([label]) => label);
}

test('script checker rejects unpinned, unquoted, protocol-relative and dynamic loads', () => {
    const sri = 'integrity="sha384-AAAA" crossorigin="anonymous"';
    assert.deepEqual(scriptSourceOffences(`<script src="https://cdn.jsdelivr.net/npm/q@1.0.0/q.min.js" ${sri}></script>`), []);
    assert.deepEqual(scriptSourceOffences(`<script src="https://cdnjs.cloudflare.com/ajax/libs/q/1.0.0/q.min.js" ${sri}></script>`), []);
    assert.deepEqual(scriptSourceOffences('<script src="js/local.js"></script>'), []);
    for (const tag of [
        `<script src="https://cdn.jsdelivr.net/npm/q/q.min.js" ${sri}></script>`,
        `<script src="https://cdn.jsdelivr.net/npm/q@latest/q.min.js" ${sri}></script>`,
        `<script src="https://cdn.jsdelivr.net/npm/q@1/q.min.js" ${sri}></script>`,
        `<script src="https://cdn.jsdelivr.net/npm/q@^1.0.0/q.min.js" ${sri}></script>`,
        `<script src=https://cdn.jsdelivr.net/npm/q/q.min.js ${sri}></script>`,
        `<script src=https://cdn.jsdelivr.net/npm/q@1.0.0/q.min.js></script>`,
        `<script src="//cdn.jsdelivr.net/npm/q@1.0.0/q.min.js" ${sri}></script>`,
        `<script src="http://cdn.jsdelivr.net/npm/q@1.0.0/q.min.js" ${sri}></script>`,
        `<script\n  src='https://cdn.jsdelivr.net/npm/q@1.0.0/q.min.js'\n  crossorigin="anonymous"></script>`
    ]) {
        assert.notDeepEqual(scriptSourceOffences(tag), [], tag);
    }
    for (const snippet of [
        "const s = document.createElement('script');",
        'document.write("<script src=x></script>")',
        "el.innerHTML = '<script src=x></script>';",
        "importScripts('https://cdn.example/x.js')",
        "await import('https://cdn.example/x.mjs')",
        "img.src = '//cdn.example/x.js'"
    ]) {
        assert.notDeepEqual(dynamicLoaderOffences(snippet), [], snippet);
    }
});

test('external website scripts are https, exact-version, SRI-pinned and statically declared', () => {
    const offences = [];
    for (const path of websiteSources) {
        const source = readFileSync(path, 'utf8');
        const name = relative(websiteRoot, path).split(sep).join('/');
        if (path.endsWith('.html')) {
            for (const offence of scriptSourceOffences(source)) offences.push(`${name}: ${offence}`);
        }
        for (const offence of dynamicLoaderOffences(source)) offences.push(`${name}: ${offence}`);
    }
    assert.deepEqual(offences, [], `External scripts must be immutable and SRI-protected:\n${offences.join('\n')}`);
});
