# Hybrid-Migration Plan — StealthX Infrastructure

Datum: 2026-05-04
Status: ENTWURF — zur Diskussion

---

## Aktuelle Drittanbieter-Abhaengigkeiten

| Anbieter | Dienst | Kosten | Migrierbar? |
|----------|--------|--------|-------------|
| Railway | Backend Hosting (Node.js Signaling) | ~$5-20/mo | JA — eigener VPS |
| Metered.ca | TURN Relay Server | Pay-per-use | JA — eigener coturn |
| Brevo | Transaktions-Email (Primary) | Free Tier 300/Tag | JA — eigener Postfix/SMTP |
| Resend | Transaktions-Email (Fallback) | Free Tier 100/Tag | JA — eigener SMTP |
| Stripe | Payments / Checkout | 2.9% + €0.25 | NEIN — bleibt (kein Ersatz) |
| Firebase | FCM Push + Crashlytics | Free Tier | TEILWEISE — Push schwer ersetzbar |
| Reown/WC | WalletConnect Relay | Free | ENTFERNT — SIWE direkt |

---

## Empfohlene Hybrid-Architektur

```
┌─────────────────────────────────────────────────────────┐
│                    EIGENER SERVER                         │
│         (Hetzner CX33 oder dedicated VPS)                │
├─────────────────────────────────────────────────────────┤
│  1. Signaling Backend (Node.js)      — Primary           │
│  2. coturn TURN/STUN Server          — Primary           │
│  3. Postfix/SMTP Relay               — Primary Email     │
│  4. Monitoring (Uptime + Metrics)    — Grafana/Loki      │
│  5. Redis (optional — Session State) — fuer Persistence  │
└─────────────────────────────────────────────────────────┘
           │                    │
           │ Failover           │ Failover
           ▼                    ▼
┌──────────────────┐   ┌──────────────────┐
│  Railway         │   │  Brevo / Resend  │
│  (Signaling      │   │  (Email Fallback)│
│   Fallback)      │   │                  │
└──────────────────┘   └──────────────────┘

┌──────────────────────────────────────────┐
│         BLEIBT BEI DRITTANBIETER          │
├──────────────────────────────────────────┤
│  Stripe     — Payments (kein Ersatz)      │
│  Firebase   — FCM Push (kein Ersatz)      │
│  Firebase   — Crashlytics (optional)      │
│  GitHub     — Code + Releases + Pages     │
└──────────────────────────────────────────┘
```

---

## Migrations-Prioritaet

| Prio | Was | Warum | Aufwand |
|------|-----|-------|---------|
| 1 | **TURN → eigener coturn** | Kostenreduktion + volle Kontrolle + Privacy | 2-4h Setup |
| 2 | **Signaling → eigener VPS** | Unabhaengigkeit von Railway, persistentes Filesystem | 4-8h Migration |
| 3 | **Email → eigener SMTP** | Kein Limit, keine Drittanbieter-Abhaengigkeit | 2-3h Setup |
| 4 | **Monitoring → Grafana/Loki** | Besser als Railway Logs, langfristige Metriken | 4-6h Setup |
| 5 | **FCM → UnifiedPush/ntfy** | Privacy-Gewinn, aber groesserer Client-Aufwand | Langfristig |

---

## Detailplan pro Dienst

### 1. TURN Server (coturn) — Prioritaet 1

**Aktuell:** Metered.ca TURN (pay-per-use, Credentials via `/ice-servers`)
**Ziel:** Eigener coturn auf Hetzner (oder bestehendem Server 135.181.254.229)

**Setup:**
```bash
apt install coturn
# /etc/turnserver.conf:
listening-port=3478
tls-listening-port=5349
realm=turn.stealthx.tech
use-auth-secret
static-auth-secret=$TURN_SECRET  # generiert, in env
cert=/etc/letsencrypt/live/turn.stealthx.tech/fullchain.pem
pkey=/etc/letsencrypt/live/turn.stealthx.tech/privkey.pem
```

**DNS:** `turn.stealthx.tech` → Server IP
**Vorteil:** Keine Kosten pro Minute, volle Kontrolle, eigene Logs
**Risiko:** Single Point of Failure ohne Redundanz

### 2. Signaling Backend — Prioritaet 2

**Aktuell:** Railway (ephemeral FS, Auto-Deploy via GitHub)
**Ziel:** Docker Container auf eigenem VPS

**Anforderungen:**
- Node.js 20+
- Persistentes Volume fuer `data/` (activation_codes.json, sold_codes.json, fcm_tokens.json)
- WebSocket-faehiger Reverse Proxy (Traefik/nginx)
- SSL via Let's Encrypt
- Auto-Restart (systemd oder Docker restart policy)
- Health-Check: `GET /health`

**Docker Compose Entwurf:**
```yaml
services:
  signaling:
    image: node:20-slim
    working_dir: /app
    volumes:
      - ./backend/signaling:/app
      - signaling-data:/app/data
    environment:
      - ADMIN_API_KEY
      - STRIPE_SECRET_KEY
      - STRIPE_WEBHOOK_SECRET
      - ALLOWED_SIGNATURES
      - TURN_USER
      - TURN_PASS
    ports:
      - "8080:8080"
    command: node src/server.js
    restart: unless-stopped
volumes:
  signaling-data:
```

**Migration:**
1. Server vorbereiten (Docker, Traefik, DNS)
2. `data/` von Railway sichern (manuell kopieren)
3. Env vars uebertragen
4. Deploy + Smoke-Test
5. DNS `api.stealthx.tech` oder Railway-URL auf neuen Server zeigen
6. Railway als Cold-Standby behalten

### 3. Email SMTP — Prioritaet 3

**Aktuell:** Brevo (Primary) + Resend (Fallback)
**Ziel:** Eigener Postfix/Mailgun-Drop-in auf VPS

**Setup:**
- Postfix + DKIM + SPF + DMARC fuer `stealthx.tech`
- Oder: Mailcow (all-in-one) wenn Admin-UI gewuenscht
- Fallback: Brevo bleibt als sekundaerer Transport

**Vorteil:** Kein Rate Limit, keine Abhaengigkeit
**Risiko:** Deliverability (Spam-Filter bei Gmail/Outlook) — benoetigt Warmup

### 4. Monitoring — Prioritaet 4

**Aktuell:** Railway Logs (flüchtig, kein Alerting)
**Ziel:** Grafana + Loki + Prometheus auf eigenem Server

**Metriken:**
- Active WebSocket Connections
- Session Count (calls in progress)
- FCM Token Count
- TURN Usage
- Error Rate
- Memory/CPU

### 5. FCM Replacement — Langfristig

**Problem:** Firebase FCM ist der einzige zuverlaessige Push-Kanal fuer Android.
**Alternativen:**
- UnifiedPush (ntfy.sh) — Privacy-freundlich, aber Nutzer muss ntfy installieren
- WebSocket Wakeup — funktioniert nur wenn App im Vordergrund
- Eigener Push-Daemon — Battery-intensive, von Android gekillt

**Empfehlung:** FCM bleibt fuer v1.x. In v2.x UnifiedPush als OPTION anbieten.

---

## VPS Specs (Minimum)

| Ressource | Minimum | Empfohlen |
|-----------|---------|-----------|
| CPU | 2 vCPU | 4 vCPU |
| RAM | 4 GB | 8 GB |
| Disk | 40 GB SSD | 80 GB NVMe |
| Netzwerk | 1 Gbit/s, unmetered | 1 Gbit/s |
| OS | Ubuntu 24.04 LTS | Ubuntu 24.04 LTS |
| Standort | EU (GDPR) | Helsinki/Falkenstein |

**Hetzner CX33 (bestehend):** 2 vCPU, 8 GB RAM, 80 GB — ausreichend fuer alles.
Bereits vorhanden unter `135.181.254.229` (ekklesia.gr laeuft dort).

---

## Risiken

| Risiko | Mitigation |
|--------|-----------|
| Single Point of Failure | Railway als Cold-Standby, DNS Failover |
| TURN Bandwidth | Rate Limit pro User, Monitoring |
| Email Deliverability | SPF/DKIM/DMARC korrekt, Warmup-Phase |
| Wartungsaufwand | Docker + auto-update, Monitoring Alerts |
| DDoS | Cloudflare DNS Proxy oder Hetzner DDoS Protection |

---

## Zeitplan (Vorschlag)

| Woche | Aktion |
|-------|--------|
| 1 | coturn Setup + DNS + Test |
| 2 | Signaling Docker + Migration von Railway |
| 3 | Email SMTP + DKIM/SPF |
| 4 | Monitoring + Alerting |
| Spaeter | FCM Alternative evaluieren |

---

## Entscheidungen fuer Gio

1. Soll die Migration auf den bestehenden Hetzner (135.181.254.229) oder einen neuen Server?
2. Railway komplett kuendigen oder als Failover behalten?
3. coturn auf eigenem Server oder bei einem guenstigen TURN-Provider (z.B. Twilio)?
4. Email: eigener Postfix oder Mailcow?
5. Zeitrahmen: sofort starten oder nach Production Release?
