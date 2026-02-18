# SecureCall — Complete Launch Guide (Zero-Cost Strategy)

> **Gratis Cloud Strategie:** GitHub Pages + Railway.app + Metered.ca
>
> Einzige Kosten: **25 EUR** (Google Play Developer Account, einmalig)
> Laufende Kosten: **0 EUR/Monat** (Free Tiers)

---

## Kosten-Übersicht

| Posten | Kosten | Typ |
|--------|--------|-----|
| Website (GitHub Pages) | 0 EUR | Kostenlos |
| Signaling Server (Railway.app) | 0 EUR | Free Tier (500h/Monat) |
| TURN Server (Metered.ca) | 0 EUR | Free Tier (50 GB/Monat) |
| SSL (automatisch) | 0 EUR | GitHub Pages + Railway |
| Google Play Developer | 25 EUR | Einmalig |
| **Gesamt** | **25 EUR** | **Einmalig** |

## Timeline

| Woche | Aufgabe |
|-------|---------|
| **1** | Website deployen, Railway + Metered einrichten, Keystore, Builds |
| **2** | Manual QA, Play Store Account, Internal Testing |
| **2–3** | Beta Testing (10–20 Tester) |
| **3** | Production Release, Marketing |

---

## Schritt 1: Website auf GitHub Pages deployen

### 1.1 GitHub Pages aktivieren

1. Gehe zu https://github.com/NeaBouli/stealth/settings/pages
2. **Source:** "GitHub Actions" wählen
3. Der Workflow `.github/workflows/deploy-pages.yml` ist bereits im Repo

> Der Workflow deployed automatisch das `website/` Verzeichnis bei jedem Push zu `main`.

### 1.2 Verifizieren

Nach dem nächsten Push zu `main`:
1. Warte 1-2 Minuten auf den Build
2. Prüfe: https://neabouli.github.io/stealth/
3. Alle Seiten testen: `/`, `/privacy.html`, `/security.html`, `/faq.html`

### 1.3 Custom Domain (optional, kostet ~12 EUR/Jahr)

Falls du `stealthx.app` kaufen möchtest:
1. Domain registrieren bei Namecheap (~12 EUR/Jahr) oder Cloudflare (~11 EUR/Jahr)
2. DNS: CNAME `www` → `neabouli.github.io`
3. GitHub Pages Settings → Custom Domain → `stealthx.app`
4. "Enforce HTTPS" aktivieren
5. `website/CNAME` Datei erstellen mit Inhalt: `stealthx.app`

### Checkliste

- [ ] GitHub Pages aktiviert
- [ ] Website erreichbar unter https://neabouli.github.io/stealth/
- [ ] Alle Seiten laden korrekt
- [ ] HTTPS funktioniert

---

## Schritt 2: Signaling Server auf Railway.app deployen

### 2.1 Railway Account erstellen

1. Öffne https://railway.com
2. "Start a New Project" klicken
3. Mit **GitHub** einloggen (NeaBouli Account)
4. GitHub-Zugriff autorisieren

### 2.2 Projekt erstellen

1. Dashboard → "New Project"
2. "Deploy from GitHub repo" → `NeaBouli/stealth`
3. Railway erkennt `backend/signaling/railway.json` automatisch

### 2.3 Service konfigurieren

Falls nicht automatisch erkannt:
1. Service Settings → Source → **Root Directory:** `backend/signaling`
2. **Start Command:** `node src/server.js`

### 2.4 Environment Variables setzen

Service → Variables:

| Variable | Wert |
|----------|------|
| `NODE_ENV` | `production` |
| `PORT` | `${{RAILWAY_PORT}}` |
| `TURN_SECRET` | `[openssl rand -hex 32 ausführen]` |
| `CORS_ORIGIN` | `https://neabouli.github.io` |

### 2.5 Domain generieren

1. Service → Settings → Networking → "Generate Domain"
2. URL kopieren: `[name]-production.up.railway.app`
3. Diese URL ist der Signaling Server!

### 2.6 Verifizieren

```bash
curl https://[DEINE-URL].up.railway.app/health
# → {"status":"ok"}
```

### Checkliste

- [ ] Railway Account erstellt
- [ ] Projekt deployed und läuft (grüner Status)
- [ ] Environment Variables gesetzt
- [ ] Health Check antwortet
- [ ] Railway URL notiert für Android App

> **Detaillierte Anleitung:** [docs/RAILWAY_DEPLOYMENT.md](RAILWAY_DEPLOYMENT.md)

---

## Schritt 3: TURN Server über Metered.ca einrichten

### 3.1 Account erstellen

1. Öffne https://www.metered.ca/signup
2. Account erstellen (E-Mail + Passwort)
3. Free Plan wählen (50 GB/Monat)

### 3.2 TURN Credentials erhalten

1. Dashboard → "TURN Server" Tab
2. Credentials und URLs notieren:
   - `stun:stun.relay.metered.ca:80`
   - `turn:global.relay.metered.ca:80`
   - `turns:global.relay.metered.ca:443?transport=tcp`
   - Username + Credential

### 3.3 Android App URLs updaten

In `client_android/app/build.gradle` die Release-URLs anpassen:

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

### Checkliste

- [ ] Metered.ca Account erstellt
- [ ] TURN Credentials erhalten
- [ ] URLs in build.gradle eingetragen

> **Detaillierte Anleitung:** [docs/TURN_SERVER_SETUP.md](TURN_SERVER_SETUP.md)

---

## Schritt 4: Release Keystore generieren

### 4.1 Keystore erstellen

```bash
keytool -genkey -v \
    -keystore securecall-release-key.jks \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias securecall \
    -dname "CN=SecureCall, OU=Mobile, O=StealthX, L=Berlin, ST=Berlin, C=DE"
```

> Sichere Passwörter wählen! Generieren: `openssl rand -base64 32`

### 4.2 Keystore sicher verwahren

- **NIEMALS** im Git-Repo committen (`.gitignore` blockiert `*.jks` bereits)
- Backup in Passwort-Manager (1Password, Bitwarden)
- Backup auf verschlüsseltem USB-Stick
- SHA-256 Fingerprint notieren: `keytool -list -v -keystore securecall-release-key.jks`

### 4.3 Signing-Variablen setzen

```bash
export SECURECALL_STORE_FILE=/pfad/zu/securecall-release-key.jks
export SECURECALL_STORE_PASSWORD=[STORE_PASSWORD]
export SECURECALL_KEY_ALIAS=securecall
export SECURECALL_KEY_PASSWORD=[KEY_PASSWORD]
```

### Checkliste

- [ ] Keystore generiert
- [ ] 2+ Backups erstellt (verschiedene Orte)
- [ ] SHA-256 Fingerprint notiert
- [ ] Signing-Variablen gesetzt

---

## Schritt 5: Release AABs bauen

### 5.1 Voraussetzungen

```bash
java -version        # JDK 17+
rustc --version      # Rust 1.70+
echo $ANDROID_HOME   # Android SDK Pfad

# Rust Android Targets
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
```

### 5.2 Bauen

```bash
cd client_android
./gradlew clean
./gradlew bundleFreeRelease bundleProRelease bundlePremiumRelease
```

Oder: `bash tools/build_release_aabs.sh`

### 5.3 Prüfen

```bash
ls -lh app/build/outputs/bundle/*/app-*-release.aab
# Alle AABs < 15 MB
```

### Checkliste

- [ ] Clean Build erfolgreich
- [ ] 3 AABs gebaut (FREE, PRO, PREMIUM)
- [ ] Alle AABs < 15 MB

---

## Schritt 6: Manual Testing auf echtem Gerät

### 6.1 APK installieren

```bash
./gradlew assembleFreeRelease
adb install app/build/outputs/apk/free/release/app-free-release.apk
```

### 6.2 Kritische Tests

```
[ ] App startet ohne Crash (Cold Start < 3s)
[ ] WebSocket verbindet zum Railway Server
[ ] Anruf zwischen 2 Geräten funktioniert
[ ] Audio beidseitig hörbar und klar
[ ] "Encrypted" Status wird angezeigt
[ ] FLAG_SECURE blockiert Screenshots (PRO/PREMIUM)
[ ] RAM < 150 MB (idle), < 300 MB (Anruf)
[ ] FREE: 15-Min-Limit + 10-Kontakte-Limit
```

> **Vollständige QA-Checkliste:** [docs/FINAL_QA_CHECKLIST.md](FINAL_QA_CHECKLIST.md)

### Checkliste

- [ ] APK auf Gerät installiert
- [ ] Alle kritischen Tests bestanden
- [ ] Kein Crash in Logcat

---

## Schritt 7: Google Play Developer Account

### 7.1 Account erstellen

1. Öffne https://play.google.com/console/signup
2. Google-Account einloggen
3. **25 EUR** Registrierungsgebühr bezahlen (einmalig)
4. Identitätsverifizierung abschließen (kann 2-7 Tage dauern)

> **Tipp:** Diesen Schritt früh starten wegen Wartezeit!

### 7.2 Account konfigurieren

```
1. Play Console → Settings → Developer account
2. Website: https://neabouli.github.io/stealth/
3. Privacy Policy URL: https://neabouli.github.io/stealth/privacy.html
4. Support: https://github.com/NeaBouli/stealth/issues
```

### Checkliste

- [ ] Account erstellt und 25 EUR bezahlt
- [ ] Identitätsverifizierung abgeschlossen
- [ ] Website und Privacy Policy URLs eingetragen

---

## Schritt 8: Play Console einrichten

### 8.1 App erstellen

```
1. Play Console → "Create app"
2. App name: "SecureCall"
3. Default language: German (de-DE)
4. Free or paid: Free
5. Declarations bestätigen → "Create app"
```

### 8.2 Store Listing ausfüllen

Texte aus `marketing/play_store/de/` und `marketing/play_store/en/`:

| Feld | Quelle |
|------|--------|
| App name | `title.txt` |
| Short description | `short_description.txt` |
| Full description | `full_description.txt` |
| App icon | `logo.png` (512x512 Version erstellen) |
| Feature graphic | 1024x500 PNG (muss erstellt werden) |
| Screenshots | Mind. 2 Phone-Screenshots (1080x1920) |

### 8.3 Content Rating + Data Safety

```
Content Rating: PEGI 3 (Communication, keine Gewalt/Sprache)

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
2. "Use Google-generated key" (empfohlen)
3. Upload Key: keytool -export -keystore securecall-release-key.jks -alias securecall -rfc > upload_cert.pem
4. upload_cert.pem hochladen
```

### Checkliste

- [ ] App erstellt in Play Console
- [ ] Store Listing komplett (DE + EN)
- [ ] App Icon + Screenshots hochgeladen
- [ ] Content Rating ausgefüllt
- [ ] Data Safety ausgefüllt
- [ ] App Signing konfiguriert

---

## Schritt 9: Internal Testing + Beta

### 9.1 Internal Testing

```
1. Play Console → Testing → Internal testing
2. "Create new release" → AAB hochladen
3. Tester-Liste erstellen (eigene E-Mail)
4. "Start rollout to Internal testing"
5. Opt-in Link öffnen → App aus Play Store installieren
```

### 9.2 Closed Beta (2-3 Wochen)

```
1. Testing → Closed testing → "Create new release"
2. AAB hochladen
3. 10-20 Tester einladen
4. Feedback sammeln über GitHub Issues
5. Bugs fixen, Update-AAB hochladen
```

### Exit-Kriterien (Beta → Production)

| Kriterium | Ziel |
|-----------|------|
| Crash-free Rate | > 99,5% |
| Kritische Bugs | 0 |
| Audio-Qualität | ≥ 4/5 |
| Tester mit erfolgreichem Anruf | > 80% |

### Checkliste

- [ ] Internal Testing bestanden
- [ ] 10-20 Beta-Tester aktiv
- [ ] Feedback eingearbeitet
- [ ] Exit-Kriterien erfüllt

---

## Schritt 10: Production Release

### 10.1 Pre-Release

```
[ ] Version in build.gradle erhöht (versionCode 3, versionName "1.0")
[ ] Git Tag: git tag -a v1.0 -m "v1.0 Production" && git push origin v1.0
[ ] Clean Build aller 3 AABs
[ ] Railway Server stabil (Health Check OK)
```

### 10.2 Staged Rollout

```
1. Play Console → Production → "Create new release"
2. AAB hochladen → Release Notes eintragen
3. "Start rollout to Production" → 5%
4. 2 Tage warten → Crash-Rate prüfen
5. 10% → 25% → 50% → 100% (je 2 Tage)
```

**Rollout stoppen bei:** Crash-Rate > 1% oder kritische Bugs

### Checkliste

- [ ] Production Release mit 5% Rollout gestartet
- [ ] Crash-Rate nach 48h geprüft (< 1%)
- [ ] Rollout schrittweise auf 100% erhöht
- [ ] App live im Play Store

---

## Schritt 11: Post-Launch

### Monitoring

- **Play Console:** Android Vitals → Crash Rate, ANR Rate
- **Railway:** Dashboard → Logs, Metrics
- **Metered.ca:** Dashboard → Usage (50 GB Limit)

### Support

- **GitHub Issues:** https://github.com/NeaBouli/stealth/issues
- **Play Store Reviews:** Innerhalb 24h antworten

### Marketing

```
Priorität 1 (Launch Day):
- Reddit: r/privacy, r/Android (verteilt über 3-5 Tage)
- Twitter/X: Launch-Post

Priorität 2 (Woche 1-2):
- Hacker News: "Show HN" Post
- Product Hunt Launch
- GitHub README mit Badges

Priorität 3 (Monat 1):
- Deutsche Tech-Medien: Heise, Golem, t3n, Kuketz Blog
- YouTube: Techlore, The Hated One
```

### Monat-1-Ziele

| Metrik | Ziel |
|--------|------|
| Downloads | 1.000+ |
| Rating | ≥ 4,0 Sterne |
| GitHub Stars | 500+ |
| Crash-free Rate | > 99,5% |

---

## Schritt 12: GitHub Wiki aktivieren

1. Repository Settings → Features → **Wikis** aktivieren
2. Wiki-Seiten aus `docs/WIKI/` kopieren (13 Seiten)
3. Detaillierte Anleitung: [docs/ENABLE_WIKI.md](ENABLE_WIKI.md)

Schnellste Methode via Git:
```bash
git clone https://github.com/NeaBouli/stealth.wiki.git
cp docs/WIKI/*.md stealth.wiki/
cd stealth.wiki && git add . && git commit -m "Add documentation" && git push
```

---

## Gesamte Launch-Checkliste

### Infrastruktur (0 EUR)
- [ ] GitHub Pages aktiviert und Website live
- [ ] Railway.app Signaling Server deployed
- [ ] Metered.ca TURN Server eingerichtet
- [ ] Health Check OK

### App Build
- [ ] Keystore generiert und sicher verwahrt
- [ ] Release URLs in build.gradle eingetragen
- [ ] 3 AABs gebaut und getestet
- [ ] APK auf echtem Gerät getestet

### Google Play (25 EUR)
- [ ] Developer Account erstellt
- [ ] App angelegt mit Store Listing
- [ ] Internal Testing bestanden
- [ ] Beta Testing abgeschlossen
- [ ] Production Release live

### Post-Launch
- [ ] Monitoring aktiv (Play Console + Railway)
- [ ] GitHub Wiki mit 13 Dokumentationsseiten
- [ ] Marketing-Posts veröffentlicht
- [ ] GitHub Issues als Support-Kanal aktiv
