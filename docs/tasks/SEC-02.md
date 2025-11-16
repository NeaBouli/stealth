# TASK: SEC-02 – IMSI-Catcher Detection & Funkzellenmonitoring (Premium/OS)

## 1. Ziel des Tasks
Entwicklung eines Moduls zur Erkennung und Meldung verdächtiger Funkzellen.
Dieses Modul soll:
- IMSI-Catcher erkennen (heuristisch),
- 2G-Downgrade-Angriffe melden,
- unstabile oder gefälschte Cell-IDs erkennen,
- Premium- und OS-Policies anwenden (Warnung / Blockierung).

Das System wird **keine Bewegungsprofile** speichern – alles rein lokal.

---

## 2. Anforderungen

### 2.1 Überwachte Parameter

Das Modul überwacht ausschließlich:
- MCC (Mobile Country Code)
- MNC (Mobile Network Code)
- LAC (Location Area Code)
- CID (Cell ID)
- Funktyp (2G/3G/4G/5G)
- Signalstärke
- Nachbarzellen (optional)

### 2.2 Alarmbedingungen

Das System soll Alarm schlagen, wenn:
1. das Gerät auf 2G gedowngradet wird (z. B. während eines Calls),
2. ungewöhnlich viele Zellen mit gleicher CID gefunden werden,
3. MCC/MNC nicht plausibel sind,
4. der Netztyp während der Session mehrfach springt,
5. CID/LAC aus bekannter Fake-Catcher-Liste stammen (lokale DB),
6. Signalstärken extrem unlogisch sind (z. B. zu stark trotz Entfernung).

### 2.3 Anzeigen und Aktionen

Bei Verdacht:
- Premium: UI-Warnung + optional Call-Abbruch
- OS: Sofort-Blockierung + Funkmodi-Einschränkungen

---

## 3. Architektur & Modulstruktur

client_android/
└── app/src/main/java/com/securecall/app/cellmonitor/
├── CellMonitor.kt
├── DetectorHeuristics.kt
├── CellInfoProvider.kt
├── FakeCellDatabase.kt
├── model/CellState.kt
└── alerts/
├── CellAlertManager.kt
└── CellAlertEvent.kt

yaml
Code kopieren

**CellMonitor.kt**
- zentrale Überwachung
- sammelt Zustände
- meldet Verdachtsfälle an Policy Engine

**DetectorHeuristics.kt**
- Downgrade-Detection
- Anomalie-Heuristiken
- Fake-CID/LAC-Erkennung

**CellInfoProvider.kt**
- Schnittstelle zum Android-TelephonyManager
- liefert Live-Zellinfos

**FakeCellDatabase.kt**
- lokale JSON-Liste verdächtiger CIDs/LACs
- später: OTA-Updates über Premium-Management-API

---

## 4. Datenfluss

1. CellInfoProvider liefert Rohdaten (TelephonyManager)
2. DetectorHeuristics analysiert:
   - Netztyp-Wechsel
   - CIDs/LACs
   - Signalstärke
   - bekannte Fake-Zellen
3. CellMonitor wertet heuristische Ergebnisse aus
4. Policy Engine (ANDROID-04) entscheidet:
   - WARN
   - BLOCK (OS-Modus)
5. UI stellt Warnungen dar (Call Screen & Status Screen)

---

## 5. Deliverables

- vollständiges Funkzellen-Monitoring-Modul
- FakeCellDatabase.json (kleine Beispiel-Datenbank)
- Policy-Verknüpfung (Premium/OS)
- UI-Warnungen (Popups oder Banner)
- Unit Tests für Heuristiken
- Integration Tests auf echtem Gerät (Pixel empfohlen)

---

## 6. Tests

### 6.1 Unit Tests
- MCC/MNC-Plausibilität
- Downgrade-Detection
- Fake-CID-Erkennung

### 6.2 Integration Tests
- Live-Überwachung über TelephonyManager
- Simulation von Netztyp-Wechseln (über Debug-Tools)

### 6.3 Security Tests
- Falsche Zellen (z. B. wiederholte gleiche CID)
- 2G-Fallback
- unlogische LAC/CID Werte

---

## 7. Q&A (FAQ)

**F:** Kann ein IMSI-Catcher zuverlässig zu 100% erkannt werden?  
**A:** Nein. Erkennung ist heuristisch. Ziel: Warnen, nicht garantieren.

**F:** Speichern wir Funkdaten?  
**A:** Nein, alles rein transient, keine Bewegungsprofile.

**F:** Was passiert im OS-Modus?  
**A:** Call wird sofort blockiert und Funkprofil ggf. auf „LTE-only“ gesetzt.

---

## 8. Referenzen
- SECURITY_DESIGN.md – Kapitel "Funkzellenangriffe"
- ANDROID-04 Policy Engine
- ANDROID-06 Device Owner Whitelist
- PROJECT_PAPER – Premium/OS Spezifikation
