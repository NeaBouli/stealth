# Production Server Deployment Guide

Complete step-by-step guide for deploying SecureCall infrastructure.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    VPS Server                       │
│                                                     │
│  ┌──────────┐    ┌──────────────────┐              │
│  │  Nginx   │────│  Signaling (PM2) │              │
│  │  :443    │    │  :8080           │              │
│  └──────────┘    └──────────────────┘              │
│       │                                             │
│  ┌──────────┐    ┌──────────────────┐              │
│  │ Website  │    │  Coturn (TURN)   │              │
│  │ /opt/sc/ │    │  :3478, :5349    │              │
│  │ website/ │    │  :49152-49200    │              │
│  └──────────┘    └──────────────────┘              │
└─────────────────────────────────────────────────────┘
```

Two deployment options are available:
- **Option A: Bare-metal (PM2)** — `deployment/` directory (this guide)
- **Option B: Docker** — `deploy/` directory (see `docs/DEPLOYMENT_GUIDE.md`)

The Docker path is authoritative for new deployments and keeps the shared TURN
secret out of environment interpolation and the committed coturn template. The
bare-metal path remains a legacy option and must place the same private secret
in signaling and `/etc/turnserver.conf` through the operator's secret workflow.

## Server Requirements

| Spec | Minimum | Recommended |
|------|---------|-------------|
| OS | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |
| RAM | 2 GB | 4 GB |
| CPU | 1 vCore | 2 vCores |
| Storage | 20 GB SSD | 40 GB SSD |
| Bandwidth | 1 TB/mo | Unmetered |

### Recommended Providers

| Provider | Plan | Price | Notes |
|----------|------|-------|-------|
| Hetzner Cloud | CX21 | ~€5/mo | Best value, EU data centers |
| DigitalOcean | Basic | $6/mo | Good documentation |
| Vultr | Cloud Compute | $6/mo | Global locations |
| OVH | VPS Starter | €3.50/mo | Budget option, EU |

## Domain Setup

### 1. Purchase Domain

Purchase `neabouli.github.io/stealth` (or your domain) from a registrar.

### 2. DNS Records

Add these A records pointing to your VPS IP:

| Type | Host | Value | TTL |
|------|------|-------|-----|
| A | `@` | `YOUR_VPS_IP` | 300 |
| A | `www` | `YOUR_VPS_IP` | 300 |
| A | `signal` | `YOUR_VPS_IP` | 300 |
| A | `turn` | `YOUR_VPS_IP` | 300 |

### 3. Verify DNS

```bash
dig neabouli.github.io/stealth +short
dig signal.securecall.app +short
dig turn.securecall.app +short
```

All should return your VPS IP.

## Deployment Steps

### Step 1: Initial Server Setup

```bash
# SSH into server
ssh root@YOUR_VPS_IP

# Clone repository
git clone https://github.com/NeaBouli/stealth.git /opt/securecall-repo
cd /opt/securecall-repo

# Run setup script
bash deployment/setup_server.sh
```

This installs: Node.js 18, PM2, Nginx, Certbot, Coturn, UFW firewall.

### Step 2: Deploy Signaling Server

```bash
bash deployment/deploy_signaling.sh
```

The script never prints credential values. On first deploy it writes fresh
`TURN_PASS` and `ADMIN_API_KEY` values only into
`/opt/securecall/signaling/.env` (owner-only, mode `0600`); on later runs it
preserves the existing file and only re-enforces the permissions. Transfer
both values into your encrypted password manager through your restricted
operator workflow — do not print, log, or retain them in terminal output,
screenshots, or tickets.

Verify:
```bash
curl http://localhost:8080/
# Should return: {"status":"ok",...}

pm2 status
# Should show: securecall-signaling | online
```

### Step 3: Configure Nginx

```bash
# Copy configs
sudo cp deployment/nginx_config/signal.securecall.app.conf /etc/nginx/sites-available/
sudo cp deployment/nginx_config/neabouli.github.io/stealth.conf /etc/nginx/sites-available/

# Enable sites
sudo ln -sf /etc/nginx/sites-available/signal.securecall.app.conf /etc/nginx/sites-enabled/
sudo ln -sf /etc/nginx/sites-available/neabouli.github.io/stealth.conf /etc/nginx/sites-enabled/

# Remove default
sudo rm -f /etc/nginx/sites-enabled/default

# Deploy website
sudo cp -r website/ /opt/securecall/website/

# Test and reload
sudo nginx -t
sudo systemctl reload nginx
```

### Step 4: Install SSL Certificates

```bash
bash deployment/ssl_setup.sh admin@example.com
```

Verify:
```bash
curl -I https://signal.securecall.app/
# Should return: HTTP/2 200

curl -I https://neabouli.github.io/stealth/
# Should return: HTTP/2 200
```

### Step 5: Configure TURN Server

```bash
# Copy config
sudo cp deployment/coturn_config/turnserver.conf /etc/turnserver.conf

# Edit with your values
sudo nano /etc/turnserver.conf
```

Set:
- `external-ip=YOUR_VPS_IP`
- `static-auth-secret=<same private 64-hex value configured for signaling>`

Do not write `$TURN_SECRET` literally into the coturn file. For new systems,
prefer the Docker flow in `docs/DEPLOYMENT_GUIDE.md`, which renders this value
from `deploy/secrets/turn_secret` at container startup.

Uncomment TLS lines after SSL cert is obtained:
- `cert=/etc/letsencrypt/live/turn.securecall.app/fullchain.pem`
- `pkey=/etc/letsencrypt/live/turn.securecall.app/privkey.pem`

```bash
# Start coturn
sudo systemctl enable coturn
sudo systemctl restart coturn
sudo systemctl status coturn
```

### Step 6: Setup Monitoring

```bash
bash deployment/monitoring_setup.sh
```

### Step 7: Verify Everything

```bash
# Signaling health
curl https://signal.securecall.app/
# → {"status":"ok",...}

# Website
curl -I https://neabouli.github.io/stealth/
# → HTTP/2 200

# WebSocket test (requires wscat or websocat)
npx wscat -c wss://signal.securecall.app/signal
# → Connected

# TURN test
turnutils_uclient -t turn.securecall.app -u securecall -w YOUR_TURN_PASS
# → Allocation successful

# PM2 processes
pm2 status
# → securecall-signaling | online

# Firewall
sudo ufw status
# → Active, all ports configured
```

## Post-Deployment

### Monitoring

- **PM2 Dashboard**: `pm2 monit`
- **Logs**: `pm2 logs securecall-signaling`
- **Health checks**: Every 5 min via cron
- **UptimeRobot**: Set up free monitoring at https://uptimerobot.com
  - Monitor URL: `https://signal.securecall.app/`
  - Check interval: 5 minutes
  - Alert: Email/Slack

### Updates

```bash
cd /opt/securecall-repo
git pull origin main
bash deployment/deploy_signaling.sh
```

### Backup

The signaling server is stateless (in-memory only). Back up:
- `/opt/securecall/signaling/.env` (credentials)
- `/etc/letsencrypt/` (SSL certs)
- `/etc/turnserver.conf` (TURN config)

These contain private material. Store backups encrypted and access-controlled;
the Docker backup script intentionally excludes `deploy/secrets/turn_secret`.

### Security Hardening

- [ ] SSH key-only auth (disable password login)
- [ ] fail2ban configured and running
- [ ] Unattended security updates enabled
- [ ] Non-root user for all operations
- [ ] ADMIN_API_KEY set to a strong random value
- [ ] TURN secret is unique and strong
- [ ] Regular system updates scheduled

## Credential Storage

> **WARNING**: Never commit credentials to git, and never print them to
> terminal, provisioner, or CI output.

`deployment/deploy_signaling.sh` never emits credential values: fresh
`TURN_PASS` / `ADMIN_API_KEY` values exist only in
`/opt/securecall/signaling/.env` (mode `0600`, owner-only). Read them from
that restricted file only inside your operator workflow and store them in an
encrypted password manager; keep every backup encrypted and
access-controlled.

| Credential | Location | Purpose |
|------------|----------|---------|
| TURN_PASS | `/opt/securecall/signaling/.env` | TURN authentication |
| ADMIN_API_KEY | `/opt/securecall/signaling/.env` | Admin API access |
| SSH key | Local machine | Server access |
| SSL cert email | Let's Encrypt | Certificate renewal |
| VPS login | Provider dashboard | Server management |

## Troubleshooting

| Issue | Solution |
|-------|---------|
| PM2 process offline | `pm2 restart securecall-signaling` |
| Nginx 502 Bad Gateway | Check PM2: `pm2 status`, restart if needed |
| SSL cert expired | `sudo certbot renew` |
| TURN not working | Check UFW allows UDP 3478, check coturn logs |
| WebSocket timeout | Check Nginx proxy timeouts |
| Can't SSH | Check UFW allows port 22, check fail2ban |
| Disk full | `pm2 flush` to clear logs, `docker system prune` |
