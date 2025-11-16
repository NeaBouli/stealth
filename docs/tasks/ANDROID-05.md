# TASK: ANDROID-05 – VPN-Firewall & Ghost Tunnel (Pro-Version)

## 1. Ziel des Tasks
Implementierung eines app-internen VPN-Dienstes (Android VpnService),
der sämtlichen App-Traffic kontrolliert und nur sichere GhostNet-Verbindungen
erlaubt. Dies bildet die Sicherheitsgrundlage aller Pro- und Premium-Funktionen.

---

## 2. Anforderungen

### 2.1 Funktionsumfang
- vollständiger VPN-Dienst basierend auf `VpnService`
- Filtern von Traffic nach Ziel-IP, Port, Protokoll
- Whitelist-basiertes Routing:
  - nur GhostNet-Relays sind erlaubt
  - alles andere wird gedroppt
- Überwachung eingehender/ausgehender Pakete
- Logging NUR für technische Diagnose (ohne Metadaten)

### 2.2 Ghost Tunnel (erweiterter Modus)
- optionaler Multi-Hop Modus (2–3 Relays)
- verschleierte Quell-/Zielpakete
- optionale Padding-Pakete zur Traffic-Obfuskation

---

## 3. Architektur & Modulstruktur

client_android/
└── app/src/main/java/com/securecall/app/vpn/
├── GhostVpnService.kt
├── FirewallRules.kt
├── AllowedEndpoints.kt
├── PacketInspector.kt
├── GhostTunnel.kt
├── utils/
│ ├── IpUtils.kt
│ ├── PacketParser.kt

yaml
Code kopieren

GhostVpnService:
- startet & stoppt VPN
- erstellt TUN-Interface
- leitet erlaubte Pakete weiter
- blockiert verbotene Pakete

FirewallRules:
- definiert, welche Ziele erlaubt/blockiert werden
- Profile für Free/Pro/Premium/OS

PacketInspector:
- analysiert IP/TCP/UDP-Pakete
- erkennt unerwartete Verbindungen

GhostTunnel:
- Multi-Hop-Routing
- Padding-Pakete
- Traffic-Obfuskation

---

## 4. Workflow (Packet Flow)

[App Traffic]
↓
[GhostVpnService] -- TUN Interface -->
↓
[PacketInspector]
↓
[FirewallRules]
→ ALLOW → GhostNet Relay (WebRTC/QUIC)
→ DROP → wird verworfen

yaml
Code kopieren

---

## 5. Firewall-Regeln (Pro-Version)

| Kategorie                        | Verhalten |
|----------------------------------|-----------|
| GhostNet Relay IPs/Ports         | ALLOW     |
| TURN/STUN Server                 | ALLOW     |
| DNS                              | BLOCK (eigener Resolver optional) |
| HTTP/HTTPS extern                | BLOCK     |
| andere Apps (systemweit)         | NICHT betroffen |
| ICMP                              | BLOCK     |
| IPv6                              | optional deaktivieren |

---

## 6. Deliverables

- GhostVpnService.kt voll funktionsfähig  
- FirewallRules.kt mit Strukturen für alle Produktlinien  
- PacketInspector.kt implementiert  
- Multi-Hop GhostTunnel (MVP, Dummy-Hop reicht)  
- Manifest + Permissions + Foreground Service Notification  
- Integration in Policy Engine (ANDROID-04)  
- vollständige Dokumentation im Kopf-Kommentar  

---

## 7. Tests

### 7.1 Unit Tests
- Parsing von Paketen
- Rule-Matching
- Erlaubte vs. blockierte Ziele

### 7.2 Integration Tests
- Start/Stop des VPN
- realer Test: GhostNet Call → funktioniert
- realer Test: Zugriff auf google.com → blockiert

### 7.3 Security Tests
- DNS-Leak-Test
- IPv6-Leak-Test
- App versucht HTTP → blockiert

---

## 8. Q&A (FAQ)

**F:** Müssen wir echten Multi-Hop schon jetzt bauen?  
**A:** Nein, ein einzelner Dummy-Hop reicht. Full Multi-Hop folgt später in ANDROID-07.

**F:** Gibt es ein UI für das VPN?  
**A:** Ja, ein einfacher Switch in den Einstellungen (Pro-Version).

**F:** Muss der VPN-Dienst dauerhaft laufen?  
**A:** Nur während eines Calls oder wenn die App aktiv ist (Konfiguration später erweiterbar).

---

## 9. Referenzen
- ANDROID-04 Policy Engine
- SECURITY_DESIGN.md
- ARCHITECTURE_OVERVIEW.md
