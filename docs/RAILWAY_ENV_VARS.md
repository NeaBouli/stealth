# Railway Environment Variables Setup

## Dashboard URL
https://railway.com/project/263caa21-e6f6-4075-9470-22427cfcf5f9

## Steps
1. Open Railway Dashboard (link above)
2. Click on your service → **Settings** → **Variables**
3. Add each variable below

## Required Variables

| Variable | Value |
|----------|-------|
| `NODE_ENV` | `production` |
| `PORT` | `8080` |
| `STUN_URL` | `stun:stun.l.google.com:19302` |
| `TURN_URL` | `turn:a.relay.metered.ca:443?transport=tcp` |
| `TURN_USER` | *(from Metered.ca Dashboard → TURN Server → Credentials)* |
| `TURN_PASS` | *(from Metered.ca Dashboard → TURN Server → Credentials)* |
| `ADMIN_API_KEY` | *(generate with `openssl rand -base64 32`)* |

## Data volume and non-root image

The signaling image runs as UID 1001 (`securecall`). Railway mounts volumes as
root, so a service with a volume at `/app/data` needs `RAILWAY_RUN_UID=0`
(Railway docs, Volumes). With `NODE_ENV=production` the server refuses to start
when the data directory is not writable instead of silently using
`/tmp/stealthx-data` (which would drop licenses and activation codes on
restart). `DATA_DIR_EPHEMERAL_OK=1` re-enables that fallback explicitly; do not
set it for a production service.

## Generate ADMIN_API_KEY

```bash
openssl rand -base64 32
```

Copy the output and paste it as the value for `ADMIN_API_KEY`.

## After Adding Variables
Railway will automatically redeploy (~2 min).

## Verify Deployment

```bash
# Health check
curl https://protective-healing-production.up.railway.app/health

# ICE servers (requires ADMIN_API_KEY)
curl -H "X-Admin-Key: YOUR_ADMIN_KEY" https://protective-healing-production.up.railway.app/ice-servers
```
