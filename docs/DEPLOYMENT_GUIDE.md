# SecureCall Deployment Guide

Complete guide for deploying the SecureCall backend infrastructure on a VPS.

## Architecture Overview

```
                          ┌──────────────┐
                          │   Internet   │
                          └──────┬───────┘
                                 │
                    ┌────────────┼────────────┐
                    │            │             │
              ┌─────▼──┐  ┌─────▼──┐    ┌─────▼──┐
              │ :443   │  │ :443   │    │ :3478  │
              │ stealthx│  │ signal.│    │ turn.  │
              │ .app   │  │ secure │    │ secure │
              │(Website)│  │ call   │    │ call   │
              └────┬───┘  │ .app   │    │ .app   │
                   │      │(Signal)│    │(TURN)  │
                   │      └───┬────┘    └────────┘
              ┌────▼───┐  ┌───▼────┐    ┌────────┐
              │ Nginx  │  │ Nginx  │    │ coturn │
              │ Static │  │ Proxy  │    │        │
              └────────┘  └───┬────┘    └────────┘
                          ┌───▼────┐
                          │Signaling│
                          │ :8080  │
                          └────────┘
```

## Prerequisites

- **VPS**: Ubuntu 22.04 LTS (recommended), 2 vCPU, 2 GB RAM minimum
- **Domains**:
  - `stealthx.app` (landing page)
  - `signal.securecall.app` (signaling server)
  - `turn.securecall.app` (TURN server)
- **DNS access** for A/AAAA records
- **Docker** and **Docker Compose** installed

## Step 1: VPS Setup

### 1.1 Initial Server Setup

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install essentials
sudo apt install -y curl git ufw fail2ban

# Create non-root user (if not exists)
sudo adduser securecall
sudo usermod -aG sudo securecall
su - securecall
```

### 1.2 Firewall Configuration

```bash
# Allow SSH
sudo ufw allow 22/tcp

# Allow HTTP/HTTPS (Nginx)
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Allow TURN server
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
sudo ufw allow 5349/tcp
sudo ufw allow 5349/udp

# Allow TURN relay ports
sudo ufw allow 49152:49200/udp

# Enable firewall
sudo ufw enable
sudo ufw status
```

### 1.3 Install Docker

```bash
# Install Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker

# Install Docker Compose
sudo apt install -y docker-compose-plugin

# Verify
docker --version
docker compose version
```

### 1.4 Harden SSH

```bash
# Edit SSH config
sudo nano /etc/ssh/sshd_config
```

Set:
```
PermitRootLogin no
PasswordAuthentication no
PubkeyAuthentication yes
MaxAuthTries 3
```

```bash
sudo systemctl restart sshd
```

## Step 2: DNS Configuration

Add these DNS records at your domain registrar:

### For stealthx.app

| Type | Name | Value | TTL |
|------|------|-------|-----|
| A | @ | YOUR_VPS_IP | 300 |
| A | www | YOUR_VPS_IP | 300 |

### For securecall.app

| Type | Name | Value | TTL |
|------|------|-------|-----|
| A | signal | YOUR_VPS_IP | 300 |
| A | turn | YOUR_VPS_IP | 300 |

Verify DNS propagation:
```bash
dig signal.securecall.app +short
dig stealthx.app +short
```

## Step 3: Clone & Configure

### 3.1 Clone Repository

```bash
cd /opt
sudo mkdir securecall && sudo chown $USER:$USER securecall
cd securecall
git clone https://github.com/stealthx/securecall.git .
```

### 3.2 Configure Environment

```bash
cd deploy
cp .env.example .env
nano .env
```

Fill in:
```bash
DOMAIN=securecall.app
SIGNAL_DOMAIN=signal.securecall.app
TURN_DOMAIN=turn.securecall.app
WEBSITE_DOMAIN=stealthx.app
ADMIN_EMAIL=admin@example.com

# Generate secure values:
ADMIN_API_KEY=$(openssl rand -hex 32)
TURN_USER=securecall
TURN_PASS=$(openssl rand -hex 24)

echo "Admin API Key: $ADMIN_API_KEY"
echo "TURN Password: $TURN_PASS"
```

### 3.3 Configure TURN Server

```bash
# Edit coturn config with your VPS IP
nano coturn/turnserver.conf
```

Uncomment and set:
```
external-ip=YOUR_VPS_PUBLIC_IP
user=securecall:YOUR_TURN_PASSWORD
```

## Step 4: SSL Certificates (Let's Encrypt)

### 4.1 Initial Certificate Generation

Before starting Nginx with SSL, get certificates first:

```bash
# Create certbot directories
mkdir -p certbot/conf certbot/www

# Get certificates (standalone mode, before Nginx starts)
docker run --rm -p 80:80 \
  -v $(pwd)/certbot/conf:/etc/letsencrypt \
  -v $(pwd)/certbot/www:/var/www/certbot \
  certbot/certbot certonly \
  --standalone \
  --email admin@example.com \
  --agree-tos \
  --no-eff-email \
  -d signal.securecall.app

docker run --rm -p 80:80 \
  -v $(pwd)/certbot/conf:/etc/letsencrypt \
  -v $(pwd)/certbot/www:/var/www/certbot \
  certbot/certbot certonly \
  --standalone \
  --email admin@example.com \
  --agree-tos \
  --no-eff-email \
  -d stealthx.app -d www.stealthx.app
```

### 4.2 Auto-Renewal

The `certbot` container in docker-compose handles automatic renewal every 12 hours. To manually renew:

```bash
docker compose run --rm certbot renew
docker compose exec nginx nginx -s reload
```

## Step 5: Deploy

### 5.1 Start All Services

```bash
cd /opt/securecall/deploy

# Build and start
docker compose up -d --build

# Check status
docker compose ps
docker compose logs -f signaling
```

### 5.2 Verify Deployment

```bash
# Health check
curl https://signal.securecall.app/

# Test WebSocket (requires wscat)
npx wscat -c wss://signal.securecall.app/signal

# Check website
curl -I https://stealthx.app/

# Check TURN
turnutils_uclient -t -u securecall -w YOUR_TURN_PASS turn.securecall.app
```

## Step 6: Monitoring

### 6.1 Health Check Script

A health check script is provided at `deploy/scripts/healthcheck.sh`. Set up a cron job:

```bash
# Run health check every 5 minutes
crontab -e
*/5 * * * * /opt/securecall/deploy/scripts/healthcheck.sh >> /var/log/securecall-health.log 2>&1
```

### 6.2 Log Monitoring

```bash
# View signaling logs
docker compose logs -f signaling

# View nginx access logs
docker compose exec nginx cat /var/log/nginx/access.log

# View all logs
docker compose logs --tail=100
```

### 6.3 Resource Monitoring

```bash
# Docker resource usage
docker stats

# Disk usage
df -h
docker system df
```

## Step 7: Maintenance

### Update Application

```bash
cd /opt/securecall
git pull origin main
cd deploy
docker compose up -d --build signaling
```

### Restart Services

```bash
docker compose restart signaling
docker compose restart nginx
```

### Backup

There is no persistent database to back up. The signaling server is stateless (in-memory only). Back up:
- `.env` file
- SSL certificates (`certbot/conf/`)
- TURN server config

### Scale

For higher load, increase resources and adjust:
```bash
# In docker-compose.yml, add resource limits:
deploy:
  resources:
    limits:
      memory: 512M
      cpus: '1.0'
```

## Troubleshooting

| Issue | Solution |
|-------|---------|
| SSL cert not found | Run certbot standalone first (Step 4.1) |
| WebSocket 502 | Check signaling container is running: `docker compose ps` |
| TURN not working | Check firewall allows UDP 3478 and 49152-49200 |
| Website 403 | Check website directory is mounted correctly |
| Container crash loop | Check logs: `docker compose logs signaling` |
| DNS not resolving | Wait for propagation (up to 48h) or check A records |
| Port conflict | Check if another service uses 80/443: `sudo lsof -i :80` |

## Security Checklist

- [ ] SSH key-only authentication
- [ ] Firewall enabled (UFW)
- [ ] Fail2ban active
- [ ] Non-root Docker user
- [ ] Admin API key set in `.env`
- [ ] TURN credentials set (not defaults)
- [ ] SSL certificates active
- [ ] Automatic cert renewal working
- [ ] No sensitive data in git
- [ ] Regular system updates scheduled
