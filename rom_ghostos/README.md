# GHOSTOS BlackRoot – Hardened Secure OS

Dieses Verzeichnis enthält alle Arbeiten am gehärteten Spezialbetriebssystem
„GHOSTOS BlackRoot“, der höchsten Sicherheitsstufe des SecureCall Ecosystems.

## Ziele
- vollständige OS-Härtung (Kernel + Systemdienste)
- minimale Oberfläche (keine Fremd-Apps)
- ausschließlicher Fokus auf sichere Kommunikation
- SecureCall als System-App mit erweiterten Rechten
- keine Google-Services, kein WebView, kein Play Store

## Hauptkomponenten
### 1. Kernel & System Hardening
- SELinux strikt
- keine Debug-Schnittstellen
- nur notwendige Systemdienste aktiv
- RAM-Purge nach jeder Session

### 2. Netzwerkrestriktionen
- Kernel-level Firewall
- LTE-only / No-GSM Modi
- deaktivierte Hintergrundverbindungen
- optional deaktiviertes WLAN/Bluetooth/NFC

### 3. SecureCall System Integration
- System-App mit exklusivem Mikrofonzugriff
- GhostNet Transport tief integriert
- Device Owner Funktionen überflüssig (OS übernimmt Kontrolle)

## Relevante Dokumente
- docs/SECURITY_DESIGN.md
- docs/ARCHITECTURE_OVERVIEW.md
- docs/tasks/ROM-01.md

