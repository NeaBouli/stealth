// reportRoute.js — Bug Report API Endpoint
// Add to your existing Railway backend: require('./reportRoute')(app);

const fetch = (...args) => import('node-fetch').then(({default: f}) => f(...args));

// In-memory rate limit store (resets on server restart — fine for abuse prevention)
const rateLimitStore = new Map();
const RATE_LIMIT_MAX = 3;       // max reports per IP
const RATE_LIMIT_WINDOW = 60 * 60 * 1000; // 1 hour in ms

function getRateLimitKey(ip) {
    return `rl:${ip}`;
}

function checkRateLimit(ip) {
    const key = getRateLimitKey(ip);
    const now = Date.now();
    const entry = rateLimitStore.get(key);

    if (!entry || now - entry.windowStart > RATE_LIMIT_WINDOW) {
        rateLimitStore.set(key, { count: 1, windowStart: now });
        return true;
    }
    if (entry.count >= RATE_LIMIT_MAX) {
        return false;
    }
    entry.count++;
    return true;
}

// Clean up old entries every 2 hours to prevent memory leak
setInterval(() => {
    const now = Date.now();
    for (const [key, entry] of rateLimitStore.entries()) {
        if (now - entry.windowStart > RATE_LIMIT_WINDOW * 2) {
            rateLimitStore.delete(key);
        }
    }
}, 2 * 60 * 60 * 1000);

async function uploadScreenshot(base64Data, filename, githubToken, repoOwner, repoName) {
    // Strip data URL prefix if present
    const base64Clean = base64Data.replace(/^data:image\/[a-z]+;base64,/, '');
    const timestamp = Date.now();
    const safeName = `${timestamp}-${filename.replace(/[^a-zA-Z0-9._-]/g, '_')}`;
    const path = `website/bug-screenshots/${safeName}`;

    const response = await fetch(`https://api.github.com/repos/${repoOwner}/${repoName}/contents/${path}`, {
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${githubToken}`,
            'Content-Type': 'application/json',
            'User-Agent': 'SecureCall-BugReport/1.0'
        },
        body: JSON.stringify({
            message: `bug-report: add screenshot ${safeName}`,
            content: base64Clean,
            branch: 'main'
        })
    });

    if (!response.ok) {
        const err = await response.text();
        throw new Error(`Screenshot upload failed: ${err}`);
    }

    const data = await response.json();
    return data.content.download_url;
}

async function createGitHubIssue({ appVersion, androidVersion, device, description, email, screenshotUrl }, githubToken, repoOwner, repoName) {
    const emailLine = email ? `\n**Reporter (optional):** ${email}` : '';
    const screenshotLine = screenshotUrl ? `\n\n**Screenshot:**\n![screenshot](${screenshotUrl})` : '';

    const body = `## Bug Report — SecureCall Wiki

**App Version:** ${appVersion}
**Android Version:** ${androidVersion}
**Device:** ${device}${emailLine}

---

### Description

${description}
${screenshotLine}

---
*Submitted via SecureCall Wiki Bug Report Form*`;

    const response = await fetch(`https://api.github.com/repos/${repoOwner}/${repoName}/issues`, {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${githubToken}`,
            'Content-Type': 'application/json',
            'User-Agent': 'SecureCall-BugReport/1.0'
        },
        body: JSON.stringify({
            title: `[User Report] ${appVersion} / ${device} — ${description.substring(0, 60)}${description.length > 60 ? '…' : ''}`,
            body,
            labels: ['user-report']
        })
    });

    if (!response.ok) {
        const err = await response.text();
        throw new Error(`GitHub Issue creation failed: ${err}`);
    }

    return await response.json();
}

module.exports = function registerReportRoute(app) {
    // Middleware: parse JSON body (add if not already in your app)
    app.use(require('express').json({ limit: '5mb' })); // 5mb for screenshots

    app.post('/api/report', async (req, res) => {
        // --- Config from environment ---
        const GITHUB_TOKEN   = process.env.GITHUB_TOKEN;
        const REPO_OWNER     = process.env.GITHUB_REPO_OWNER || 'NeaBouli';
        const REPO_NAME      = process.env.GITHUB_REPO_NAME  || 'stealth';

        if (!GITHUB_TOKEN) {
            console.error('[report] GITHUB_TOKEN not set');
            return res.status(500).json({ error: 'Server misconfiguration' });
        }

        // --- Get real IP (Railway sits behind a proxy) ---
        const ip = req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.socket.remoteAddress || 'unknown';

        // --- Honeypot check (hidden field must be empty) ---
        if (req.body.website || req.body._trap) {
            // Bot filled the honeypot — silently accept but don't create issue
            return res.status(200).json({ success: true, issue_number: null });
        }

        // --- Rate limit ---
        if (!checkRateLimit(ip)) {
            return res.status(429).json({ error: 'Too many reports. Please wait an hour before submitting again.' });
        }

        // --- Validate required fields ---
        const { appVersion, androidVersion, device, description, email, screenshot, screenshotName } = req.body;

        if (!appVersion || !androidVersion || !device || !description) {
            return res.status(400).json({ error: 'Missing required fields.' });
        }
        if (description.trim().length < 30) {
            return res.status(400).json({ error: 'Description too short. Please provide at least 30 characters.' });
        }
        if (description.length > 5000) {
            return res.status(400).json({ error: 'Description too long (max 5000 characters).' });
        }
        if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            return res.status(400).json({ error: 'Invalid email address.' });
        }

        // --- Optional screenshot upload ---
        let screenshotUrl = null;
        if (screenshot && screenshotName) {
            // Validate it's an image
            if (!screenshot.startsWith('data:image/')) {
                return res.status(400).json({ error: 'Screenshot must be an image file.' });
            }
            // Max ~3MB base64
            if (screenshot.length > 4 * 1024 * 1024) {
                return res.status(400).json({ error: 'Screenshot too large (max 3 MB).' });
            }
            try {
                screenshotUrl = await uploadScreenshot(screenshot, screenshotName, GITHUB_TOKEN, REPO_OWNER, REPO_NAME);
            } catch (err) {
                console.error('[report] Screenshot upload error:', err.message);
                // Non-fatal — continue without screenshot
            }
        }

        // --- Create GitHub Issue ---
        try {
            const issue = await createGitHubIssue(
                { appVersion, androidVersion, device, description: description.trim(), email, screenshotUrl },
                GITHUB_TOKEN, REPO_OWNER, REPO_NAME
            );
            console.log(`[report] Issue #${issue.number} created from IP ${ip}`);
            return res.status(201).json({ success: true, issue_number: issue.number, issue_url: issue.html_url });
        } catch (err) {
            console.error('[report] GitHub issue error:', err.message);
            return res.status(500).json({ error: 'Failed to submit report. Please try GitHub Issues directly.' });
        }
    });

    console.log('[report] /api/report endpoint registered');
};
