# ANDROID-05 – VPN Firewall & Ghost Tunnel

## Ziel
Ein App-interner VPN-Service (VpnService API), der:

- nur GhostNet-Verbindungen erlaubt,
- andere Netzwerkzugriffe blockiert,
- optional Routing über Multi-Hop ermöglicht.

## Umfang

### 1. Ghost Tunnel
- eigener VPN-Tunnel
- erlaubt nur:
  - Signaling Server IP
  - GhostNet Relays
- alles andere → DROP

### 2. Implementierung
- VpnService.Builder
- Selbst definierte Allowed IP Routes
- Paketfilter (eingehend/ausgehend)

### 3. Sicherheitslogik
- App darf nicht ohne VPN kommunizieren
- bei Fehlern → Block / Call abbrechen

## Tests
- App außerhalb VPN → Block
- App mit VPN → OK
- Fremdtraffic → DROP

