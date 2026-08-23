# SecureCall — Complete Launch Guide (Zero-Cost Strategy)

> **Free Cloud Strategy:** GitHub Pages + Railway.app + Metered.ca
>
> Only cost: **25 EUR** (Google Play Developer Account, one-time)
> Ongoing costs: **0 EUR/month** (Free Tiers)

---

## Cost Overview

| Item | Cost | Type |
|------|------|------|
| Website (GitHub Pages) | 0 EUR | Free |
| Signaling Server (Railway.app) | 0 EUR | Free Tier (500h/month) |
| TURN Server (Metered.ca) | 0 EUR | Free Tier (50 GB/month) |
| SSL (automatic) | 0 EUR | GitHub Pages + Railway |
| Google Play Developer | 25 EUR | One-time |
| **Total** | **25 EUR** | **One-time** |

## Timeline

| Week | Task |
|------|------|
| **1** | Deploy website, set up Railway + Metered, keystore, builds |
| **2** | Manual QA, Play Store Account, Internal Testing |
| **2–3** | Beta Testing (10–20 testers) |
| **3** | Production Release, Marketing |

---

## Step 1: Deploy Website on GitHub Pages

### 1.1 Enable GitHub Pages

1. Go to https://github.com/NeaBouli/stealth/settings/pages
2. **Source:** select "GitHub Actions"
3. The workflow `.github/workflows/deploy-pages.yml` is already in the repo

> The workflow automatically deploys the `website/` directory on every push to `main`.

### 1.2 Verify

After the next push to `main`:
1. Wait 1-2 minutes for the build
2. Check: https://neabouli.github.io/stealth/
3. Test all pages: `/`, `/privacy.html`, `/security.html`, `/faq.html`

### 1.3 Custom Domain (optional, costs ~12 EUR/year)

If you want to use a custom domain:
1. Register domain at Namecheap (~12 EUR/year) or Cloudflare (~11 EUR/year)
2. DNS: CNAME `www` → `neabouli.github.io`
3. GitHub Pages Settings → Custom Domain → `neabouli.github.io/stealth`
4. Enable "Enforce HTTPS"
5. Create `website/CNAME` file with content: `neabouli.github.io/stealth`

### Checklist

- [ ] GitHub Pages enabled
- [ ] Website reachable at https://neabouli.github.io/stealth/
- [ ] All pages load correctly
- [ ] HTTPS works

---

## Step 2: Deploy Signaling Server on Railway.app

### 2.1 Create Railway Account

1. Open https://railway.com
2. Click "Start a New Project"
3. Log in with **GitHub** (NeaBouli Account)
4. Authorize GitHub access

### 2.2 Create Project

1. Dashboard → "New Project"
2. "Deploy from GitHub repo" → `NeaBouli/stealth`
3. Railway automatically detects `backend/signaling/railway.json`

### 2.3 Configure Service

If not automatically detected:
1. Service Settings → Source → **Root Directory:** `backend/signaling`
2. **Start Command:** `node src/server.js`

### 2.4 Set Environment Variables

Service → Variables:

| Variable | Value |
|----------|-------|
| `NODE_ENV` | `production` |
| `PORT` | `${{RAILWAY_PORT}}` |
| `TURN_SECRET` | `[run openssl rand -hex 32]` |
| `CORS_ORIGIN` | `https://neabouli.github.io` |

### 2.5 Generate Domain

1. Service → Settings → Networking → "Generate Domain"
2. Copy URL: `[name]-production.up.railway.app`
3. This URL is the Signaling Server!

### 2.6 Verify

```bash
curl https://[YOUR-URL].up.railway.app/health
# → {"status":"ok"}
```

### Checklist

- [ ] Railway Account created
- [ ] Project deployed and running (green status)
- [ ] Environment Variables set
- [ ] Health Check responds
- [ ] Railway URL noted for Android App

> **Detailed guide:** [docs/RAILWAY_DEPLOYMENT.md](RAILWAY_DEPLOYMENT.md)

---

## Step 3: Set Up TURN Server via Metered.ca

### 3.1 Create Account

1. Open https://www.metered.ca/signup
2. Create account (email + password)
3. Choose Free Plan (50 GB/month)

### 3.2 Get TURN Credentials

1. Dashboard → "TURN Server" Tab
2. Note credentials and URLs:
   - `stun:stun.relay.metered.ca:80`
   - `turn:global.relay.metered.ca:80`
   - `turns:global.relay.metered.ca:443?transport=tcp`
   - Username + Credential

### 3.3 Update Android App URLs

In `client_android/app/build.gradle` adjust the release URLs:

```groovy
release {
    buildConfigField "String", "SIGNAL_WS_URL",
        "\"wss://[RAILWAY-URL].up.railway.app/signal\""
    buildConfigField "String", "STUN_URL",
        "\"stun:stun.relay.metered.ca:80\""
    buildConfigField "String", "TURN_URL",
        "\"turn:global.relay.metered.ca:80\""
    buildConfigField "String", "TURNS_URL",
        "\"turns:global.relay.metered.ca:443?transport=tcp\""
}
```

### Checklist

- [ ] Metered.ca Account created
- [ ] TURN Credentials obtained
- [ ] URLs entered in build.gradle

> **Detailed guide:** [docs/TURN_SERVER_SETUP.md](TURN_SERVER_SETUP.md)

---

## Step 4: Generate Release Keystore

### 4.1 Create Keystore

```bash
keytool -genkey -v \
    -keystore securecall-release-key.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias securecall \
    -dname "CN=SecureCall, OU=Mobile, O=StealthX, L=Berlin, ST=Berlin, C=DE"
```

> Choose secure passwords! Generate: `openssl rand -base64 32`

### 4.2 Store Keystore Securely

- **NEVER** commit to the Git repo (`.gitignore` already blocks `*.jks`)
- Backup in password manager (1Password, Bitwarden)
- Backup on encrypted USB drive
- Note SHA-256 fingerprint: `keytool -list -v -keystore securecall-release-key.jks`

### 4.3 Set Signing Variables

```bash
export SECURECALL_STORE_FILE=/path/to/securecall-release-key.jks
export SECURECALL_STORE_PASSWORD=[STORE_PASSWORD]
export SECURECALL_KEY_ALIAS=securecall
export SECURECALL_KEY_PASSWORD=[KEY_PASSWORD]
```

### Checklist

- [ ] Keystore generated
- [ ] 2+ backups created (different locations)
- [ ] SHA-256 fingerprint noted
- [ ] Signing variables set

---

## Step 5: Build Distribution Artifacts

### 5.1 Prerequisites

```bash
java -version        # JDK 17+
rustc --version      # Rust 1.70+
echo $ANDROID_HOME   # Android SDK path

# Rust Android Targets
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
```

### 5.2 Build

```bash
cd client_android
./gradlew clean
./gradlew -Pinternal bundleFreeRelease \
  assembleFreeRelease assembleProRelease assemblePremiumRelease \
  verifyNoVpnServiceSource verifyFreeReleaseVpnPolicy \
  verifyProReleaseVpnPolicy verifyPremiumReleaseVpnRuntime
```

The Free AAB is the only Google Play artifact. Free, Pro and Premium APKs are direct-download
artifacts; only the direct Premium APK contains the optional app-managed WireGuard feature.
See [`DISTRIBUTION_MATRIX.md`](DISTRIBUTION_MATRIX.md).

### 5.3 Verify

```bash
ls -lh app/build/outputs/bundle/freeRelease/app-free-release.aab
ls -lh app/build/outputs/apk/{free,pro,premium}/release/app-*-release.apk
```

### Checklist

- [ ] Clean build successful
- [ ] Free AAB built for Google Play
- [ ] Free, Pro and Premium direct APKs built
- [ ] VPN boundary verification tasks passed
- [ ] No Pro or Premium artifact is prepared for Google Play

---

## Step 6: Manual Testing on Real Device

### 6.1 Install APK

```bash
./gradlew bundleFreeRelease assembleFreeRelease
adb install app/build/outputs/apk/free/release/app-free-release.apk
```

### 6.2 Critical Tests

```
[ ] App starts without crash (Cold Start < 3s)
[ ] WebSocket connects to Railway Server
[ ] Call between 2 devices works
[ ] Audio audible on both sides and clear
[ ] "Encrypted" status is displayed
[ ] FLAG_SECURE blocks screenshots (PRO/PREMIUM)
[ ] RAM < 150 MB (idle), < 300 MB (call)
[ ] FREE: 15-min limit + 10-contact limit
```

> **Full QA checklist:** [docs/FINAL_QA_CHECKLIST.md](FINAL_QA_CHECKLIST.md)

### Checklist

- [ ] APK installed on device
- [ ] All critical tests passed
- [ ] No crash in Logcat

---

## Step 7: Google Play Developer Account

### 7.1 Create Account

1. Open https://play.google.com/console/signup
2. Log in with Google Account
3. Pay **25 EUR** registration fee (one-time)
4. Complete identity verification (may take 2-7 days)

> **Tip:** Start this step early due to wait time!

### 7.2 Configure Account

```
1. Play Console → Settings → Developer account
2. Website: https://neabouli.github.io/stealth/
3. Privacy Policy URL: https://neabouli.github.io/stealth/privacy.html
4. Support: https://github.com/NeaBouli/stealth/issues
```

### Checklist

- [ ] Account created and 25 EUR paid
- [ ] Identity verification completed
- [ ] Website and Privacy Policy URLs entered

---

## Step 8: Set Up Play Console

### 8.1 Create App

```
1. Play Console → "Create app"
2. App name: "SecureCall"
3. Default language: German (de-DE)
4. Free or paid: Free
5. Confirm declarations → "Create app"
```

### 8.2 Fill In Store Listing

Texts from `marketing/play_store/de/` and `marketing/play_store/en/`:

| Field | Source |
|-------|--------|
| App name | `title.txt` |
| Short description | `short_description.txt` |
| Full description | `full_description.txt` |
| App icon | `logo.png` (create 512x512 version) |
| Feature graphic | 1024x500 PNG (must be created) |
| Screenshots | Min. 2 phone screenshots (1080x1920) |

### 8.3 Content Rating + Data Safety

```
Content Rating: PEGI 3 (Communication, no violence/language)

Data Safety:
- Encryption: Yes, all data in transit
- FREE: Crash reports (Firebase, opt-out)
- PRO/PREMIUM: No data collected
- Data sharing: None
- Data deletion: In-app Settings
```

### 8.4 App Signing

```
1. Setup → App signing
2. "Use Google-generated key" (recommended)
3. Upload Key: keytool -export -keystore securecall-release-key.jks -alias securecall -rfc > upload_cert.pem
4. Upload upload_cert.pem
```

### Checklist

- [ ] App created in Play Console
- [ ] Store Listing complete (DE + EN)
- [ ] App Icon + Screenshots uploaded
- [ ] Content Rating filled in
- [ ] Data Safety filled in
- [ ] App Signing configured

---

## Step 9: Internal Testing + Beta

### 9.1 Internal Testing

```
1. Play Console → Testing → Internal testing
2. "Create new release" → Upload AAB
3. Create tester list (own email)
4. "Start rollout to Internal testing"
5. Open opt-in link → Install app from Play Store
```

### 9.2 Closed Beta (2-3 weeks)

```
1. Testing → Closed testing → "Create new release"
2. Upload AAB
3. Invite 10-20 testers
4. Collect feedback via GitHub Issues
5. Fix bugs, upload update AAB
```

### Exit Criteria (Beta → Production)

| Criterion | Target |
|-----------|--------|
| Crash-free Rate | > 99.5% |
| Critical Bugs | 0 |
| Audio Quality | >= 4/5 |
| Testers with successful call | > 80% |

### Checklist

- [ ] Internal Testing passed
- [ ] 10-20 beta testers active
- [ ] Feedback incorporated
- [ ] Exit criteria met

---

## Step 10: Production Release

### 10.1 Pre-Release

```
[ ] Version in build.gradle incremented (versionCode 3, versionName "1.0")
[ ] Git Tag: git tag -a v1.0 -m "v1.0 Production" && git push origin v1.0
[ ] Clean build of the Free Play AAB and all three direct APKs
[ ] Railway Server stable (Health Check OK)
```

### 10.2 Staged Rollout

```
1. Play Console → Production → "Create new release"
2. Upload AAB → Enter release notes
3. "Start rollout to Production" → 5%
4. Wait 2 days → Check crash rate
5. 10% → 25% → 50% → 100% (2 days each)
```

**Stop rollout if:** Crash rate > 1% or critical bugs

### Checklist

- [ ] Production Release started with 5% rollout
- [ ] Crash rate checked after 48h (< 1%)
- [ ] Rollout gradually increased to 100%
- [ ] App live in Play Store

---

## Step 11: Post-Launch

### Monitoring

- **Play Console:** Android Vitals → Crash Rate, ANR Rate
- **Railway:** Dashboard → Logs, Metrics
- **Metered.ca:** Dashboard → Usage (50 GB limit)

### Support

- **GitHub Issues:** https://github.com/NeaBouli/stealth/issues
- **Play Store Reviews:** Respond within 24h

### Marketing

```
Priority 1 (Launch Day):
- Reddit: r/privacy, r/Android (spread over 3-5 days)
- Twitter/X: Launch post

Priority 2 (Week 1-2):
- Hacker News: "Show HN" post
- Product Hunt Launch
- GitHub README with badges

Priority 3 (Month 1):
- German tech media: Heise, Golem, t3n, Kuketz Blog
- YouTube: Techlore, The Hated One
```

### Month 1 Goals

| Metric | Target |
|--------|--------|
| Downloads | 1,000+ |
| Rating | >= 4.0 stars |
| GitHub Stars | 500+ |
| Crash-free Rate | > 99.5% |

---

## Step 12: Enable GitHub Wiki

1. Repository Settings → Features → Enable **Wikis**
2. Copy wiki pages from `docs/WIKI/` (13 pages)
3. Detailed guide: [docs/ENABLE_WIKI.md](ENABLE_WIKI.md)

Fastest method via Git:
```bash
git clone https://github.com/NeaBouli/stealth.wiki.git
cp docs/WIKI/*.md stealth.wiki/
cd stealth.wiki && git add . && git commit -m "Add documentation" && git push
```

---

## Complete Launch Checklist

### Infrastructure (0 EUR)
- [ ] GitHub Pages enabled and website live
- [ ] Railway.app Signaling Server deployed
- [ ] Metered.ca TURN Server set up
- [ ] Health Check OK

### App Build
- [ ] Keystore generated and stored securely
- [ ] Release URLs entered in build.gradle
- [ ] Free Play AAB and all three direct APKs built and tested
- [ ] APK tested on real device

### Google Play (25 EUR)
- [ ] Developer Account created
- [ ] App created with Store Listing
- [ ] Internal Testing passed
- [ ] Beta Testing completed
- [ ] Production Release live

### Post-Launch
- [ ] Monitoring active (Play Console + Railway)
- [ ] GitHub Wiki with 13 documentation pages
- [ ] Marketing posts published
- [ ] GitHub Issues active as support channel
