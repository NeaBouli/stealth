# OS-05 – Network Profiles (LTE-only, No-GSM, No-WiFi)

## Ziel
Erstellung eines netzwerkseitigen Restriktionssystems für GHOSTOS BlackRoot, um Funkprofile sicher zu steuern.

## Anforderungen

### 1. Profile
- **LTE-only Mode**
  - GSM/2G deaktiviert (hohes Risiko für Abhörbarkeit)
  - 3G optional deaktivieren
  - 4G/5G erlaubt

- **No-GSM Mode**
  - GSM vollständig blockiert
  - keine Circuit-Switched Anrufe

- **AirGap Mode**
  - WLAN deaktiviert
  - mobile Daten deaktiviert
  - Bluetooth/NFC deaktiviert
  - nur GhostNet über Relay-Layer aktiv (falls aktiviert)

### 2. Implementierung
- Änderung der Radio HAL Konfiguration
- Blockieren unsicherer Funk-Stacks
- Überwachen von unerwarteten Funkänderungen
  → Policy Engine entscheidet bei Abweichungen (AUTO-BLOCK)

### 3. UI
- keine Umschaltung durch Nutzer in Free/Pro
- Premium/OS: Profile sind Teil der Policies

## Deliverables
- rom_ghostos/network/PROFILE_CONFIG.md
- modifizierte HAL-Konfiguration

## Tests
- GSM darf nicht aktivierbar sein
- LTE-only stabil
- unerlaubte Funkprofile → Blockade + Warnung

