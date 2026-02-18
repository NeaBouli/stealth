# SecureCall — TURN Server Setup (Metered.ca — Gratis)

> TURN Server über Metered.ca — 50 GB/Monat kostenlos, kein eigener Server nötig.

## Was ist ein TURN Server?

TURN (Traversal Using Relays around NAT) ermöglicht WebRTC-Verbindungen wenn
direkte Peer-to-Peer Verbindungen durch Firewalls oder NAT blockiert werden.
Ohne TURN können ~10-15% der Nutzer keine Anrufe aufbauen.

## Kosten

| Plan | Preis | Traffic | TURN Servers |
|------|-------|---------|-------------|
| **Free** | $0/mo | 50 GB/mo | Global (5 Regionen) |
| Starter | $29/mo | 500 GB/mo | Global |
| Growth | $99/mo | 2 TB/mo | Global + Premium |

> 50 GB/Monat reicht für ~5.000-10.000 Minuten Anrufe über TURN-Relay.
> Die meisten Anrufe nutzen direkte P2P-Verbindung und brauchen kein TURN.

---

## Schritt 1: Metered Account erstellen

1. Öffne https://www.metered.ca/signup
2. Account erstellen (E-Mail + Passwort)
3. Free Plan wählen
4. E-Mail bestätigen

## Schritt 2: TURN Credentials generieren

1. Dashboard → "TURN Server" Tab
2. "Create Turn Server Credentials" klicken
3. Metered generiert automatisch:
   - **API Key** (wird für REST API benötigt)
   - **TURN URLs** (mehrere Server weltweit)

4. Unter "TURN Server Credentials" findest du:
   - Username
   - Password (oder Credential)
   - TURN Server URLs

## Schritt 3: TURN URLs notieren

Metered stellt mehrere Server bereit. Typische URLs:

```
stun:stun.relay.metered.ca:80
turn:global.relay.metered.ca:80
turn:global.relay.metered.ca:80?transport=tcp
turn:global.relay.metered.ca:443
turns:global.relay.metered.ca:443?transport=tcp
```

## Schritt 4: Android App integrieren

### Option A: Statische Credentials (einfach)

In `client_android/app/build.gradle`:

```groovy
release {
    buildConfigField "String", "STUN_URL",
        "\"stun:stun.relay.metered.ca:80\""
    buildConfigField "String", "TURN_URL",
        "\"turn:global.relay.metered.ca:80\""
    buildConfigField "String", "TURNS_URL",
        "\"turns:global.relay.metered.ca:443?transport=tcp\""
    buildConfigField "String", "TURN_USERNAME",
        "\"[METERED_USERNAME]\""
    buildConfigField "String", "TURN_CREDENTIAL",
        "\"[METERED_CREDENTIAL]\""
}
```

### Option B: Dynamisch über API (empfohlen für Production)

Metered bietet eine REST API für temporäre TURN Credentials:

```bash
curl "https://[APP_NAME].metered.live/api/v1/turn/credentials?apiKey=[API_KEY]"
```

Antwort:
```json
[
  { "urls": "stun:stun.relay.metered.ca:80" },
  {
    "urls": "turn:global.relay.metered.ca:80",
    "username": "temp_user_123",
    "credential": "temp_pass_456"
  }
]
```

Integration im Signaling Server (`backend/signaling/src/server.js`):
```javascript
// Beim Call-Setup TURN Credentials vom Metered API holen
const turnResponse = await fetch(
    `https://${METERED_APP}.metered.live/api/v1/turn/credentials?apiKey=${METERED_API_KEY}`
);
const iceServers = await turnResponse.json();
// An beide Clients senden als Teil der Signaling-Nachricht
```

## Schritt 5: Testen

### WebRTC Trickle ICE Test

1. Öffne https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/
2. Server hinzufügen:
   - URI: `turn:global.relay.metered.ca:80`
   - Username: `[METERED_USERNAME]`
   - Credential: `[METERED_CREDENTIAL]`
3. "Gather candidates" klicken
4. Prüfe ob `relay` Candidates erscheinen (= TURN funktioniert)

### In der App testen

1. App auf zwei Geräte installieren
2. Anruf starten
3. Logcat prüfen: `adb logcat | grep -i "turn\|relay\|ice"`
4. Prüfe ob "relay" als Connection Type angezeigt wird

---

## Monitoring

- **Dashboard:** https://dashboard.metered.ca
- **Usage:** Dashboard → Usage → Traffic-Verbrauch prüfen
- **Alerts:** Dashboard → Settings → E-Mail-Benachrichtigung bei 80% Limit

## Selbst-gehostete Alternative

Falls du später einen eigenen TURN-Server betreiben möchtest:
- Siehe `deployment/coturn_config/turnserver.conf`
- Anleitung: `docs/PRODUCTION_DEPLOYMENT.md` → Schritt 5
- Kosten: ~€4.35/mo (Hetzner CX22)
