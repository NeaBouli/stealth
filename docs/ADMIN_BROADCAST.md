# Emergency Broadcast System — Admin Guide

> **SECURITY:** `ADMIN_API_KEY` is stored ONLY in Railway environment variables.
> Never commit the actual key. Find it: Railway Dashboard → Variables → `ADMIN_API_KEY`

## How It Works
The server sends ONLY a numeric `template_id` to all connected clients.
No message text is transmitted — all alert content is pre-installed on each device.

This is a privacy-preserving design: even if the broadcast is intercepted,
it reveals nothing about the alert content.

## Available Templates

| ID | Icon | Title | Severity | Dismissable |
|----|------|-------|----------|-------------|
| 1 | Red | CRITICAL: Do Not Use SecureCall | CRITICAL | No |
| 2 | Orange | Security Alert | HIGH | No |
| 3 | Yellow | Critical Update Required | HIGH | Yes |
| 4 | Blue | Service Maintenance | LOW | Yes |
| 5 | Black | STEALTH PROTOCOL ACTIVATED | CRITICAL | No |
| 6 | Radio | Emergency Broadcast | HIGH | Yes |
| 7 | Warning | Network Compromise Warning | HIGH | Yes |
| 8 | Green | All Clear | INFO | Yes |

## Sending a Broadcast

### Via WebSocket (reaches online clients)
The server broadcasts to all connected WebSocket clients:
```json
{"type": "EMERGENCY_BROADCAST", "template_id": 3}
```

### Via FCM (reaches offline clients)
Server sends FCM data message to all registered tokens:
```json
{"type": "EMERGENCY_BROADCAST", "template_id": 3}
```

### Server Admin Endpoint
```bash
curl -X POST https://protective-healing-production.up.railway.app/admin/broadcast \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_KEY" \
  -d '{"template_id": 3}'
```

## Server Implementation (server.js)
Add this endpoint to the signaling server:

```javascript
app.post('/admin/broadcast', (req, res) => {
  const { template_id } = req.body;
  const auth = req.headers.authorization;
  if (auth !== `Bearer ${process.env.ADMIN_KEY}`) {
    return res.status(403).json({ error: 'unauthorized' });
  }
  if (!template_id || template_id < 1 || template_id > 8) {
    return res.status(400).json({ error: 'invalid template_id (1-8)' });
  }

  // Broadcast to all WebSocket clients
  const msg = JSON.stringify({ type: 'EMERGENCY_BROADCAST', template_id });
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) client.send(msg);
  });

  // Also send via FCM to all registered tokens
  // (implementation depends on your FCM setup)

  res.json({ ok: true, sent_to: wss.clients.size });
});
```

## Testing
Use template 8 (All Clear / INFO severity) for safe testing:
```bash
curl -X POST https://protective-healing-production.up.railway.app/admin/broadcast \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_KEY" \
  -d '{"template_id": 8}'
```
