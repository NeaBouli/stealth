# SecureCall — Complete Launch Guide

> **Phase 14: Final Step-by-Step Anleitung zum Production Launch**
>
> Dieses Dokument enthält ALLE manuellen Schritte in der richtigen Reihenfolge,
> um SecureCall von der Entwicklung in die Production zu bringen.

---

## Übersicht & Timeline

| Woche | Phase | Aufgabe |
|-------|-------|---------|
| **1** | Infrastruktur | Domain kaufen, VPS aufsetzen, SSL, TURN, Website deployen |
| **1** | App Build | Keystore generieren, Environment Vars, Release AABs bauen |
| **2** | Testing | Manual QA auf echtem Gerät, Bugfixes |
| **2** | Play Store | Developer Account, 3 Apps anlegen, Internal Testing |
| **3–4** | Beta | Closed Beta mit 10–20 Testern, Feedback einarbeiten |
| **5** | Launch | Production Release, Marketing, Monitoring |

### Kosten-Übersicht

| Posten | Kosten | Typ |
|--------|--------|-----|
| Domain `stealthx.app` | ~12–15 €/Jahr | Jährlich |
| VPS (Hetzner CX22) | 4,35 €/Monat | Monatlich |
| Google Play Developer | 25 € | Einmalig |
| SSL (Let's Encrypt) | 0 € | Kostenlos |
| Netlify (Website) | 0 € | Free Tier |
| **Gesamt Start** | **~42 €** | |
| **Laufend** | **~4,35 €/Monat** | |

---

## Schritt 1: Website Deployment

### Option A: Netlify (Empfohlen)

Netlify bietet automatische Deploys, kostenloses SSL, und globales CDN.

**1.1** Account erstellen: https://app.netlify.com/signup

**1.2** Neues Projekt verbinden:
```
1. "Add new site" → "Import an existing project"
2. "Deploy with GitHub" wählen
3. Repository: nicokimmel/stealth (oder euer Repo)
4. Branch: main
5. Base directory: website
6. Build command: (leer lassen — statische Seite)
7. Publish directory: website
8. "Deploy site" klicken
```

**1.3** Custom Domain einrichten:
```
1. Site settings → Domain management → Add custom domain
2. "stealthx.app" eingeben
3. DNS-Einstellungen folgen (siehe Schritt 2)
```

**1.4** SSL aktivieren:
```
1. Domain management → HTTPS
2. "Verify DNS configuration"
3. "Provision certificate" — Netlify erstellt automatisch ein Let's Encrypt Zertifikat
4. "Force HTTPS" aktivieren
```

**1.5** Verifizieren:
```bash
curl -I https://stealthx.app
# Erwartete Antwort: HTTP/2 200
# Header: x-powered-by: Netlify
```

### Option B: GitHub Pages

**1.1** Repository Settings öffnen:
```
1. GitHub → Repository → Settings → Pages
2. Source: "Deploy from a branch"
3. Branch: main
4. Folder: /website
5. Save
```

**1.2** Custom Domain:
```
1. Custom domain: stealthx.app
2. "Enforce HTTPS" aktivieren
```

**1.3** CNAME-Datei erstellen:
```bash
echo "stealthx.app" > website/CNAME
git add website/CNAME && git commit -m "chore: add CNAME for GitHub Pages" && git push
```

### Checkliste Website Deployment

- [ ] Website ist unter https://stealthx.app erreichbar
- [ ] HTTPS funktioniert (Schloss-Symbol im Browser)
- [ ] Alle Seiten laden: /, /privacy.html, /security.html, /faq.html
- [ ] 404-Seite funktioniert (beliebige URL testen)
- [ ] Mobile Ansicht funktioniert (Chrome DevTools → Responsive)
- [ ] robots.txt erreichbar: https://stealthx.app/robots.txt
- [ ] sitemap.xml erreichbar: https://stealthx.app/sitemap.xml

---

## Schritt 2: Domain kaufen & DNS konfigurieren

### 2.1 Domain registrieren

**Empfohlene Registrare:**

| Registrar | Preis .app | Link |
|-----------|-----------|------|
| Namecheap | ~12 €/Jahr | https://www.namecheap.com |
| Cloudflare | ~11 €/Jahr | https://dash.cloudflare.com |
| Google Domains → Squarespace | ~14 €/Jahr | https://domains.squarespace.com |
| INWX (deutsch) | ~15 €/Jahr | https://www.inwx.de |

> **Hinweis:** `.app` Domains erzwingen HTTPS (HSTS preloaded). Perfekt für ein Security-Produkt.

**Domain kaufen:**
```
1. Registrar öffnen
2. Nach "stealthx.app" suchen
3. In den Warenkorb → Kaufen
4. WHOIS Privacy aktivieren (kostenlos bei Cloudflare/Namecheap)
5. Auto-Renewal aktivieren
```

### 2.2 DNS Records konfigurieren

Folgende DNS Records beim Registrar eintragen:

**Für Netlify:**
```
Typ     Name       Wert                              TTL
──────────────────────────────────────────────────────────
A       @          75.2.60.5                          300
CNAME   www        [dein-site-name].netlify.app       300
A       signal     [VPS IP-Adresse]                   300
A       turn       [VPS IP-Adresse]                   300
```

> Die Netlify IP (75.2.60.5) wird in den Netlify DNS-Einstellungen angezeigt.

**Für GitHub Pages:**
```
Typ     Name       Wert                              TTL
──────────────────────────────────────────────────────────
A       @          185.199.108.153                    300
A       @          185.199.109.153                    300
A       @          185.199.110.153                    300
A       @          185.199.111.153                    300
CNAME   www        [username].github.io               300
A       signal     [VPS IP-Adresse]                   300
A       turn       [VPS IP-Adresse]                   300
```

**Für den VPS (Signaling + TURN):**
```
Typ     Name       Wert                              TTL
──────────────────────────────────────────────────────────
A       signal     [VPS IP-Adresse]                   300
A       turn       [VPS IP-Adresse]                   300
```

### 2.3 DNS Propagation prüfen

```bash
# DNS Auflösung testen
dig stealthx.app +short
dig signal.stealthx.app +short
dig turn.stealthx.app +short

# Oder online: https://dnschecker.org
```

### Checkliste DNS

- [ ] Domain `stealthx.app` registriert
- [ ] WHOIS Privacy aktiviert
- [ ] Auto-Renewal aktiviert
- [ ] A-Record für `@` zeigt auf Website-Hoster
- [ ] A-Record für `signal` zeigt auf VPS IP
- [ ] A-Record für `turn` zeigt auf VPS IP
- [ ] DNS-Auflösung funktioniert (dig bestätigt)
- [ ] Website erreichbar über Domain

---

## Schritt 3: VPS kaufen & Production Server deployen

### 3.1 VPS bestellen

**Empfohlene Anbieter:**

| Anbieter | Plan | vCPU | RAM | Speicher | Preis | Link |
|----------|------|------|-----|----------|-------|------|
| **Hetzner CX22** | Shared | 2 | 4 GB | 40 GB | 4,35 €/mo | https://console.hetzner.cloud |
| Hetzner CX32 | Shared | 4 | 8 GB | 80 GB | 8,45 €/mo | https://console.hetzner.cloud |
| DigitalOcean | Basic | 2 | 2 GB | 50 GB | $12/mo | https://cloud.digitalocean.com |
| Netcup VPS 1000 | Shared | 2 | 2 GB | 64 GB | 4,35 €/mo | https://www.netcup.de |

> **Empfehlung:** Hetzner CX22 in Falkenstein oder Nürnberg (DE) — bestes Preis-Leistungs-Verhältnis, DSGVO-konform.

**VPS erstellen:**
```
1. Hetzner Cloud Console öffnen
2. "Create Server" klicken
3. Location: Falkenstein (DE)
4. Image: Ubuntu 24.04 LTS
5. Type: CX22 (2 vCPU, 4 GB RAM)
6. SSH Key: Eigenen Public Key hochladen
7. Name: securecall-prod
8. "Create & Buy Now"
9. IP-Adresse notieren → [VPS_IP]
```

### 3.2 SSH-Zugang einrichten

```bash
# SSH Key generieren (falls noch nicht vorhanden)
ssh-keygen -t ed25519 -C "securecall-prod" -f ~/.ssh/securecall

# Ersten Login testen
ssh root@[VPS_IP]
```

### 3.3 Server Setup ausführen

Das Setup-Script aus Phase 12 verwenden:

```bash
# Auf dem VPS:
# 1. Repository klonen
git clone https://github.com/nicokimmel/stealth.git /opt/securecall
cd /opt/securecall

# 2. Setup-Script ausführen (installiert Node.js, PM2, Nginx, Coturn, UFW)
chmod +x deployment/setup_server.sh
sudo bash deployment/setup_server.sh
```

**Was das Script installiert:**
- Node.js 18 LTS
- PM2 (Process Manager)
- Nginx (Reverse Proxy)
- Coturn (TURN Server)
- UFW Firewall (Ports: 22, 80, 443, 3478, 5349, 49152-49200)
- Certbot (Let's Encrypt)

### 3.4 Signaling Server deployen

```bash
# Auf dem VPS:
cd /opt/securecall

# Environment-Datei erstellen (siehe Schritt 7)
cp deployment/.env.example deployment/.env
nano deployment/.env

# Deploy-Script ausführen
chmod +x deployment/deploy_signaling.sh
bash deployment/deploy_signaling.sh
```

### 3.5 Nginx konfigurieren

```bash
# Nginx-Configs kopieren
sudo cp deployment/nginx_config/signal.stealthx.app.conf /etc/nginx/sites-available/
sudo cp deployment/nginx_config/stealthx.app.conf /etc/nginx/sites-available/

# Symlinks erstellen
sudo ln -sf /etc/nginx/sites-available/signal.stealthx.app.conf /etc/nginx/sites-enabled/
sudo ln -sf /etc/nginx/sites-available/stealthx.app.conf /etc/nginx/sites-enabled/

# Default-Config entfernen
sudo rm -f /etc/nginx/sites-enabled/default

# Konfiguration testen & laden
sudo nginx -t
sudo systemctl reload nginx
```

### 3.6 Verifizieren

```bash
# Signaling Server Status
pm2 status

# HTTP-Test (vor SSL)
curl http://signal.stealthx.app/health
# Erwartete Antwort: {"status":"ok"}

# Ports prüfen
sudo ufw status
sudo ss -tlnp | grep -E '(8080|80|443|3478)'
```

### Checkliste VPS

- [ ] VPS bestellt und läuft
- [ ] SSH-Zugang funktioniert
- [ ] setup_server.sh erfolgreich ausgeführt
- [ ] Signaling Server läuft (pm2 status = online)
- [ ] Nginx läuft und konfiguriert
- [ ] UFW Firewall aktiv
- [ ] Health-Check antwortet: `/health` → `{"status":"ok"}`

---

## Schritt 4: SSL Zertifikate einrichten (Let's Encrypt)

### 4.1 Certbot installieren (falls nicht durch Setup-Script)

```bash
sudo apt install -y certbot python3-certbot-nginx
```

### 4.2 SSL-Zertifikate anfordern

```bash
# Website (falls auf VPS gehostet, nicht Netlify)
sudo certbot --nginx -d stealthx.app -d www.stealthx.app \
    --email admin@stealthx.app --agree-tos --no-eff-email

# Signaling Server
sudo certbot --nginx -d signal.stealthx.app \
    --email admin@stealthx.app --agree-tos --no-eff-email

# TURN Server
sudo certbot certonly --standalone -d turn.stealthx.app \
    --email admin@stealthx.app --agree-tos --no-eff-email \
    --pre-hook "systemctl stop nginx" \
    --post-hook "systemctl start nginx"
```

### 4.3 TURN Server SSL konfigurieren

```bash
# Zertifikat-Pfade in Coturn-Config eintragen
sudo nano /etc/turnserver.conf
```

Folgende Zeilen hinzufügen/ändern:
```
cert=/etc/letsencrypt/live/turn.stealthx.app/fullchain.pem
pkey=/etc/letsencrypt/live/turn.stealthx.app/privkey.pem
```

```bash
sudo systemctl restart coturn
```

### 4.4 Auto-Renewal einrichten

```bash
# Renewal testen
sudo certbot renew --dry-run

# Cron-Job prüfen (wird automatisch installiert)
sudo systemctl status certbot.timer
```

### 4.5 Verifizieren

```bash
# SSL-Zertifikate prüfen
echo | openssl s_client -connect signal.stealthx.app:443 -servername signal.stealthx.app 2>/dev/null | openssl x509 -noout -dates

# HTTPS-Test
curl -I https://signal.stealthx.app/health
# Erwartete Antwort: HTTP/2 200

# Online-Test: https://www.ssllabs.com/ssltest/
# Erwartetes Rating: A oder A+
```

### Checkliste SSL

- [ ] Certbot installiert
- [ ] SSL für signal.stealthx.app aktiv
- [ ] SSL für turn.stealthx.app aktiv
- [ ] SSL für stealthx.app aktiv (falls auf VPS)
- [ ] Auto-Renewal funktioniert (`certbot renew --dry-run`)
- [ ] SSL Labs Rating: A oder A+
- [ ] HTTPS-Redirect funktioniert (HTTP → HTTPS)

---

## Schritt 5: TURN Server konfigurieren

### 5.1 Coturn Konfiguration

```bash
sudo nano /etc/turnserver.conf
```

**Minimale Production-Konfiguration:**
```ini
# Netzwerk
listening-port=3478
tls-listening-port=5349
listening-ip=0.0.0.0
external-ip=[VPS_IP]
relay-ip=[VPS_IP]
min-port=49152
max-port=49200

# Authentifizierung
use-auth-secret
static-auth-secret=[TURN_SECRET]   # ← Sicheres Passwort generieren!
realm=turn.stealthx.app

# SSL (aus Schritt 4)
cert=/etc/letsencrypt/live/turn.stealthx.app/fullchain.pem
pkey=/etc/letsencrypt/live/turn.stealthx.app/privkey.pem

# Sicherheit
no-multicast-peers
denied-peer-ip=10.0.0.0-10.255.255.255
denied-peer-ip=172.16.0.0-172.31.255.255
denied-peer-ip=192.168.0.0-192.168.255.255
denied-peer-ip=0.0.0.0-0.255.255.255
denied-peer-ip=127.0.0.0-127.255.255.255

# Logging
log-file=/var/log/coturn/turnserver.log
verbose
```

### 5.2 TURN Secret generieren

```bash
# Sicheres Secret generieren (64 Zeichen)
openssl rand -hex 32
# Ausgabe kopieren → als [TURN_SECRET] in turnserver.conf UND .env eintragen
```

### 5.3 Coturn starten & testen

```bash
sudo systemctl enable coturn
sudo systemctl restart coturn
sudo systemctl status coturn

# TURN-Test mit turnutils
turnutils_uclient -t -u test -w test turn.stealthx.app
```

### 5.4 Online testen

**Trickle ICE Test:** https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/

```
STUN/TURN URI: turn:turn.stealthx.app:3478
Username:      (leer bei use-auth-secret)
Password:      (leer bei use-auth-secret)
```

> Für `use-auth-secret` wird das Credential dynamisch vom Signaling Server generiert.
> Trickle ICE funktioniert nur mit statischem User/Passwort.
> Der echte Test erfolgt mit der App (Schritt 9).

### Checkliste TURN

- [ ] Coturn installiert und konfiguriert
- [ ] TURN Secret generiert und eingetragen
- [ ] SSL-Zertifikat eingebunden
- [ ] Private IP-Ranges blockiert
- [ ] Coturn-Service läuft (`systemctl status coturn`)
- [ ] Firewall-Ports offen (3478, 5349, 49152-49200)
- [ ] Log-Datei wird geschrieben

---

## Schritt 6: Release Keystore generieren & sicher verwahren

### 6.1 Keystore generieren

```bash
# Im Projektverzeichnis (lokal, NICHT auf dem Server!)
keytool -genkey -v \
    -keystore securecall-release-key.jks \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -alias securecall \
    -storepass [STORE_PASSWORD] \
    -keypass [KEY_PASSWORD] \
    -dname "CN=SecureCall, OU=Mobile, O=StealthX, L=Berlin, ST=Berlin, C=DE"
```

> **KRITISCH:** Ersetze `[STORE_PASSWORD]` und `[KEY_PASSWORD]` mit sicheren Passwörtern!
> Generiere sie mit: `openssl rand -base64 32`

### 6.2 Keystore verifizieren

```bash
keytool -list -v -keystore securecall-release-key.jks -storepass [STORE_PASSWORD]

# SHA-256 Fingerprint notieren → wird für Play Console benötigt
# Ausgabe enthält:
# Certificate fingerprints:
#     SHA256: XX:XX:XX:...
```

### 6.3 Keystore sicher verwahren

**NIEMALS im Git-Repository committen!** (`.gitignore` blockt bereits `*.jks` und `*.keystore`)

**Backup-Strategie (ALLE drei umsetzen):**

| Ort | Anleitung |
|-----|-----------|
| **Passwort-Manager** | 1Password, Bitwarden: Keystore-Datei als Attachment speichern |
| **Verschlüsselter USB** | LUKS/VeraCrypt-Volume auf USB-Stick, Keystore + Passwort-Datei |
| **Cloud (verschlüsselt)** | In ein Cryptomator-Vault oder gpg-verschlüsselt in Cloud-Storage |

```bash
# Beispiel: Keystore mit GPG verschlüsseln für Cloud-Backup
gpg --symmetric --cipher-algo AES256 securecall-release-key.jks
# Ergebnis: securecall-release-key.jks.gpg
# Zum Entschlüsseln: gpg -d securecall-release-key.jks.gpg > securecall-release-key.jks
```

### 6.4 Google Play App Signing aktivieren

> **Dringend empfohlen:** Google Play App Signing lässt Google den Upload-Key verwalten.
> Falls du den Keystore verlierst, kann Google trotzdem Updates signieren.

```
1. Play Console → App → Setup → App signing
2. "Use Google-managed key" → Accept
3. Upload Key = dein lokaler Keystore
4. Google signiert die APKs mit ihrem Key vor der Auslieferung
```

### Checkliste Keystore

- [ ] Keystore generiert (`securecall-release-key.jks`)
- [ ] SHA-256 Fingerprint notiert
- [ ] Keystore NICHT im Git-Repo
- [ ] Backup in Passwort-Manager
- [ ] Backup auf verschlüsseltem USB-Stick
- [ ] Backup in verschlüsseltem Cloud-Storage
- [ ] Passwörter separat und sicher dokumentiert
- [ ] Google Play App Signing wird aktiviert (bei Play Console Setup)

---

## Schritt 7: Environment Variables für Production

### 7.1 Alle benötigten Variablen

Erstelle eine `.env`-Datei auf dem VPS:

```bash
# Auf dem VPS:
nano /opt/securecall/deployment/.env
```

```bash
# ─── Signaling Server ────────────────────────────────────
NODE_ENV=production
PORT=8080
SIGNAL_HOST=0.0.0.0

# ─── TURN Server Credentials ─────────────────────────────
TURN_SECRET=[TURN_SECRET]          # ← Gleicher Wert wie in turnserver.conf
TURN_DOMAIN=turn.stealthx.app
TURN_PORT=3478
TURNS_PORT=5349

# ─── Domain ──────────────────────────────────────────────
DOMAIN=stealthx.app
SIGNAL_DOMAIN=signal.stealthx.app

# ─── Logging ──────────────────────────────────────────────
LOG_LEVEL=warn
```

### 7.2 Lokale Build-Variablen (für Android)

```bash
# Auf deinem lokalen Entwicklungsrechner:
# Terminal / .bashrc / .zshrc:

export SECURECALL_STORE_FILE=/pfad/zu/securecall-release-key.jks
export SECURECALL_STORE_PASSWORD=[STORE_PASSWORD]
export SECURECALL_KEY_ALIAS=securecall
export SECURECALL_KEY_PASSWORD=[KEY_PASSWORD]
```

```bash
# Alternativ: signing.properties Datei (NICHT committen!)
echo "SECURECALL_STORE_FILE=/pfad/zu/securecall-release-key.jks
SECURECALL_STORE_PASSWORD=[STORE_PASSWORD]
SECURECALL_KEY_ALIAS=securecall
SECURECALL_KEY_PASSWORD=[KEY_PASSWORD]" > client_android/signing.properties
```

### 7.3 Production URLs in der App

Die Server-URLs sind bereits in `client_android/app/build.gradle` konfiguriert:

```groovy
// Release Build → Production Server
release {
    buildConfigField "String", "SIGNAL_WS_URL", "\"wss://signal.stealthx.app/signal\""
    buildConfigField "String", "STUN_URL", "\"stun:stun.l.google.com:19302\""
    buildConfigField "String", "TURN_URL", "\"turn:turn.stealthx.app:3478\""
    buildConfigField "String", "TURNS_URL", "\"turns:turn.stealthx.app:5349\""
}
```

> Diese Werte müssen NICHT manuell geändert werden — sie sind im Gradle-File fest definiert.

### Checkliste Environment

- [ ] `.env` auf VPS erstellt mit allen Variablen
- [ ] TURN_SECRET stimmt mit turnserver.conf überein
- [ ] Lokale Signing-Variablen gesetzt
- [ ] signing.properties NICHT im Git-Repo
- [ ] Production URLs in build.gradle korrekt

---

## Schritt 8: Release AABs bauen

### 8.1 Voraussetzungen prüfen

```bash
# Android Studio / SDK prüfen
echo $ANDROID_HOME
# → /Users/[user]/Library/Android/sdk

# JDK Version prüfen (mindestens JDK 17)
java -version

# Rust Toolchain prüfen (für Crypto Engine)
rustc --version
rustup target list --installed
# Benötigt: aarch64-linux-android, armv7-linux-androideabi, x86_64-linux-android

# Signing-Variablen prüfen
echo $SECURECALL_STORE_FILE
# → Pfad zum Keystore
```

### 8.2 Rust Cross-Compilation Targets installieren

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
```

### 8.3 AABs für alle drei Flavors bauen

```bash
cd client_android

# Clean Build
./gradlew clean

# AABs bauen (für Play Store Upload)
./gradlew bundleFreeRelease
./gradlew bundleProRelease
./gradlew bundlePremiumRelease

# APKs bauen (für direktes Testen auf Gerät)
./gradlew assembleFreeRelease
./gradlew assembleProRelease
./gradlew assemblePremiumRelease
```

**Oder das Build-Script verwenden:**

```bash
chmod +x tools/build_release_aabs.sh
bash tools/build_release_aabs.sh
```

### 8.4 Build-Artefakte prüfen

```bash
# AABs (für Play Store)
ls -lh app/build/outputs/bundle/freeRelease/app-free-release.aab
ls -lh app/build/outputs/bundle/proRelease/app-pro-release.aab
ls -lh app/build/outputs/bundle/premiumRelease/app-premium-release.aab

# APKs (für direktes Testen)
ls -lh app/build/outputs/apk/free/release/app-free-release.apk
ls -lh app/build/outputs/apk/pro/release/app-pro-release.apk
ls -lh app/build/outputs/apk/premium/release/app-premium-release.apk
```

**Erwartete Größen:** < 15 MB pro AAB/APK

### 8.5 APK auf Gerät installieren (Test)

```bash
# Gerät verbinden und prüfen
adb devices

# FREE Version installieren
adb install app/build/outputs/apk/free/release/app-free-release.apk

# Falls bereits installiert:
adb install -r app/build/outputs/apk/free/release/app-free-release.apk
```

### Checkliste Build

- [ ] JDK 17+ installiert
- [ ] Rust Toolchain mit Android-Targets installiert
- [ ] Signing-Variablen gesetzt
- [ ] `./gradlew clean` erfolgreich
- [ ] FREE AAB gebaut (< 15 MB)
- [ ] PRO AAB gebaut (< 15 MB)
- [ ] PREMIUM AAB gebaut (< 15 MB)
- [ ] APKs gebaut für direktes Testen
- [ ] APK erfolgreich auf Gerät installiert

---

## Schritt 9: Manual Testing auf echtem Gerät

### 9.1 Test-Geräte

| Gerät | Android | Zweck |
|-------|---------|-------|
| Primär (Hauptgerät) | Android 13–15 | Haupt-QA |
| Sekundär (zweites Gerät / Emulator) | Android 10+ | Gegenpartei für Anrufe |
| Low-End (optional) | Android 6–8 | Kompatibilität |

### 9.2 Kritische Tests (MUSS bestanden werden)

Vollständige QA-Checkliste: `docs/FINAL_QA_CHECKLIST.md`

**Hier die absoluten Must-Pass Tests:**

```
─── Installation & Start ───────────────────────────────
[ ] App installiert ohne Fehler
[ ] Cold Start < 3 Sekunden
[ ] Keine Crashes beim Start (Logcat prüfen)
[ ] Berechtigungs-Dialoge erscheinen korrekt

─── Verschlüsselung ────────────────────────────────────
[ ] Key-Paar wird beim ersten Start generiert
[ ] Anruf zwischen 2 Geräten möglich
[ ] "Encrypted" Status wird angezeigt
[ ] Audio ist beidseitig hörbar und klar

─── Server-Verbindung ──────────────────────────────────
[ ] WebSocket-Verbindung zu signal.stealthx.app klappt
[ ] Signaling-Handshake funktioniert
[ ] TURN-Relay funktioniert (bei NAT)
[ ] Reconnect nach Netzwerkwechsel (WiFi → Mobile)

─── Audio-Qualität ─────────────────────────────────────
[ ] Klare Sprachqualität (kein Rauschen, Echo)
[ ] Latenz < 300ms
[ ] Audio funktioniert über Lautsprecher
[ ] Audio funktioniert über Kopfhörer/Bluetooth

─── Anti-Recording (PRO/PREMIUM) ───────────────────────
[ ] FLAG_SECURE verhindert Screenshots
[ ] Security Status wird angezeigt (grün/gelb/rot)
[ ] Warnung bei erkannter Bedrohung

─── Performance ────────────────────────────────────────
[ ] RAM < 150 MB (Idle)
[ ] RAM < 300 MB (während Anruf)
[ ] Batterie < 5% pro Stunde Anruf
[ ] CPU < 20% während Anruf

─── Tier-spezifisch ────────────────────────────────────
[ ] FREE: 15-Min-Limit wird durchgesetzt
[ ] FREE: 10-Kontakte-Limit wird durchgesetzt
[ ] PRO: Unbegrenzte Anrufe funktionieren
[ ] PREMIUM: GhostNet Relay aktiv (IP-Masking)
```

### 9.3 Logcat überwachen

```bash
# Alle SecureCall-Logs anzeigen
adb logcat | grep -i "securecall"

# Crashes anzeigen
adb logcat *:E | grep -i "securecall\|FATAL\|AndroidRuntime"

# Netzwerk-Logs
adb logcat | grep -i "websocket\|signal\|turn\|stun"
```

### 9.4 Performance testen

```bash
# Performance-Test-Script ausführen
chmod +x tools/performance_test.sh
bash tools/performance_test.sh
```

### Checkliste Testing

- [ ] Alle kritischen Tests bestanden
- [ ] Kein Crash in Logcat
- [ ] Audio klar auf beiden Seiten
- [ ] Performance innerhalb der Limits
- [ ] FREE Tier-Limits funktionieren
- [ ] PRO Features funktionieren
- [ ] PREMIUM Features funktionieren
- [ ] Netzwerkwechsel überlebt der Anruf

---

## Schritt 10: Google Play Developer Account

### 10.1 Account erstellen

**Link:** https://play.google.com/console/signup

```
1. https://play.google.com/console/signup öffnen
2. Google-Account einloggen (eigener oder neuer Account für StealthX)
3. Entwicklertyp: "Privat" oder "Organisation"
4. Name: "StealthX" (wird öffentlich angezeigt)
5. E-Mail: developer@stealthx.app
6. 25 € Registrierungsgebühr bezahlen (einmalig, Kreditkarte)
7. Identitätsverifizierung abschließen (Ausweis-Upload)
```

> **Hinweis:** Die Identitätsverifizierung kann 2–7 Tage dauern.
> Diesen Schritt früh starten!

### 10.2 Account-Einstellungen

```
1. Play Console → Settings → Developer account
2. Contact email: developer@stealthx.app
3. Phone: Telefonnummer eintragen
4. Website: https://stealthx.app
5. Support → Settings:
   - Email: support@stealthx.app
   - Privacy Policy URL: https://stealthx.app/privacy.html
```

### Checkliste Developer Account

- [ ] Google Play Developer Account erstellt
- [ ] 25 € bezahlt
- [ ] Identitätsverifizierung abgeschlossen
- [ ] Kontakt-E-Mail konfiguriert
- [ ] Website-URL eingetragen
- [ ] Support-E-Mail konfiguriert
- [ ] Privacy Policy URL eingetragen

---

## Schritt 11: Play Console — 3 Apps einrichten

### 11.1 App-Details

| | FREE | PRO | PREMIUM |
|---|---|---|---|
| **Package** | `com.securecall.app.free` | `com.securecall.app.pro` | `com.securecall.app.premium` |
| **Name** | SecureCall | SecureCall Pro | SecureCall Premium |
| **Preis** | Kostenlos | Kostenlos (IAP) | Kostenlos (IAP) |
| **Kategorie** | Communication | Communication | Communication |
| **Content Rating** | PEGI 3 | PEGI 3 | PEGI 3 |

> **Empfehlung:** Starte mit EINER App (FREE) und füge PRO/PREMIUM als In-App-Purchases hinzu.
> Das ist einfacher als 3 separate Apps zu managen.
> Falls separate Apps gewünscht: Jeden Schritt 3x wiederholen.

### 11.2 App erstellen

Für jede App:

```
1. Play Console → "Create app"
2. App name: "SecureCall" (bzw. Pro/Premium)
3. Default language: German (de-DE)
4. App or game: App
5. Free or paid: Free
6. Declarations: Alle bestätigen
7. "Create app"
```

### 11.3 Store Listing ausfüllen

Die Texte liegen bereits in `marketing/play_store/`:

```
├── de/
│   ├── title.txt              → App-Name
│   ├── short_description.txt  → Kurzbeschreibung (80 Zeichen)
│   ├── full_description.txt   → Vollständige Beschreibung (4000 Zeichen)
│   └── release_notes.txt      → Release Notes
└── en/
    ├── title.txt
    ├── short_description.txt
    ├── full_description.txt
    └── release_notes.txt
```

**Store Listing Felder:**

```
─── Main store listing ─────────────────────────────────
App name:           [aus title.txt]
Short description:  [aus short_description.txt]
Full description:   [aus full_description.txt]
App icon:           512x512 PNG (logo.png verwenden/anpassen)
Feature graphic:    1024x500 PNG (muss erstellt werden)
Screenshots:        Min. 2 Phone-Screenshots (1080x1920)
                    Siehe: marketing/SCREENSHOT_GUIDE.md

─── Categorization ─────────────────────────────────────
App category:       Communication
Tags:               Encrypted calls, Privacy, VoIP, Security

─── Contact details ────────────────────────────────────
Email:              support@stealthx.app
Website:            https://stealthx.app
Privacy Policy:     https://stealthx.app/privacy.html
```

### 11.4 Content Rating

```
1. App content → Content rating → Start questionnaire
2. Category: Communication (Utility)
3. Violence: None
4. Sexuality: None
5. Substances: None
6. Language: None
7. → PEGI 3 / Everyone
```

### 11.5 Data Safety

```
1. App content → Data safety → Manage
2. Encryption: Yes, all data in transit is encrypted
3. Data collection:
   - FREE: Crash reports (Firebase Crashlytics) — Optional, can opt out
   - PRO/PREMIUM: No data collected
4. Data sharing: No data shared with third parties
5. Data deletion: Users can delete all data in app settings
```

### 11.6 App Signing

```
1. Setup → App signing
2. Choose: "Use Google-generated key" (recommended)
3. Upload Key: Upload certificate from your keystore:
   keytool -export -keystore securecall-release-key.jks \
       -alias securecall -rfc > upload_cert.pem
4. Upload upload_cert.pem to Play Console
```

### Checkliste Play Console

- [ ] App(s) erstellt in Play Console
- [ ] Store Listing ausgefüllt (DE + EN)
- [ ] App Icon hochgeladen (512x512)
- [ ] Feature Graphic erstellt und hochgeladen (1024x500)
- [ ] Mindestens 2 Screenshots hochgeladen
- [ ] Content Rating ausgefüllt (PEGI 3)
- [ ] Data Safety ausgefüllt
- [ ] Privacy Policy URL eingetragen
- [ ] App Signing konfiguriert
- [ ] Kontakt-E-Mail eingetragen

---

## Schritt 12: Internal Testing Track Upload

### 12.1 AAB hochladen

```
1. Play Console → App → Testing → Internal testing
2. "Create new release"
3. AAB hochladen: app-free-release.aab
4. Release name: "0.2-beta (Internal)"
5. Release notes: [aus release_notes.txt]
6. "Review release" → "Start rollout to Internal testing"
```

### 12.2 Tester einladen

```
1. Internal testing → Testers tab
2. "Create email list" → Name: "Core Team"
3. E-Mail-Adressen der Tester eintragen (max 100)
4. Opt-in Link kopieren und an Tester senden
```

> Tester müssen den Opt-in Link öffnen und die App über den Play Store installieren.

### 12.3 Selbst testen

```
1. Opt-in Link im Browser öffnen (mit dem Google-Account des Testers)
2. "Become a tester" / "Accept" klicken
3. Play Store öffnen → SecureCall suchen → Installieren
4. Alle kritischen Tests aus Schritt 9 durchlaufen
```

### Checkliste Internal Testing

- [ ] AAB hochgeladen und verarbeitet
- [ ] Release erstellt und ausgerollt
- [ ] Tester-Liste erstellt
- [ ] Opt-in Link verteilt
- [ ] App aus Play Store installierbar
- [ ] Grundfunktionen nach Play Store Installation getestet

---

## Schritt 13: Beta Testing (2–3 Wochen)

### 13.1 Closed Beta starten

```
1. Play Console → Testing → Closed testing
2. "Create new release" → AAB hochladen
3. Tester-Liste: 10–20 Personen
   - 3–5 Entwickler/Tech-Kontakte
   - 3–5 nicht-technische Benutzer
   - 2–5 verschiedene Geräte/Android-Versionen
4. "Start rollout to Closed testing"
```

### 13.2 Feedback sammeln

**Feedback-Kanäle:**
- Google Play In-App-Feedback (automatisch)
- E-Mail: beta@stealthx.app
- GitHub Issues: https://github.com/nicokimmel/stealth/issues
- Google Form (optional): Erstelle ein Feedback-Formular

**Feedback-Formular Vorschlag:**
```
1. Welches Gerät und Android-Version nutzt du?
2. Konnte die App erfolgreich installiert werden?
3. Konnte ein verschlüsselter Anruf durchgeführt werden?
4. Wie ist die Audioqualität? (1-5)
5. Gab es Abstürze? Wenn ja, wann?
6. Was gefällt dir gut?
7. Was sollte verbessert werden?
8. Würdest du die App empfehlen? (1-10)
```

### 13.3 Beta-Timeline

| Tag | Aufgabe |
|-----|---------|
| **1** | AAB an Closed Testing Track hochladen |
| **1–2** | Opt-in Links verteilen, Installation prüfen |
| **3–7** | Erste Feedback-Runde, kritische Bugs fixen |
| **8** | Update-AAB hochladen (Bugfix-Release) |
| **8–14** | Zweite Test-Runde, Performance-Monitoring |
| **15** | Finale Feedback-Runde |
| **16–17** | Letzte Fixes einarbeiten |
| **18–21** | Stabilisierung, kein neuer Code |

### 13.4 Exit-Kriterien (Beta → Production)

| Kriterium | Ziel |
|-----------|------|
| Crash-free Rate | > 99,5% |
| ANR Rate | < 0,47% |
| Kritische Bugs | 0 |
| Audio-Qualität Durchschnitt | ≥ 4/5 |
| NPS (Weiterempfehlung) | ≥ 7/10 |
| Tester mit erfolgreichem Anruf | > 80% |

### Checkliste Beta

- [ ] Closed Beta gestartet mit 10–20 Testern
- [ ] Feedback-Kanal kommuniziert
- [ ] Erste Feedback-Runde eingearbeitet
- [ ] Bugfix-Release hochgeladen
- [ ] Zweite Test-Runde abgeschlossen
- [ ] Crash-free Rate > 99,5%
- [ ] Keine kritischen Bugs offen
- [ ] Alle Exit-Kriterien erfüllt

---

## Schritt 14: Production Release

### 14.1 Pre-Release Checklist

```
─── Code ───────────────────────────────────────────────
[ ] Alle Beta-Bugs gefixt
[ ] Finaler Code-Review durchgeführt
[ ] Version in build.gradle erhöht:
    versionCode 3
    versionName "1.0"
[ ] Release Branch/Tag erstellt:
    git tag -a v1.0 -m "SecureCall v1.0 Production Release"
    git push origin v1.0

─── Build ──────────────────────────────────────────────
[ ] Clean Build erfolgreich
[ ] Alle 3 AABs gebaut (FREE, PRO, PREMIUM)
[ ] AAB-Größen geprüft (< 15 MB)
[ ] R8-Mapping-File gesichert (für Crashlytics)

─── Server ─────────────────────────────────────────────
[ ] Signaling Server läuft stabil
[ ] SSL-Zertifikate gültig (> 30 Tage)
[ ] TURN Server funktioniert
[ ] Health-Check Monitoring aktiv

─── Play Store ─────────────────────────────────────────
[ ] Store Listing final (DE + EN)
[ ] Screenshots aktuell
[ ] Privacy Policy URL korrekt
[ ] Data Safety ausgefüllt
[ ] Content Rating abgeschlossen
```

### 14.2 Production Release erstellen

```
1. Play Console → App → Production
2. "Create new release"
3. AAB hochladen: app-free-release.aab (v1.0)
4. Release name: "1.0"
5. Release notes eintragen (DE + EN)
6. "Review release"
```

### 14.3 Staged Rollout

> **Niemals sofort 100% ausrollen!** Gestaffelter Rollout fängt Probleme früh ab.

| Tag | Rollout | Nutzer (bei 1000 Downloads) |
|-----|---------|----------------------------|
| Tag 1–2 | 5% | ~50 |
| Tag 3–4 | 10% | ~100 |
| Tag 5–6 | 25% | ~250 |
| Tag 7–8 | 50% | ~500 |
| Tag 9–10 | 100% | Alle |

```
1. "Start rollout to Production" → 5%
2. 2 Tage warten, Crashlytics und Play Console Vitals prüfen
3. Play Console → Production → "Update rollout" → 10%
4. Wiederholen bis 100%
```

**Rollout stoppen bei:**
- Crash-free Rate < 99%
- ANR Rate > 1%
- Neue kritische Bugs in Reviews

```
# Rollout stoppen:
Play Console → Production → "Halt rollout"
# Hotfix bauen, neuen Release erstellen
```

### Checkliste Production Release

- [ ] Pre-Release Checklist komplett
- [ ] AAB hochgeladen und verarbeitet
- [ ] Staged Rollout gestartet (5%)
- [ ] Crash-Rate nach 48h geprüft
- [ ] Rollout auf 10% erhöht
- [ ] Rollout auf 25% erhöht
- [ ] Rollout auf 50% erhöht
- [ ] Rollout auf 100% abgeschlossen
- [ ] App live im Play Store sichtbar

---

## Schritt 15: Post-Launch Monitoring & Support

### 15.1 Monitoring einrichten

**Play Console Vitals:**
```
Play Console → App → Android Vitals
- Crash rate: Ziel < 0,5%
- ANR rate: Ziel < 0,2%
- Tägliche Prüfung in der ersten Woche
```

**Server Monitoring:**

```bash
# PM2 Monitoring
pm2 monit

# Automatischen Health-Check einrichten (auf VPS)
chmod +x /opt/securecall/deploy/scripts/healthcheck.sh
crontab -e
# Folgende Zeile hinzufügen:
*/5 * * * * /opt/securecall/deploy/scripts/healthcheck.sh >> /var/log/securecall-health.log 2>&1
```

**Uptime Monitoring (kostenlos):**

| Tool | Link | Features |
|------|------|----------|
| **UptimeRobot** | https://uptimerobot.com | 50 Monitore kostenlos, 5-Min-Intervall |
| BetterStack | https://betterstack.com | Status-Page, Alerting |
| Freshping | https://freshping.io | 50 Monitore kostenlos |

**UptimeRobot einrichten:**
```
1. Account erstellen: https://uptimerobot.com
2. Monitor hinzufügen:
   - Name: "SecureCall Signaling"
   - URL: https://signal.stealthx.app/health
   - Interval: 5 Minuten
   - Alert: E-Mail an admin@stealthx.app
3. Zweiten Monitor:
   - Name: "SecureCall Website"
   - URL: https://stealthx.app
   - Interval: 5 Minuten
```

### 15.2 Support einrichten

**E-Mail-Adressen:**
```
support@stealthx.app    → Allgemeiner Support
privacy@stealthx.app    → Datenschutz-Anfragen (DSGVO)
security@stealthx.app   → Security-Reports (Responsible Disclosure)
press@stealthx.app      → Presse-Anfragen
```

> Diese E-Mail-Adressen bei eurem E-Mail-Provider einrichten (z.B. Google Workspace ~6 €/Nutzer/Monat
> oder kostenlos mit Zoho Mail / ImprovMX für Weiterleitung).

**E-Mail-Weiterleitung (kostenlos):**
```
1. ImprovMX: https://improvmx.com (kostenlos für 1 Domain)
2. Domain: stealthx.app
3. Forward: *@stealthx.app → deine-email@gmail.com
4. MX-Records bei DNS-Registrar setzen (ImprovMX zeigt die Werte an)
```

**GitHub Issues:**
```
1. GitHub → Repository → Issues → Enable
2. Issue Templates erstellen:
   - Bug Report
   - Feature Request
   - Security Vulnerability
3. Link: https://github.com/nicokimmel/stealth/issues
```

### 15.3 Play Store Reviews beantworten

```
1. Play Console → App → Ratings and reviews
2. Auf jede 1–3 Sterne Review antworten (innerhalb 24h)
3. Höflich, lösungsorientiert, niemals defensiv
4. Bei technischen Problemen: Support-E-Mail anbieten
```

**Antwort-Templates:**

*Bug-Report:*
> Thank you for your feedback. We're sorry you experienced this issue.
> Please contact support@stealthx.app with your device model and Android version
> so we can investigate and fix this as quickly as possible.

*Feature-Request:*
> Thank you for the suggestion! We'll add this to our roadmap.
> Follow our updates on GitHub: github.com/nicokimmel/stealth

### 15.4 Incident Response

```
─── Severity 1 (Outage): Signaling Server down ────────
1. ssh root@[VPS_IP]
2. pm2 status → prüfen ob Prozess läuft
3. pm2 restart signaling
4. pm2 logs signaling --lines 50 → Ursache finden
5. Falls Server selbst down: Hetzner Console → Reboot

─── Severity 2 (Degraded): Hohe Latenz / Drops ────────
1. pm2 monit → CPU/RAM prüfen
2. sudo journalctl -u coturn --since "1 hour ago"
3. TURN-Logs prüfen: /var/log/coturn/turnserver.log
4. Nginx-Logs: /var/log/nginx/access.log

─── Severity 3 (Bug): App-Crash bei Nutzern ───────────
1. Firebase Console → Crashlytics (nur FREE Tier)
2. Play Console → Android Vitals → Crashes
3. Crash analysieren mit R8-Mapping:
   client_android/app/build/outputs/mapping/freeRelease/mapping.txt
4. Hotfix bauen und als neuen Release hochladen
```

### Checkliste Monitoring & Support

- [ ] UptimeRobot eingerichtet (Signaling + Website)
- [ ] Health-Check Cron-Job aktiv auf VPS
- [ ] E-Mail-Adressen eingerichtet (support, privacy, security)
- [ ] GitHub Issues aktiviert
- [ ] Play Store Review-Benachrichtigungen aktiviert
- [ ] Incident-Response-Plan bekannt
- [ ] PM2 Log-Rotation eingerichtet

---

## Schritt 16: Marketing Launch

### 16.1 Pre-Launch (1 Woche vor Release)

**Social Media Accounts erstellen:**
- [ ] Twitter/X: @stealthxapp
- [ ] GitHub: Repo-Beschreibung und README aktualisieren
- [ ] Reddit Account (falls nicht vorhanden)

**Landing Page live:**
- [ ] https://stealthx.app erreichbar
- [ ] Alle Seiten funktionieren
- [ ] Google Play Link aktualisieren (sobald App live)

### 16.2 Launch Day

**Reddit Posts (höchste Priorität):**

| Subreddit | Titel-Vorschlag | Regeln beachten |
|-----------|-----------------|-----------------|
| r/privacy | "I built an open-source E2E encrypted voice calling app with zero metadata" | Self-promotion rules lesen |
| r/Android | "SecureCall: Open-source encrypted voice calls with Rust crypto engine" | Self-promotion max 10% |
| r/privacytoolsIO | "New: SecureCall — E2E encrypted calls, source available, zero metadata" | Community-fokussiert |
| r/netsec | "Security architecture of SecureCall: XChaCha20-Poly1305, X25519, Double Ratchet" | Technisch, keine Werbung |
| r/de | "Wir haben eine E2E-verschlüsselte Telefon-App gebaut — Open Source aus Deutschland" | Deutsch |
| r/de_EDV | "SecureCall: Open-Source verschlüsselte Anrufe mit Rust Crypto Engine" | Technisch |

> **Wichtig:** Nicht am selben Tag in alle Subreddits posten! Verteile über 3–5 Tage.
> Immer zuerst die Subreddit-Regeln für Self-Promotion lesen.

**Twitter/X:**
```
🔒 SecureCall is live.

End-to-end encrypted voice calls.
Zero metadata. Source available.

Built with:
• XChaCha20-Poly1305 encryption
• Rust crypto engine
• Double Ratchet forward secrecy

Your voice belongs to you.

🔗 https://stealthx.app
📱 [Play Store Link]
💻 https://github.com/nicokimmel/stealth

#privacy #encryption #opensource #android
```

### 16.3 Woche 1–2: Community Engagement

**Hacker News:**
```
Titel: "Show HN: SecureCall – Open-source E2E encrypted voice calls with Rust crypto"
URL: https://stealthx.app
```
> Am besten morgens US-Ostküstenzeit (14–16 Uhr MESZ) posten.

**Product Hunt:**
```
1. Account: https://www.producthunt.com
2. "Post a product"
3. Name: SecureCall
4. Tagline: "End-to-end encrypted voice calls with zero metadata"
5. Topics: Privacy, Security, Open Source, Android
6. Am besten Dienstag oder Mittwoch um 00:01 PST posten
```

**Deutschsprachige Tech-Medien:**

| Medium | Kontakt | Typ |
|--------|---------|-----|
| Heise | newstips@heise.de | Artikel |
| Golem | redaktion@golem.de | Artikel |
| t3n | redaktion@t3n.de | Artikel |
| Mike Kuketz Blog | kontakt@kuketz-blog.de | Review |
| Privacy Handbuch | Kontaktformular | Listing |

**E-Mail-Template für Presse:**
```
Betreff: SecureCall — Open-Source E2E-verschlüsselte Telefonie aus Deutschland

Hallo [Name],

wir haben SecureCall veröffentlicht — eine Android-App für
Ende-zu-Ende-verschlüsselte Sprachanrufe.

Besonderheiten:
• Kryptografie-Engine in Rust (memory-safe)
• XChaCha20-Poly1305 Verschlüsselung
• Keinerlei Metadaten-Erfassung
• Quellcode öffentlich auf GitHub
• Entwickelt in Deutschland, DSGVO-konform

Website: https://stealthx.app
Source: https://github.com/nicokimmel/stealth
Play Store: [Link]

Gerne sende ich weitere Informationen oder stehe für Fragen
zur Verfügung.

Beste Grüße
[Name], StealthX
```

### 16.4 Monat 1: Ziele

| Metrik | Ziel |
|--------|------|
| Play Store Downloads | 1.000+ |
| Play Store Rating | ≥ 4,0 Sterne |
| GitHub Stars | 500+ |
| Crash-free Rate | > 99,5% |
| Website Traffic | 5.000+ Besucher |

### 16.5 Ongoing Marketing

- [ ] Wöchentlich 1–2 Twitter/X Posts (Updates, Security-Tipps, Entwicklungsfortschritt)
- [ ] Monatlich Reddit-Updates in r/privacy (Changelog, neue Features)
- [ ] GitHub Release Notes für jedes Update
- [ ] Blog-Posts erwägen (auf Website oder Medium) über Security-Themen
- [ ] YouTube-Kontakte: Techlore, The Hated One, Rob Braxman, Louis Rossmann

### Checkliste Marketing

- [ ] Social Media Accounts erstellt
- [ ] Landing Page live mit Play Store Link
- [ ] Reddit Posts vorbereitet (nicht alle am selben Tag!)
- [ ] Twitter/X Launch-Post veröffentlicht
- [ ] Hacker News "Show HN" gepostet
- [ ] Product Hunt Launch geplant
- [ ] Presse-E-Mails an DE-Tech-Medien gesendet
- [ ] GitHub README aktualisiert mit Badges und Screenshots

---

## Gesamte Launch-Checkliste

### Infrastruktur
- [ ] Domain registriert (stealthx.app)
- [ ] DNS konfiguriert (A-Records für signal, turn)
- [ ] VPS bestellt und eingerichtet (Hetzner CX22)
- [ ] Signaling Server deployed und erreichbar
- [ ] SSL-Zertifikate aktiv (signal, turn, website)
- [ ] TURN Server konfiguriert und getestet
- [ ] Website deployed (Netlify oder GitHub Pages)
- [ ] Monitoring eingerichtet (UptimeRobot)

### App Build & Signing
- [ ] Release Keystore generiert und sicher verwahrt
- [ ] 3 Backups des Keystores erstellt
- [ ] Environment Variables konfiguriert
- [ ] Alle 3 Release AABs gebaut
- [ ] APKs auf echtem Gerät getestet

### Quality Assurance
- [ ] Alle kritischen Tests bestanden
- [ ] Performance innerhalb der Limits
- [ ] Kein bekannter kritischer Bug

### Google Play
- [ ] Developer Account erstellt und verifiziert
- [ ] App(s) in Play Console angelegt
- [ ] Store Listing komplett (DE + EN)
- [ ] Screenshots und Grafiken hochgeladen
- [ ] Content Rating und Data Safety ausgefüllt
- [ ] Internal Testing bestanden
- [ ] Closed Beta abgeschlossen (2–3 Wochen)
- [ ] Exit-Kriterien erfüllt
- [ ] Production Release mit Staged Rollout
- [ ] 100% Rollout erreicht

### Post-Launch
- [ ] Server Monitoring aktiv
- [ ] Support-E-Mails eingerichtet
- [ ] Review-Antworten innerhalb 24h
- [ ] Marketing-Posts veröffentlicht
- [ ] Monat-1-Ziele getrackt

---

> **Du hast es geschafft!** 🎉
>
> SecureCall ist live. Deine Nutzer können jetzt verschlüsselt telefonieren.
>
> Bei Fragen: Alle Detail-Dokumente findest du in `docs/`:
> - `PRODUCTION_DEPLOYMENT.md` — Server-Setup Details
> - `FINAL_QA_CHECKLIST.md` — 80+ Testfälle
> - `BUILD_RELEASE.md` — Build-Anleitung
> - `PLAY_STORE_CHECKLIST.md` — Play Store Details
> - `BETA_TESTING_PLAN.md` — Beta-Testplan
> - `KEYSTORE_INFO.md` — Keystore Details
> - `DEPLOYMENT_GUIDE.md` — Docker Deployment
