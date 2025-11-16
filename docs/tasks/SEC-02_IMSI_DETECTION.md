# SEC-02 – IMSI-Catcher Detection & Funkzellen-Monitoring

## Ziel
Erkennung von verdächtigen Funkzellen und Baseband-Manipulationen.

## Umfang

### 1. Erfasste Parameter
- MCC/MNC
- LAC/Tracking Area Code
- Cell-ID
- Netzwerktyp (2G/3G/4G/5G)
- Signalstärke
- Frequenzband (falls verfügbar)

### 2. Bedrohungsmuster
- 2G-Downgrade ohne Grund
- Cell-ID-Wechsel mit unüblicher Häufung
- Fake LAC (Region passt nicht zum Ort)
- ungewöhnlich hohe Sendeleistung
- plötzliche Netzwechsel

### 3. Erkennungsmethoden
- heuristisches Modell
- Blacklist auffälliger Cell-IDs
- Vergleich mit Netzwerk-Whitelist (optional)
- DeviceOwner erlaubt zusätzliche Datenpunkte

### 4. Reaktion (Premium)
- Warnung an Nutzer
- Empfehlung: Call abbrechen
- je nach Policy: automatischer Block

## Tests
- Cell-ID mit Blacklist → Warnung
- 2G-Downgrade → Warnung/Block
- unplausible LAC → Warnung

