# ANDROID-05 – VPN Firewall (MVP) – Abschlussbericht

**Status:** ✔ Abgeschlossen  
**Betroffene Module:**  
- `GhostVpnService`  
- `VpnController`  
- `CallActivity` (Erweiterung)  
- Manifest (VpnService-Registration)

---

## 1. Ziel
Erstellung eines minimalen VPN-Dienstes (GhostVPN), der als Grundlage
für zukünftige Policy-basierte Firewall-Regeln dient.

Diese Stufe stellt **nur die technische Basis** bereit:
- VPN-Dienst lässt sich starten/stoppen
- Interface wird erstellt
- Dummy-IP + Dummy-DNS
- keine echten Filterregeln
- Logging aktiv  
- Integration in den Call-Flow (Start bei Call-Beginn)

---

## 2. Implementierte Komponenten

### 2.1 GhostVpnService
- erzeugt ein VPN-Interface (Android `VpnService`)
- setzt eine Dummy-Adresse (`10.0.0.2/32`)
- setzt Dummy-DNS (1.1.1.1)
- startet/stopp sauber (MVP)
- liefert Log-Ausgaben über Start/Stop

### 2.2 VpnController
- kleine Helper-Class zum Starten/Stoppen
- wird von `CallActivity` genutzt
- Logging: Start/Stop-Anfragen werden ausgegeben

### 2.3 Manifest
- Service wurde korrekt registriert (Permission: BIND_VPN_SERVICE)
- App unterstützt nun den GhostVPN-Dienst

### 2.4 CallActivity Integration
Beim Start des Calls:
VpnController.start(...)

yaml
Code kopieren

Beim Beenden des Calls:
VpnController.stop(...)

yaml
Code kopieren

Damit ist GhostVPN voll funktional eingebunden.

---

## 3. Bekannte Einschränkungen (MVP-Charakter)
Diese Stufe enthält bewusst **noch keinen produktiven Firewall-Code**:

Nicht enthalten:
- keine Paketfilterung
- kein Blocken anderer Apps
- kein Split-Tunnel
- keine Policy-Engine-Verknüpfung
- keine IP- oder Port-Regeln
- keine aktive Überwachung des App-Traffics

Diese Features folgen in ANDROID-06/07/08.

---

## 4. Nächste Schritte
### Für ANDROID-06 (Firewall-Regeln)
- TUN-Socket lesen/schreiben
- Routing-Filter integrieren
- Basis: nur SecureCall → erlaubt, Rest → drop

### Für ANDROID-07 (Policy Binding)
- PolicyEngine + VPN verknüpfen
- VPN nur aktiv bei Pro/Premium
- UI-Status für VPN-Schutz hinzufügen

### Für ANDROID-08 (Packet Inspector)
- Logging bestimmter Pakete
- Debug-Modus für Entwickler

---

## 5. Dateien in diesem Patch
- `GhostVpnService.java` (neu)
- `VpnController.java` (neu)
- Änderungen in `CallActivity.java`
- Manifest erweitert
- `docs/android/ANDROID-05.md` (dieses Dokument)

