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
const htmlSources = websiteSources.filter(path => path.endsWith('.html'));

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

test('external website scripts are version-pinned and integrity-protected', () => {
    const offences = [];
    const externalScript = /<script\b[^>]*\bsrc=["']https:\/\/[^"']+["'][^>]*>/gi;
    for (const path of htmlSources) {
        const html = readFileSync(path, 'utf8');
        for (const tag of html.match(externalScript) || []) {
            if (!/\bintegrity=["']sha(?:256|384|512)-[^"']+["']/i.test(tag)) {
                offences.push(`${relative(websiteRoot, path).split(sep).join('/')}: missing SRI`);
            }
            if (!/\bcrossorigin=["']anonymous["']/i.test(tag)) {
                offences.push(`${relative(websiteRoot, path).split(sep).join('/')}: missing anonymous CORS`);
            }
        }
    }
    assert.deepEqual(offences, [], `External scripts must be immutable and SRI-protected:\n${offences.join('\n')}`);
});
