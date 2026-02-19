# Backend Production Deployment Guide

## Railway.app Setup (5 Minuten)

### 1. Account erstellen
- Gehe zu: https://railway.app
- Login mit GitHub Account (empfohlen)
- Keine Kreditkarte noetig fuer GRATIS Tier

### 2. Neues Projekt erstellen
- Click: "New Project"
- Waehle: "Deploy from GitHub repo"
- Suche: "NeaBouli/stealth"
- Select: backend/signaling Directory
- Railway erkennt automatisch Node.js

### 3. Environment Variables setzen

In Railway Dashboard -> Variables Tab:

```
NODE_ENV=production
PORT=8080
STUN_URL=stun:stun.l.google.com:19302
TURN_URL=turn:a.relay.metered.ca:443?transport=tcp
TURN_USER=[SIEHE METERED.CA UNTEN]
TURN_PASS=[SIEHE METERED.CA UNTEN]
ADMIN_API_KEY=[GENERIERE RANDOM STRING]
```

ADMIN_API_KEY generieren:
```bash
openssl rand -base64 32
```

### 4. Domain notieren

Nach Deploy zeigt Railway eine URL:
```
https://[dein-app-name].up.railway.app
```

Notiere diese URL! Format:
- REST API: `https://[app].up.railway.app`
- WebSocket: `wss://[app].up.railway.app/signal`

### 5. Deployment testen

```bash
# Health Check
curl https://[dein-app-name].up.railway.app/health

# Sollte zurueckgeben:
# {"status":"ok","uptime":123,"timestamp":"2026-02-19..."}
```

---

## Metered.ca TURN Server Setup (3 Minuten)

### 1. Account erstellen
- Gehe zu: https://www.metered.ca/stun-turn
- Sign up (Email + Passwort)
- GRATIS: 50 GB/Monat

### 2. Credentials holen

Nach Login -> Dashboard:
```
TURN Server: a.relay.metered.ca:443
Username: [DEIN_USERNAME]
Credential: [DEIN_PASSWORD]
```

### 3. In Railway.app setzen

Zurueck zu Railway -> Variables:
```
TURN_URL=turn:a.relay.metered.ca:443?transport=tcp
TURN_USER=[USERNAME von Metered]
TURN_PASS=[CREDENTIAL von Metered]
```

Click "Redeploy" nach Aenderungen.

### 4. TURN Server testen

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

## Android App konfigurieren

### 1. Update build.gradle

In `client_android/app/build.gradle`, BuildConfig fuer Release:
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
- Logs in Echtzeit: Railway Dashboard -> Deployments -> Logs
- Metriken: CPU, RAM, Network Usage
- Restart: Deployments -> ... -> Restart

### Health Checks
- Setup UptimeRobot (GRATIS): https://uptimerobot.com
- Monitor URL: `https://[app].up.railway.app/health`
- Alert via Email bei Downtime

### Metrics Endpoint
```bash
curl https://[app].up.railway.app/metrics
# Returns: memory usage, uptime, active connections, sessions
```

---

## Kosten

**Railway.app FREE Tier:**
- 500 Stunden/Monat (~20 Tage 24/7)
- 512 MB RAM
- 1 GB Disk
- Unlimited Deployments

**Metered.ca FREE Tier:**
- 50 GB TURN Traffic/Monat
- ~500-1000 Anrufe/Monat

**TOTAL: 0 EUR/Monat fuer Testing & erste User**

## Upgrade spaeter (bei Erfolg)

- Railway.app Hobby Plan: $5/Monat (unlimited Stunden)
- Metered.ca Pro: $29/Monat (500 GB)
