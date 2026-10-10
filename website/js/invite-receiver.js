/*
 * Shared invite receiver for /invite/ and /invite.html.
 *
 * Producer contract (existing, unchanged):
 *   SecureCall : ?id=<id>[&name=<name>], /invite/<id>, or bare #<id> (404 router)
 *   SecureChat / Chameleon : app=securechat|chameleon & link=<urlencoded stealthx://add/ bundle>
 *     carried in the URL fragment (#app=..&link=..) or, for older clients, the query (?app=..&link=..).
 * The bundle is handed to the app byte-for-byte. Signature and identity checks stay in the app;
 * this page only validates the shape the app import accepts and never reports a link as verified.
 */
(function (root) {
    'use strict';

    var MAX_ID = 64;
    var MAX_NAME = 100;
    var MAX_LINK = 512;
    var BUNDLE_PREFIX = 'stealthx://add/';
    var SX_ID = /^sx_[1-9A-HJ-NP-Za-km-z]{9}$/;
    var B64_32 = /^[A-Za-z0-9_-]{43}$/;
    var B64_64 = /^[A-Za-z0-9_-]{86}$/;
    var DIGITS = /^[0-9]{1,16}$/;
    var HANDLE_RAW = /^(?:%40|@)[A-Za-z0-9_]{3,20}$/;
    var HANDLE = /^@[A-Za-z0-9_]{3,20}$/;
    // C0/C1 controls, whitespace, line/paragraph separators, bidi overrides/isolates.
    var UNSAFE_TEXT = /[\u0000-\u0020\u007f-\u00a0\u2028\u2029\u202a-\u202e\u2066-\u2069]/;
    // Display names may contain ordinary spaces, ids may not.
    var UNSAFE_NAME = /[\u0000-\u001f\u007f-\u00a0\u2028\u2029\u202a-\u202e\u2066-\u2069]/;
    var APPS = {
        securechat: { name: 'SecureChat', download: 'https://securechat.stealthx.tech/', color: '#00E676' },
        chameleon: { name: 'Chameleon', download: 'https://chameleon.stealthx.tech/', color: '#b39ddb' }
    };
    var INVALID = { kind: 'invalid' };

    // Returns null when the key is absent, false when it is repeated, else the single decoded value.
    function single(params, key) {
        var all = params.getAll(key);
        if (all.length === 0) return null;
        return all.length === 1 ? all[0] : false;
    }

    function validText(value, max, unsafe) {
        return typeof value === 'string' && value.length > 0 && value.length <= max && !unsafe.test(value);
    }

    // Validates a decoded stealthx://add/ bundle against the app import contract.
    // Returns the sx id, or null. Does not decode the bundle a second time (except the handle value).
    function bundleId(link) {
        if (typeof link !== 'string' || link.length > MAX_LINK || link.indexOf(BUNDLE_PREFIX) !== 0) return null;
        if (!/^[\x21-\x7e]+$/.test(link) || /[#\\"'<>`]/.test(link)) return null;
        var rest = link.substring(BUNDLE_PREFIX.length);
        var q = rest.indexOf('?');
        if (q < 0) return null;
        var sxId = rest.substring(0, q);
        if (!SX_ID.test(sxId)) return null;
        var seen = {};
        var parts = rest.substring(q + 1).split('&');
        for (var i = 0; i < parts.length; i++) {
            var eq = parts[i].indexOf('=');
            if (eq < 1) return null;
            var key = parts[i].substring(0, eq);
            var val = parts[i].substring(eq + 1);
            if (seen[key] || !/^[xesch]$/.test(key)) return null;
            seen[key] = true;
            if ((key === 'x' || key === 'e') && !B64_32.test(val)) return null;
            if (key === 's' && !B64_64.test(val)) return null;
            if (key === 'c' && !DIGITS.test(val)) return null;
            if (key === 'h') {
                if (!HANDLE_RAW.test(val)) return null;
                var handle = val.replace(/^%40/, '@');
                if (!HANDLE.test(handle)) return null;
            }
        }
        return seen.x && seen.e && seen.s && seen.c ? sxId : null;
    }

    function hasAppLink(params) {
        return params.has('link') || params.getAll('app').some(function (v) { return v !== 'securecall'; });
    }

    // loc: { pathname, search, hash }. Pure; returns a descriptor, never touches the DOM.
    function parseInvite(loc) {
        var query = new URLSearchParams(loc.search || '');
        var hash = loc.hash && loc.hash.charAt(0) === '#' ? loc.hash.substring(1) : (loc.hash || '');
        var hashIsParams = hash.indexOf('=') >= 0;
        var frag = new URLSearchParams(hashIsParams ? hash : '');
        var qHas = hasAppLink(query);
        var fHas = hasAppLink(frag);

        if (qHas || fHas) {
            // Fragment and query both carrying a bundle is ambiguous: fail closed, no fallback.
            if (qHas && fHas) return INVALID;
            var src = fHas ? frag : query;
            if (hashIsParams && !fHas) return INVALID;
            if (!fHas && hash) return INVALID;
            var keys = [];
            src.forEach(function (v, k) { keys.push(k); });
            for (var i = 0; i < keys.length; i++) {
                if (keys[i] !== 'app' && keys[i] !== 'link') return INVALID;
            }
            if (fHas && (query.has('id') || query.has('name'))) return INVALID;
            if (!fHas && query.has('id')) return INVALID;
            var seg = (loc.pathname || '').split('/').filter(Boolean);
            if (seg.length >= 2 && seg[0] === 'invite') return INVALID;
            var app = single(src, 'app');
            var link = single(src, 'link');
            if (!app || !link || !Object.prototype.hasOwnProperty.call(APPS, app)) return INVALID;
            var sxId = bundleId(link);
            if (!sxId) return INVALID;
            return { kind: 'bundle', app: app, info: APPS[app], sxId: sxId, link: link, viaFragment: fHas };
        }

        // SecureCall ID invitation (unchanged formats).
        if (hashIsParams) return INVALID;
        var id = single(query, 'id');
        var name = single(query, 'name');
        if (id === false) return INVALID;
        if (id === '') id = null;
        if (id === null) {
            var parts = (loc.pathname || '').split('/').filter(Boolean);
            if (parts.length >= 2 && parts[0] === 'invite') id = parts[1];
        }
        if (id === null && hash) id = hash;
        if (id === null) return { kind: 'empty' };
        if (!validText(id, MAX_ID, UNSAFE_TEXT)) return INVALID;
        if (name === false || (name !== null && !validText(name, MAX_NAME, UNSAFE_NAME))) name = null;
        return { kind: 'securecall', id: id, name: name };
    }

    function render(doc, win) {
        var $ = function (id) { return doc.getElementById(id); };
        var open = $('openAppLink');
        var result = parseInvite(win.location);
        var go = function (target, fallback) {
            open.addEventListener('click', function (e) {
                e.preventDefault();
                win.location = target;
                if (fallback) setTimeout(function () { win.location = fallback; }, 2000);
            });
        };

        if (result.kind === 'bundle') {
            var info = result.info;
            $('title').textContent = 'You\'re invited to ' + info.name;
            $('subtitle').textContent = 'Tap below to add this contact in ' + info.name + '. If you don\'t have the app yet, download it first.';
            $('secureId').textContent = result.sxId;
            doc.querySelector('.id-label').textContent = 'Contact ID';
            doc.title = info.name + ' Invitation';
            open.textContent = 'Open in ' + info.name;
            open.style.background = info.color;
            open.style.color = '#000';
            $('downloadLink').textContent = 'Download ' + info.name;
            $('downloadLink').href = info.download;
            $('playLink').textContent = info.name + ' release page';
            $('playLink').href = info.download;
            open.href = result.link;
            go(result.link, info.download);
        } else if (result.kind === 'securecall') {
            $('secureId').textContent = result.id;
            var nameParam = '';
            if (result.name) {
                $('title').textContent = result.name + ' invited you!';
                $('subtitle').textContent = 'Join ' + result.name + ' on SecureCall for encrypted calls. No phone number needed.';
                doc.title = 'SecureCall Invitation from ' + result.name;
                nameParam = '&name=' + encodeURIComponent(result.name);
            } else {
                doc.title = 'SecureCall Invitation from ' + result.id;
            }
            var scheme = 'securecall://add-contact?id=' + encodeURIComponent(result.id) + nameParam;
            var https = 'https://stealthx.tech/invite/?id=' + encodeURIComponent(result.id) + nameParam;
            open.href = scheme;
            go(scheme, https);
        } else {
            var invalid = result.kind === 'invalid';
            $('secureId').textContent = invalid ? 'Invalid invitation link' : 'No ID in URL';
            $('title').textContent = invalid ? 'Invitation not recognized' : 'SecureCall';
            $('subtitle').textContent = invalid
                ? 'This invitation link is malformed or unsupported. Ask the sender for a new invitation.'
                : 'End-to-end encrypted calls. No phone number needed.';
            open.removeAttribute('href');
            open.setAttribute('aria-disabled', 'true');
            open.style.opacity = '0.4';
            open.style.pointerEvents = 'none';
        }
        return result;
    }

    function copyId() {
        var doc = root.document;
        var id = doc.getElementById('secureId').textContent;
        if (id && id !== 'loading...' && id !== 'No ID in URL' && id !== 'Invalid invitation link') {
            root.navigator.clipboard.writeText(id).then(function () {
                var label = doc.getElementById('copiedLabel');
                label.classList.add('show');
                setTimeout(function () { label.classList.remove('show'); }, 2000);
            });
        }
    }

    var api = { parseInvite: parseInvite, bundleId: bundleId, render: render, copyId: copyId };
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else {
        root.copyId = copyId;
        render(root.document, root);
    }
})(typeof window !== 'undefined' ? window : globalThis);
