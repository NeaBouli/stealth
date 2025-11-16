# <TASK-ID> – <Titel>

## 1. Status
- **Abgeschlossen am:** <Datum>
- **Entwickler:** <Name>
- **Reviewer:** <Name>

## 2. Zusammenfassung
Kurze technische Beschreibung:
- Was wurde umgesetzt?
- Was wurde verändert?
- Warum war der Task notwendig?

## 3. Implementierungsdetails
Liste aller relevanten Änderungen:
- neue Dateien / Klassen / Module
- geänderte APIs
- neue Konfigurationen
- interne Logik
- Interaktion mit anderen Modulen

## 4. Tests
### 4.1 Unit Tests
- welche Tests?
- Ergebnisse?

### 4.2 Integration Tests
- Crypto ↔ Android Bridge?
- Android ↔ Backend?

### 4.3 Security Tests
- Manipulationsversuche?
- Root-/Hooking-Erkennung?
- Timing / side-channel Checks?

### 4.4 Netzwerk Tests
- Verhalten bei Paketverlust
- Jitter
- Multi-Hop (falls relevant)

## 5. Sicherheitsaspekte
- Zeroize angewendet?
- Keine Klartext-Schlüssel im Speicher?
- Kein Debug-Logging sensibler Daten?
- Angriffsszenarien wie MITM geprüft?
- Memory Safety?

## 6. Abhängigkeiten & Kompatibilität
- Abhängigkeiten erfüllt?
- Kompatibilität mit vorherigen Tasks?
- Breaking Changes? (falls ja → dokumentieren!)

## 7. Offene Punkte / Empfehlungen
- mögliche Folge-Tasks
- bekannte Grenzen
- Optimierungspotential

