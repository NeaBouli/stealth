# Network & VPN Compatibility Audit

> Date: 2026-03-19
> Scope: Full read-only audit of all network-related code paths
> Devices tested: S10 (Mullvad VPN), S7 (COSMOTE WiFi), Tab S4 (COSMOTE WiFi)

---

## 1. WebSocket Transport

### Protocol: WSS (TLS)

| Component | Value | Source |
|-----------|-------|--------|
| URL | `wss://protective-healing-production.up.railway.app/signal` | `build.gradle:137` |
| Library | OkHttp (via HeartbeatClient) | `WebSocketService.kt:27` |
| Ping interval | 5s (server heartbeat) | `heartbeat.js` |
| Timeout | 60s no-pong → disconnect | `heartbeat.js` |
| Reconnect | HeartbeatClient auto-reconnect with backoff | `HeartbeatClient.kt` |

**Finding:** All signaling uses encrypted WebSocket (WSS). No plaintext `ws://` in production code. `network_security_config.xml` only permits cleartext for `localhost`/`127.0.0.1` (development).

### OkHttp & VPN/Proxy Behavior

OkHttp uses the JVM default `ProxySelector` and `SSLSocketFactory`. No custom proxy or socket factory is configured in the codebase.

**This means:**
- OkHttp **automatically routes through system VPN** (Android VPN sets up a TUN interface that captures all traffic)
- OkHttp **respects system proxy settings** (HTTP_PROXY, manual proxy in WiFi settings)
- No code bypasses VPN tunneling — all traffic goes through the VPN if active
- Mullvad VPN on S10 works because it routes all traffic through its tunnel, and `railway.app` is not blocked

---

## 2. STUN/TURN Configuration

### Client-side (build.gradle)

| Server | URL | Auth | Source |
|--------|-----|------|--------|
| STUN | `stun:stun.l.google.com:19302` | None | `build.gradle:138` |
| TURN | `turn:a.relay.metered.ca:443?transport=tcp` | Username + password | `build.gradle:139-142` |

### Server-side (server.js)

| Server | URL | Auth | Source |
|--------|-----|------|--------|
| STUN | `process.env.STUN_URL` or `stun:stun.l.google.com:19302` | None | `server.js:27` |
| TURN | `process.env.TURN_URL` with `TURN_USER`/`TURN_PASS` | Env vars | `server.js:28-31` |

### VPN Compatibility

- **STUN (Google, UDP:19302):** Works over VPN. Most VPNs pass UDP. STUN is a simple request/response — no persistent connection.
- **TURN (Metered.ca, TCP:443):** Excellent VPN compatibility. Uses TCP on port 443, which looks like HTTPS traffic. VPNs, firewalls, and corporate proxies almost never block this.
- **WebRTC DataChannel:** After ICE negotiation, P2P audio goes through the DataChannel. If direct P2P fails (symmetric NAT behind VPN), TURN relay handles it over TCP:443.

**Finding:** TURN over TCP:443 is the most VPN-friendly configuration possible. This is correct.

### Security Issue: Hardcoded TURN Credentials

TURN credentials are hardcoded in `build.gradle`:
```
TURN_USERNAME = "REDACTED_TURN_USERNAME"
TURN_PASSWORD = "REDACTED_TURN_PASSWORD"
```

These are baked into every APK and extractable via decompilation. Already tracked as BUG-2 in HANDOVER.md and TODO-003 in TODO.md.

**Recommendation:** Fetch credentials at runtime from `/ice-servers` endpoint (already exists, admin-only).

---

## 3. Hardcoded IPs

**Finding:** No hardcoded IP addresses in production code paths. All endpoints use domain names:
- `protective-healing-production.up.railway.app` (signaling)
- `stun.l.google.com` (STUN)
- `a.relay.metered.ca` (TURN)
- `eth.llamarpc.com` / `rpc.ankr.com` / `cloudflare-eth.com` (Ethereum RPC)

This means DNS resolution is required — but all VPNs provide DNS resolution.

---

## 4. GL.iNet Router Issue Explained

**Why S10 (Mullvad) works but GL.iNet (GL-MT300N-V2-5df) blocked Railway.app:**

The GL.iNet travel router runs OpenWrt with custom firmware. It has:
- Built-in ad/tracker blocking (similar to Pi-hole)
- DNS filtering that can block `*.railway.app` or `*.up.railway.app` domains
- Possible SNI-based filtering on HTTPS connections

Mullvad VPN on S10 bypasses the GL.iNet DNS entirely — Mullvad uses its own DNS servers inside the tunnel. So even when S10 is connected to GL.iNet WiFi, all DNS queries go through Mullvad → Railway.app resolves correctly.

S7 and Tab S4 without VPN used GL.iNet's DNS → `railway.app` was blocked → WebSocket failed to connect.

**Fix applied:** S7 and Tab S4 moved to COSMOTE-37vfbu network (standard ISP router, no DNS blocking). Confirmed working via logcat.

---

## 5. VPN Features: Implemented vs Stubbed

### GhostVpnService (WireGuard) — STUB

| Component | File | Status |
|-----------|------|--------|
| `GhostVpnService.kt` | `app/src/main/java/.../vpn/` | Exists, basic VpnService scaffold |
| `VpnController.kt` | `app/src/main/java/.../vpn/` | Config read/write, start/stop methods |
| Settings UI | `SettingsFragment.kt:355-431` | Toggle, config import, kill switch, status display |
| WireGuard tunnel | — | NOT implemented — no actual tunnel creation |
| Config format | SharedPreferences | `vpn_server_endpoint`, `vpn_server_port`, `vpn_kill_switch` |

**Status:** The VPN UI is complete (Premium tier only). The underlying WireGuard tunnel is NOT functional. There is no WireGuard library dependency, no actual tunnel establishment, no key exchange. Already tracked as BUG-006 (KNOWN STUB).

### What would be needed for real VPN:
1. Add WireGuard library (`com.wireguard.android:tunnel`)
2. Implement tunnel creation in `GhostVpnService`
3. Handle split tunneling (route only SecureCall traffic through VPN, or all traffic)
4. Server-side: WireGuard endpoint to connect to

---

## 6. Network Security Config

`app/src/main/res/xml/network_security_config.xml`:
```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```

**Finding:** Cleartext only allowed for localhost. All production traffic is TLS-only. No certificate pinning configured (would break behind corporate MITM proxies — acceptable tradeoff for a beta).

---

## 7. Logcat Evidence (2026-03-19)

### S10 (RF8N313QMFL) — Premium, Mullvad VPN active
```
11:04:37 OkHttp WebSocket https://protective-healing-production.up.railway.app/... writer
11:23:05 ONLINE_STATUS_REQUEST sent: 2 phones
11:23:05 ONLINE_STATUS_RESPONSE: 2 phones, 2 online
11:24:20 ONLINE_STATUS_RESPONSE: {"+491752536807":true,"+4915203487046":false} (S7 stopped)
11:24:50 ONLINE_STATUS_RESPONSE: {"+491752536807":true,"+4915203487046":true} (S7 restarted)
```
→ WebSocket + presence fully functional over Mullvad VPN.

### S7 (ce10160adc00152604) — Pro, COSMOTE WiFi (no VPN)
```
11:14:22 ConnectivityService: WIFI state: CONNECTED, extra: "COSMOTE-37vfbu"
11:22:07 ContactsFragment: refreshOnlineStatus: no registered phones cached, skipping
```
→ WiFi connected, but registered phones cache empty after app restart (BUG-007).

### Tab S4 (ce12182c68644439037e) — Free, COSMOTE WiFi (no VPN)
```
11:16:00 ConnectivityService: WIFI state: CONNECTED
```
→ Connected. Free tier does not show online status (by design).

---

## 8. Summary & Recommendations

| # | Finding | Risk | Action |
|---|---------|------|--------|
| 1 | All traffic uses TLS (WSS/HTTPS) | None | No change needed |
| 2 | OkHttp respects system VPN automatically | None | No change needed |
| 3 | TURN over TCP:443 — excellent VPN/firewall compat | None | No change needed |
| 4 | No hardcoded IPs | None | No change needed |
| 5 | TURN credentials hardcoded in APK | **High** | TODO-003: Fetch from /ice-servers at runtime |
| 6 | GL.iNet DNS blocking Railway.app | **Medium** | Document: users on restrictive networks need VPN or different DNS |
| 7 | VPN (WireGuard) is a stub | Low | BUG-006: Known, low priority |
| 8 | No certificate pinning | Low | Consider for v2 release builds |
| 9 | Registered phones cache lost on restart | **Medium** | BUG-007: Persist in SharedPreferences |
