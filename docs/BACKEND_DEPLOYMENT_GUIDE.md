# Backend Production Deployment Guide

## Railway.app Setup (5 Minutes)

### 1. Create Account
- Go to: https://railway.app
- Log in with GitHub account (recommended)
- No credit card required for FREE tier

### 2. Create New Project
- Click: "New Project"
- Select: "Deploy from GitHub repo"
- Search: "NeaBouli/stealth"
- Select: backend/signaling Directory
- Railway automatically detects Node.js

### 3. Set Environment Variables

In Railway Dashboard -> Variables Tab:

```
NODE_ENV=production
PORT=8080
STUN_URL=stun:stun.l.google.com:19302
TURN_URL=turn:a.relay.metered.ca:443?transport=tcp
TURN_USER=[SEE METERED.CA BELOW]
TURN_PASS=[SEE METERED.CA BELOW]
ADMIN_API_KEY=[GENERATE RANDOM STRING]
```

Generate ADMIN_API_KEY:
```bash
openssl rand -base64 32
```

### 4. Note the Domain

After deployment, Railway shows a URL:
```
https://[your-app-name].up.railway.app
```

Note this URL! Format:
- REST API: `https://[app].up.railway.app`
- WebSocket: `wss://[app].up.railway.app/signal`

### 5. Test Deployment

```bash
# Health Check
curl https://[your-app-name].up.railway.app/health

# Should return:
# {"status":"ok","uptime":123,"timestamp":"2026-02-19..."}
```

---

## Metered.ca TURN Server Setup (3 Minutes)

### 1. Create Account
- Go to: https://www.metered.ca/stun-turn
- Sign up (Email + Password)
- FREE: 50 GB/month

### 2. Get Credentials

After login -> Dashboard:
```
TURN Server: a.relay.metered.ca:443
Username: [YOUR_USERNAME]
Credential: [YOUR_PASSWORD]
```

### 3. Set in Railway.app

Back to Railway -> Variables:
```
TURN_URL=turn:a.relay.metered.ca:443?transport=tcp
TURN_USER=[USERNAME from Metered]
TURN_PASS=[CREDENTIAL from Metered]
```

Click "Redeploy" after changes.

### 4. Test TURN Server

```bash
# Install turnutils (macOS)
brew install coturn

# Test TURN
turnutils_uclient -v \
  -u [TURN_USER] \
  -w [TURN_PASS] \
  a.relay.metered.ca
```

---

## Configure Android App

### 1. Update build.gradle

In `client_android/app/build.gradle`, BuildConfig for Release:
```gradle
buildTypes {
    release {
        buildConfigField "String", "SERVER_URL", "\"wss://[YOUR-APP-NAME].up.railway.app/signal\""
        buildConfigField "String", "STUN_SERVER", "\"stun:stun.l.google.com:19302\""
        buildConfigField "String", "TURN_SERVER", "\"turn:a.relay.metered.ca:443?transport=tcp\""
    }
}
```

### 2. Rebuild APKs

```bash
cd client_android
./gradlew assembleFreeRelease assembleProRelease assemblePremiumRelease
```

---

## Monitoring & Logs

### Railway Dashboard
- Real-time logs: Railway Dashboard -> Deployments -> Logs
- Metrics: CPU, RAM, Network Usage
- Restart: Deployments -> ... -> Restart

### Health Checks
- Set up UptimeRobot (FREE): https://uptimerobot.com
- Monitor URL: `https://[app].up.railway.app/health`
- Email alert on downtime

### Metrics Endpoint
```bash
curl https://[app].up.railway.app/metrics
# Returns: memory usage, uptime, active connections, sessions
```

---

## Costs

**Railway.app FREE Tier:**
- 500 hours/month (~20 days 24/7)
- 512 MB RAM
- 1 GB Disk
- Unlimited Deployments

**Metered.ca FREE Tier:**
- 50 GB TURN traffic/month
- ~500-1000 calls/month

**TOTAL: EUR 0/month for testing & initial users**

## Upgrade Later (on success)

- Railway.app Hobby Plan: $5/month (unlimited hours)
- Metered.ca Pro: $29/month (500 GB)
