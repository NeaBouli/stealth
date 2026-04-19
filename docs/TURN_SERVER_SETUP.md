# SecureCall — TURN Server Setup (Metered.ca — Free)

> TURN Server via Metered.ca — 50 GB/month free, no own server needed.

## What is a TURN Server?

TURN (Traversal Using Relays around NAT) enables WebRTC connections when
direct peer-to-peer connections are blocked by firewalls or NAT.
Without TURN, ~10-15% of users cannot establish calls.

## Costs

| Plan | Price | Traffic | TURN Servers |
|------|-------|---------|-------------|
| **Free** | $0/mo | 50 GB/mo | Global (5 regions) |
| Starter | $29/mo | 500 GB/mo | Global |
| Growth | $99/mo | 2 TB/mo | Global + Premium |

> 50 GB/month is sufficient for ~5,000-10,000 minutes of calls via TURN relay.
> Most calls use direct P2P connection and don't need TURN.

---

## Step 1: Create Metered Account

1. Open https://www.metered.ca/signup
2. Create account (email + password)
3. Choose Free Plan
4. Confirm email

## Step 2: Generate TURN Credentials

1. Dashboard → "TURN Server" Tab
2. Click "Create Turn Server Credentials"
3. Metered automatically generates:
   - **API Key** (needed for REST API)
   - **TURN URLs** (multiple servers worldwide)

4. Under "TURN Server Credentials" you will find:
   - Username
   - Password (or Credential)
   - TURN Server URLs

## Step 3: Note TURN URLs

Metered provides multiple servers. Typical URLs:

```
stun:stun.relay.metered.ca:80
turn:global.relay.metered.ca:80
turn:global.relay.metered.ca:80?transport=tcp
turn:global.relay.metered.ca:443
turns:global.relay.metered.ca:443?transport=tcp
```

## Step 4: Integrate into Android App

### Option A: Static Credentials (simple)

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

### Option B: Dynamic via API (recommended for production)

Metered offers a REST API for temporary TURN credentials:

```bash
curl "https://[APP_NAME].metered.live/api/v1/turn/credentials?apiKey=[API_KEY]"
```

Response:
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

Integration in the signaling server (`backend/signaling/src/server.js`):
```javascript
// Fetch TURN credentials from Metered API during call setup
const turnResponse = await fetch(
    `https://${METERED_APP}.metered.live/api/v1/turn/credentials?apiKey=${METERED_API_KEY}`
);
const iceServers = await turnResponse.json();
// Send to both clients as part of the signaling message
```

## Step 5: Test

### WebRTC Trickle ICE Test

1. Open https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/
2. Add server:
   - URI: `turn:global.relay.metered.ca:80`
   - Username: `[METERED_USERNAME]`
   - Credential: `[METERED_CREDENTIAL]`
3. Click "Gather candidates"
4. Check if `relay` candidates appear (= TURN works)

### Test in the App

1. Install app on two devices
2. Start a call
3. Check Logcat: `adb logcat | grep -i "turn\|relay\|ice"`
4. Check if "relay" is displayed as Connection Type

---

## Monitoring

- **Dashboard:** https://dashboard.metered.ca
- **Usage:** Dashboard → Usage → Check traffic consumption
- **Alerts:** Dashboard → Settings → Email notification at 80% limit

## Self-Hosted Alternative

If you later want to run your own TURN server:
- See `deployment/coturn_config/turnserver.conf`
- Guide: `docs/PRODUCTION_DEPLOYMENT.md` → Step 5
- Cost: ~€4.35/mo (Hetzner CX22)
