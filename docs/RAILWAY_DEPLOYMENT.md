# SecureCall — Railway.app Deployment (Free)

> Deploy the Signaling Server on Railway.app — 0 EUR/month on the Free Tier.

## Costs

| Plan | Price | Limits |
|------|-------|--------|
| **Trial** | $0 | $5 Credit, 500h Execution, 512 MB RAM |
| Hobby | $5/month | $5 Credit incl., 8 GB RAM, no sleep |

> The Trial plan is sufficient to get started. Upgrade to Hobby ($5/mo) when the Trial is used up.

---

## Step 1: Create Railway Account

1. Open https://railway.com
2. Click "Start a New Project"
3. Log in with **GitHub** (NeaBouli Account)
4. Authorize GitHub access

## Step 2: Create New Project

1. Dashboard → "New Project"
2. Select "Deploy from GitHub repo"
3. Repository: `NeaBouli/stealth`
4. Click "Add Service"

## Step 3: Configure Service

Railway automatically detects the `railway.json` in the `backend/signaling/` directory.

**If not automatically detected — configure manually:**

1. Service Settings → Source
2. **Root Directory:** `backend/signaling`
3. **Build Command:** `npm ci --production`
4. **Start Command:** `node src/server.js`

## Step 4: Set Environment Variables

Service → Variables → "New Variable":

| Variable | Value | Description |
|----------|-------|-------------|
| `NODE_ENV` | `production` | Production mode |
| `PORT` | `${{RAILWAY_PORT}}` | Automatically set by Railway |
| `TURN_SECRET` | `[generate]` | Run `openssl rand -hex 32` locally |
| `CORS_ORIGIN` | `https://neabouli.github.io` | GitHub Pages Domain |

> Railway sets `PORT` automatically. The app must listen on `process.env.PORT`.

## Step 5: Start Deploy

1. Click "Deploy" — Railway builds and starts the server
2. Watch build logs (takes ~1-2 minutes)
3. After successful deploy: green status

## Step 6: Enable Public Domain

1. Service → Settings → Networking
2. Click "Generate Domain"
3. Railway generates a URL like: `securecall-signaling-production.up.railway.app`
4. Copy this URL — it is needed for the Android App

**Custom Domain (optional):**
1. "Add Custom Domain" → enter `signal.securecall.app`
2. Set DNS CNAME Record: `signal.securecall.app → [railway-domain].up.railway.app`

## Step 7: Verify Health Check

```bash
curl https://[YOUR-RAILWAY-URL].up.railway.app/health
# Expected response: {"status":"ok"}
```

## Step 8: Update Android App URLs

In `client_android/app/build.gradle` adjust the release URL:

```groovy
release {
    buildConfigField "String", "SIGNAL_WS_URL",
        "\"wss://[YOUR-RAILWAY-URL].up.railway.app/signal\""
}
```

---

## Monitoring

- **Dashboard:** https://railway.com/dashboard → Project → Service
- **Logs:** Service → Logs (real-time logs)
- **Metrics:** Service → Metrics (CPU, RAM, network)
- **Alerts:** Settings → Notifications (email on error)

## Troubleshooting

### Server does not start
```
Service → Deployments → Last Deployment → Check Build Logs
```
Common causes:
- `package.json` missing in Root Directory
- Node.js version too old (≥18 required)
- Environment Variables missing

### WebSocket connection fails
- Check if CORS_ORIGIN is set correctly
- Railway supports WebSocket natively — no extra setup needed
- URL must use `wss://` (not `ws://`)

### Service goes to sleep (Trial Plan)
- Trial plan has 500h/month execution time
- Service goes to sleep after ~15 minutes of inactivity
- First request after sleep takes ~5-10 seconds (cold start)
- Upgrade to Hobby ($5/mo) for 24/7 operation

---

## Cost Comparison

| Option | Cost | Uptime |
|--------|------|--------|
| **Railway Trial** | $0/mo | ~500h, cold starts |
| Railway Hobby | $5/mo | 24/7, no sleep |
| Hetzner VPS | €4.35/mo | 24/7, self-managed |
| DigitalOcean | $6/mo | 24/7, self-managed |
