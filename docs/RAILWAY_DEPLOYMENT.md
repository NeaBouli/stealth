# SecureCall — Railway.app Deployment (Gratis)

> Deploy des Signaling Servers auf Railway.app — 0 EUR/Monat im Free Tier.

## Kosten

| Plan | Preis | Limits |
|------|-------|--------|
| **Trial** | $0 | $5 Credit, 500h Execution, 512 MB RAM |
| Hobby | $5/Monat | $5 Credit inkl., 8 GB RAM, keine Sleep |

> Der Trial-Plan reicht zum Starten. Upgrade auf Hobby ($5/mo) wenn der Trial aufgebraucht ist.

---

## Schritt 1: Railway Account erstellen

1. Öffne https://railway.com
2. "Start a New Project" klicken
3. Mit **GitHub** einloggen (NeaBouli Account)
4. GitHub-Zugriff autorisieren

## Schritt 2: Neues Projekt erstellen

1. Dashboard → "New Project"
2. "Deploy from GitHub repo" wählen
3. Repository: `NeaBouli/stealth`
4. "Add Service" klicken

## Schritt 3: Service konfigurieren

Railway erkennt automatisch die `railway.json` im `backend/signaling/` Verzeichnis.

**Falls nicht automatisch erkannt — manuell konfigurieren:**

1. Service Settings → Source
2. **Root Directory:** `backend/signaling`
3. **Build Command:** `npm ci --production`
4. **Start Command:** `node src/server.js`

## Schritt 4: Environment Variables setzen

Service → Variables → "New Variable":

| Variable | Wert | Beschreibung |
|----------|------|--------------|
| `NODE_ENV` | `production` | Production-Modus |
| `PORT` | `${{RAILWAY_PORT}}` | Automatisch von Railway |
| `TURN_SECRET` | `[generieren]` | `openssl rand -hex 32` lokal ausführen |
| `CORS_ORIGIN` | `https://neabouli.github.io` | GitHub Pages Domain |

> Railway setzt `PORT` automatisch. Die App muss auf `process.env.PORT` hören.

## Schritt 5: Deploy starten

1. "Deploy" klicken — Railway baut und startet den Server
2. Build-Logs beobachten (dauert ~1-2 Minuten)
3. Nach erfolgreichem Deploy: grüner Status

## Schritt 6: Public Domain aktivieren

1. Service → Settings → Networking
2. "Generate Domain" klicken
3. Railway generiert eine URL wie: `securecall-signaling-production.up.railway.app`
4. Diese URL kopieren — wird für die Android App benötigt

**Custom Domain (optional):**
1. "Add Custom Domain" → `signal.securecall.app` eingeben
2. DNS CNAME Record setzen: `signal.securecall.app → [railway-domain].up.railway.app`

## Schritt 7: Health Check verifizieren

```bash
curl https://[DEINE-RAILWAY-URL].up.railway.app/health
# Erwartete Antwort: {"status":"ok"}
```

## Schritt 8: Android App URLs updaten

In `client_android/app/build.gradle` die Release-URL anpassen:

```groovy
release {
    buildConfigField "String", "SIGNAL_WS_URL",
        "\"wss://[DEINE-RAILWAY-URL].up.railway.app/signal\""
}
```

---

## Monitoring

- **Dashboard:** https://railway.com/dashboard → Projekt → Service
- **Logs:** Service → Logs (Echtzeit-Logs)
- **Metrics:** Service → Metrics (CPU, RAM, Netzwerk)
- **Alerts:** Settings → Notifications (E-Mail bei Fehler)

## Troubleshooting

### Server startet nicht
```
Service → Deployments → Letztes Deployment → Build Logs prüfen
```
Häufige Ursachen:
- `package.json` fehlt im Root Directory
- Node.js Version zu alt (≥18 benötigt)
- Environment Variables fehlen

### WebSocket Verbindung schlägt fehl
- Prüfe ob CORS_ORIGIN korrekt gesetzt ist
- Railway unterstützt WebSocket nativ — kein Extra-Setup nötig
- URL muss `wss://` verwenden (nicht `ws://`)

### Service schläft ein (Trial Plan)
- Trial-Plan hat 500h/Monat Execution Time
- Bei Inaktivität schläft der Service nach ~15 Minuten ein
- Erster Request nach Sleep dauert ~5-10 Sekunden (Cold Start)
- Upgrade auf Hobby ($5/mo) für 24/7 Betrieb

---

## Kosten-Vergleich

| Option | Kosten | Uptime |
|--------|--------|--------|
| **Railway Trial** | $0/mo | ~500h, Cold Starts |
| Railway Hobby | $5/mo | 24/7, kein Sleep |
| Hetzner VPS | €4.35/mo | 24/7, Self-Managed |
| DigitalOcean | $6/mo | 24/7, Self-Managed |
