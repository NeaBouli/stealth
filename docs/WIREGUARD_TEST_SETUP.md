# WireGuard Test Setup — SecureCall

> Created: 2026-03-21
> Purpose: Local WireGuard tunnel Mac↔S10 for testing VPN integration

## Prerequisites
- `brew install wireguard-tools` ✅ (v1.0.20260223)
- Mac and S10 on the same WiFi network

## Generated Keys

| Role  | Public Key |
|--------|-----------|
| Server (Mac) | `ui2z9Yb81/PhUPFNP84ZTI7DhFo5PjZAEzVwkKtNGU4=` |
| Client (S10) | `mrnczWhlyGEMZDtTBgYQ6RPTznrw3YJQ24FsGF9bhDM=` |

> Private keys are stored only in `/tmp/wg_*_private.key` — DO NOT commit!

## Start Mac as Test Server

```bash
# Copy config to WireGuard directory
sudo mkdir -p /usr/local/etc/wireguard
sudo cp /tmp/wg-test-server.conf /usr/local/etc/wireguard/wg-test.conf

# Start tunnel
sudo wg-quick up wg-test

# Check status
sudo wg show

# Stop tunnel
sudo wg-quick down wg-test
```

### Server Config (`/tmp/wg-test-server.conf`)
```ini
[Interface]
PrivateKey = <in /tmp/wg_server_private.key>
Address = 10.99.0.1/31
ListenPort = 51821

[Peer]
PublicKey = mrnczWhlyGEMZDtTBgYQ6RPTznrw3YJQ24FsGF9bhDM=
AllowedIPs = 10.99.0.2/32
```

## S10 Client Config

Transfer the file `/tmp/securecall-test.conf` to the S10 and import it in SecureCall Premium Settings.

```ini
[Interface]
PrivateKey = <in /tmp/wg_client_private.key>
Address = 10.99.0.2/31
DNS = 1.1.1.1

[Peer]
PublicKey = ui2z9Yb81/PhUPFNP84ZTI7DhFo5PjZAEzVwkKtNGU4=
Endpoint = 192.168.1.104:51821
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25
```

### Transfer Config to S10
```bash
# Option 1: Via ADB push
adb -s RF8N313QMFL push /tmp/securecall-test.conf /sdcard/Download/

# Option 2: Enter manually in SecureCall
# Premium Settings → WireGuard → Import config
```

## Test Steps for Tonight

1. **Start Mac server:**
   ```bash
   sudo cp /tmp/wg-test-server.conf /usr/local/etc/wireguard/wg-test.conf
   sudo wg-quick up wg-test
   ```

2. **Connect S10** (Serial: `RF8N313QMFL`)

3. **Push config to S10:**
   ```bash
   adb -s RF8N313QMFL push /tmp/securecall-test.conf /sdcard/Download/
   ```

4. **Import in SecureCall:**
   - Premium Settings → VPN/WireGuard → Load config file

5. **Verify tunnel:**
   ```bash
   # On Mac — client should appear as a peer
   sudo wg show

   # Ping from Mac to client
   ping 10.99.0.2
   ```

6. **Call test over VPN tunnel:**
   - Call S7 → S10
   - Check whether the call runs over the WireGuard tunnel
   - Search SecureCall logs for `WireGuard` / `VPN` entries

## Network Info
- **Mac IP (LAN):** `192.168.1.104`
- **WireGuard Subnet:** `10.99.0.0/31`
- **Server WG IP:** `10.99.0.1`
- **Client WG IP:** `10.99.0.2`
- **Listen Port:** `51821`

## Fallback Options
If the local tunnel does not work:
1. **Mullvad** — Giorgio has an account, generate config at mullvad.net/account
2. **Proton VPN** — Free plan, WireGuard config downloadable
