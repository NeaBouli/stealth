'use strict';

const { readdirSync, readFileSync, statSync } = require('node:fs');
const { join, relative, sep } = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const websiteRoot = join(__dirname, '..');

function collectHtmlFiles(directory) {
    const found = [];
    for (const entry of readdirSync(directory).sort()) {
        const path = join(directory, entry);
        if (statSync(path).isDirectory()) {
            found.push(...collectHtmlFiles(path));
        } else if (entry.endsWith('.html')) {
            found.push(path);
        }
    }
    return found;
}

const htmlFiles = collectHtmlFiles(websiteRoot);

const FORBIDDEN = [
    ['Google Analytics loader', /googletagmanager\.com|google-analytics\.com|analytics\.js|gtag\/js/i],
    ['Google Analytics property', /G-V2L60E8E7R/i],
    ['gtag initialization or call', /\bgtag\s*\(|\bwindow\.dataLayer\b|\bdataLayer\s*(?:=|\.push)/i]
];

test('website HTML contains no Google Analytics loader, property or gtag usage', () => {
    assert.ok(htmlFiles.length > 0, 'expected to scan at least one website HTML file');
    const offences = [];
    for (const path of htmlFiles) {
        const html = readFileSync(path, 'utf8');
        for (const [label, pattern] of FORBIDDEN) {
            if (pattern.test(html)) {
                offences.push(`${relative(websiteRoot, path).split(sep).join('/')}: ${label}`);
            }
        }
    }
    assert.deepEqual(offences, [], `Google Analytics must stay removed:\n${offences.join('\n')}`);
});
