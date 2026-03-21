# WireGuard Test-Setup — SecureCall

> Erstellt: 2026-03-21
> Zweck: Lokaler WireGuard-Tunnel Mac↔S10 zum Testen der VPN-Integration

## Voraussetzungen
- `brew install wireguard-tools` ✅ (v1.0.20260223)
- Mac und S10 im selben WLAN-Netz

## Generierte Keys

| Rolle  | Public Key |
|--------|-----------|
| Server (Mac) | `ui2z9Yb81/PhUPFNP84ZTI7DhFo5PjZAEzVwkKtNGU4=` |
| Client (S10) | `mrnczWhlyGEMZDtTBgYQ6RPTznrw3YJQ24FsGF9bhDM=` |

> Private Keys liegen nur in `/tmp/wg_*_private.key` — NICHT committen!

## Mac als Test-Server starten

```bash
# Config nach WireGuard-Verzeichnis kopieren
sudo mkdir -p /usr/local/etc/wireguard
sudo cp /tmp/wg-test-server.conf /usr/local/etc/wireguard/wg-test.conf

# Tunnel starten
sudo wg-quick up wg-test

# Status prüfen
sudo wg show

# Tunnel stoppen
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

Die Datei `/tmp/securecall-test.conf` auf das S10 übertragen und in SecureCall Premium Settings importieren.

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

### Config auf S10 übertragen
```bash
# Option 1: Via ADB push
adb -s RF8N313QMFL push /tmp/securecall-test.conf /sdcard/Download/

# Option 2: In SecureCall manuell eingeben
# Premium Settings → WireGuard → Config importieren
```

## Test-Schritte heute Abend

1. **Mac Server starten:**
   ```bash
   sudo cp /tmp/wg-test-server.conf /usr/local/etc/wireguard/wg-test.conf
   sudo wg-quick up wg-test
   ```

2. **S10 anschließen** (Serial: `RF8N313QMFL`)

3. **Config auf S10 pushen:**
   ```bash
   adb -s RF8N313QMFL push /tmp/securecall-test.conf /sdcard/Download/
   ```

4. **In SecureCall importieren:**
   - Premium Settings → VPN/WireGuard → Config-Datei laden

5. **Tunnel prüfen:**
   ```bash
   # Auf Mac — Client sollte als Peer erscheinen
   sudo wg show

   # Ping vom Mac zum Client
   ping 10.99.0.2
   ```

6. **Call-Test über VPN-Tunnel:**
   - S7 → S10 anrufen
   - Prüfen ob Call über WireGuard-Tunnel läuft
   - In SecureCall Logs nach `WireGuard` / `VPN` Einträgen suchen

## Netzwerk-Info
- **Mac IP (LAN):** `192.168.1.104`
- **WireGuard Subnet:** `10.99.0.0/31`
- **Server WG IP:** `10.99.0.1`
- **Client WG IP:** `10.99.0.2`
- **Listen Port:** `51821`

## Fallback-Optionen
Falls lokaler Tunnel nicht funktioniert:
1. **Mullvad** — Giorgio hat Account, Config unter mullvad.net/account generieren
2. **Proton VPN** — Free Plan, WireGuard Config downloadbar
