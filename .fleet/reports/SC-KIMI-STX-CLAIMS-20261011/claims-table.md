# Grep table — corrected claims (before -> after), 2026-10-11

| # | File | Before | After |
|---|------|--------|-------|
| STX-39 | website/index.html | `Audit on GitHub` (hero button) | `Source on GitHub` |
| STX-39 | website/index.html | `Tested. Audited. Proven.` | `Tested. Reviewed. Proven.` |
| STX-39 | website/index.html | `Quality Audit - v1.0.45` | `Quality Review - v1.0.45` |
| STX-39 | website/index.html | `Network audit: TLS, VPN compatibility, no cleartext` | `Network check: TLS, VPN compatibility, no cleartext` |
| STX-39 | website/index.html | `Full audit report` (button) | `Internal audit report` + new `Public audit register` link (issue #84) |
| STX-39 | website/security.html | `One stack, audited once, used everywhere.` | `One stack, internally reviewed, used everywhere.` |
| STX-42 | website/index.html | `Google Play distribution is in review.` | `Install the signed APK today or get the Free edition on Google Play.` |
| STX-42 | website/download.html | `Published v1.0.48 · v1.0.50 candidate under review` | `Direct APK v1.0.48 · Google Play v1.0.49 live` |
| STX-42/56 | website/llms.txt | `v1.0.49 / versionCode 78016 remains under review` (contradicted own "listing live" line) | `compliance replacement v1.0.49 / versionCode 78016 is live; next candidate: v1.0.50 / versionCode 78017` |
| STX-56 | website/llms.txt | `Signaling: WebSocket (Node.js/Express on Railway)` | `on Hetzner` |
| STX-56 | website/llms.txt | `Contact: GitHub Issues only (no personal support…)` | `GitHub Issues, the contact form on the Impressum page, or the address listed in the privacy policy` |
| STX-44 | WORKFLOWS.md | endpoint `protective-healing-production.up.railway.app/api/report` | `api.stealthx.tech/api/report` (matches live bug-report form) |
| STX-44 | docs/ADMIN_BROADCAST.md | `ADMIN_API_KEY … ONLY in Railway environment variables` + 2× Railway curl URL | production server environment (Hetzner); curl → `api.stealthx.tech` |
| STX-44 | docs/PLAY_STORE_UPLOAD_CHECKLIST.md | `Railway ENV: GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64` | `Production server ENV (Hetzner): …` |
| STX-44 | docs/BACKEND_DEPLOYMENT_GUIDE.md, LAUNCH_GUIDE.md, YOUR_BACKEND_URLS.md, RAILWAY_ENV_VARS.md | present Railway as the production setup | dated status note added: production = Hetzner (`api.stealthx.tech`), Railway = standby reference |

Verified facts: Play URL `play.google.com/store/apps/details?id=com.securecall.app.free` → HTTP 200, listing shows v1.0.49 (2026-10-11); GitHub Releases latest = v1.0.48; app signaling `wss://api.stealthx.tech/signal` (client_android/app/build.gradle); legal pages (disclaimer.html) state Hetzner-hosted api.stealthx.tech. Untouched per brief: wiki, marketing/play_store ("Independently audited" remains there — follow-up), prices.
