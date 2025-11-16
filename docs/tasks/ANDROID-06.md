# TASK: ANDROID-06 – Device Owner Setup & App-Whitelist (Premium-Version)

## 1. Ziel des Tasks
Einrichtung des Provisioning- und Device-Owner-Flows der Premium-Version.
Damit erhält SecureCall erweiterte Systemrechte und kann:
- App-Installationen kontrollieren,
- nur genehmigte Apps zulassen (Whitelist),
- Systemfunktionen sperren (Developer-Options, USB-Debugging, App-Uninstall usw.).

Dies bildet die Grundlage für PhantomLine Elite (Premium).

---

## 2. Anforderungen

### 2.1 Device Owner Aktivierung
Die App muss sich über einen Provisioning-Prozess als "Device Owner" setzen.

Erlaubte Methoden:
- QR-Code-Provisioning im Setup Wizard
- ADB Provisioning (adb shell dpm set-device-owner …)
- NFC-Provisioning (optional)

Nach erfolgreicher Einrichtung erhält die App:
- volle Device-Policy-Control
- Kontrolle über App-Installationen
- erweiterten Zugriff auf Sicherheitsfunktionen

### 2.2 App-Whitelist
Implementiere eine sichere Whitelist-Mechanik:
- nur die Apps in der Whitelist dürfen ausgeführt werden
- alle anderen Apps:
  - dürfen nicht installiert werden
  - oder werden durch die Policy blockiert (je nach Setting)

Whitelist Format (JSON):

{
"allowed_packages": [
"com.securecall.client",
"com.android.settings",
"com.securecall.tools.filevault"
]
}

yaml
Code kopieren

Diese JSON wird später über die Premium-Management-API aktualisiert.

### 2.3 Systemrestriktionen
Wenn Device Owner aktiv ist:
- USB-Debugging deaktivieren
- Developer Options sperren
- Installation von Apps außerhalb der Whitelist verbieten
- Deinstallation von SecureCall verhindern
- optional: Kamera blockieren, wenn Policy es fordert

---

## 3. Architektur & Modulstruktur

client_android/
└── app/src/main/java/com/securecall/app/enterprise/
├── DeviceOwnerManager.kt
├── WhitelistManager.kt
├── ProvisioningFlow.kt
├── SystemRestrictions.kt
└── model/
├── WhitelistConfig.kt

yaml
Code kopieren

DeviceOwnerManager:
- prüft DeviceOwner-Status
- setzt/entfernt Device Owner (wo möglich)
- verwaltet DevicePolicyController-Funktionen

WhitelistManager:
- lädt Whitelist (lokal oder via Management-API)
- erzwingt App-Restriktionen über DevicePolicyManager

ProvisioningFlow:
- UI/QR-Code/ADB-Provisioning
- Fehler-Handling & Logs

SystemRestrictions:
- Konfiguriert Gerätesperren je nach Policy:
  - USB, Kamera, Screenshot, App-Installationen

---

## 4. Provisioning-Ablauf

1. Nutzer startet „Provisioning Mode“
2. App zeigt QR-Code an (oder wartet auf ADB-Befehl)
3. Gerät rebootet in Setup-Provisioning
4. App wird als Device Owner gesetzt
5. App liest initiale Whitelist
6. Systemrestriktionen werden aktiviert
7. Gerät ist nun im Premium-Sicherheitsmodus

---

## 5. Deliverables

- DeviceOwnerManager.kt mit allen Basisfunktionen
- WhitelistManager.kt + JSON-Parser + Validierung
- ProvisioningFlow.kt
- Beispiel-Whitelist unter `client_android/assets/default_whitelist.json`
- Manifest-Einträge für Device Owner Mode
- Aktivierter DeviceAdminReceiver
- Dokumentation der Provisioning-Schritte

---

## 6. Tests

### 6.1 Unit Tests
- Validierung der Whitelist
- Laden/Speichern der JSON-Konfiguration

### 6.2 Integration Tests
- ADB-Provisioning auf Testgerät
- Blockieren einer nicht-whitelisted App
- Installation erlaubter Apps

### 6.3 Sicherheits-Tests
- Versuch, SecureCall zu deinstallieren → MUSS blockieren
- Versuch, Developer-Options zu aktivieren → MUSS blockiert sein
- Versuch, fremde APK zu installieren → MUSS blockieren

---

## 7. Q&A (FAQ)

**F:** Funktioniert Device Owner auf jedem Gerät?  
**A:** Nein. Es funktioniert stabil nur auf ungebrandeten Stock-ROMs wie Pixel.
Hersteller mit schwer modifizierten ROMs können Probleme machen.

**F:** Muss der Nutzer nach dem Provisioning alles neu einrichten?  
**A:** Ja, das Gerät durchläuft den Setup Wizard. Das ist normal und gewollt.

**F:** Kann Device Owner wieder entfernt werden?  
**A:** Nur über definierten „Deprovisioning“-Flow, nicht durch den Nutzer selbst.

---

## 8. Referenzen
- ANDROID-04 Policy Engine
- SECURITY_DESIGN.md – Kapitel "Enterprise Hardening"
- PROJECT_PAPER.md – Produktlinie „PhantomLine Elite“
