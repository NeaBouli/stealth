# TASK: ANDROID-04 – Policy Engine Implementierung (Pro-Version)

## 1. Ziel des Tasks
Die Policy Engine führt alle Sicherheitsregeln zentral aus und liefert eindeutige
Entscheidungen an den Security Monitor: ALLOW, WARN oder BLOCK.

Sie ist modular, austauschbar und pro Produktlinie konfigurierbar.

---

## 2. Architekturziel

Die Policy Engine soll:

- die erkannte Gerätesituation analysieren (Root, Recording, Netzwerk, Emulator, Debugging),
- die Richtlinien der jeweiligen Produktlinie laden (Free/Pro/Premium/OS),
- eine konsolidierte Entscheidung zurückgeben,
- keine UI- oder OS-spezifische Logik enthalten (reines Backend-Modul).

---

## 3. Modulstruktur

client_android/
└── app/src/main/java/com/securecall/app/policy/
├── PolicyEngine.kt
├── PolicyProfile.kt
├── PolicyDecision.kt
├── profiles/
│ ├── FreePolicy.kt
│ ├── ProPolicy.kt
│ ├── PremiumPolicy.kt
│ └── OSPolicy.kt
└── config/
└── policy_profiles.json (optional)

yaml
Code kopieren

---

## 4. Entscheidungslogik (Decision Model)

### 4.1 Entscheidungsräume

Jedes Risiko führt zu einer Policy-Regel:

| Risiko                 | Free | Pro  | Premium | OS   |
|------------------------|------|------|---------|------|
| Screen Recording       | WARN | BLOCK | BLOCK  | BLOCK |
| Root/Magisk            | WARN | WARN/BLOCK | BLOCK | BLOCK |
| Emulator               | WARN | BLOCK | BLOCK | BLOCK |
| Developer Options      | WARN | WARN | BLOCK | BLOCK |
| Unsicheres WLAN        | WARN | WARN | WARN/BLOCK | BLOCK |

### 4.2 Entscheidungstypen

enum PolicyDecision {
ALLOW,
WARN,
BLOCK
}

yaml
Code kopieren

---

## 5. Logikablauf

1. Security Monitor sammelt Fakten → `SecurityStatus`
2. PolicyEngine erhält den Status + aktives Profil
3. PolicyEngine berechnet
   - höchste Risikostufe
   - liefert eine `PolicyDecision`
4. SecurityMonitor setzt es um (UI-Warnung / Call blockieren)

---

## 6. Beispiel (Pro-Version)

**Fakten:**
- Recording: false  
- Root: true  
- Emulator: false  
- Developer Options: false  
- Network: trusted  

**Regel-Profil Pro:**  
- Root = WARN (default), optionaler Blockmodus aktivierbar

**Ergebnis:**  
`PolicyDecision.WARN`

---

## 7. Deliverables

- PolicyEngine.kt mit zentraler Entscheidungsmatrix
- Profile für Free, Pro, Premium, OS
- Optionale JSON-Konfiguration `policy_profiles.json`
- Verbindung zum Security Monitor integriert
- Unit Tests für jede Kombination (mind. 20 Testfälle)
- Dokumentation im Kommentarblock „ANDROID-04“

---

## 8. Testplan

### 8.1 Unit Tests
- Simulierte Status-Objekte mit diversen Risikokombinationen
- Verifizierung der Entscheidungen
- Testfälle für jede Produktlinie

### 8.2 Integration Tests
- SecurityMonitor → PolicyEngine → UI

### 8.3 Regression Tests
- sicherstellen, dass Free niemals BLOCK auslöst
- Premium/OS dürfen niemals ALLOW auslösen, wenn Risiko > 0

---

## 9. Q&A (FAQ)

**F:** Soll die Policy Engine offline funktionieren?  
**A:** Ja, komplett offline. Nur Premium/OS kann Updates über Management-API erhalten.

**F:** Sollen Profile in Code oder JSON definiert werden?  
**A:** Pro-Version kann beides unterstützen. JSON ist empfohlen für spätere Remote-Policies.

**F:** Gibt es ein Standardprofil?  
**A:** Wenn nichts definiert ist → Free.

---

## 10. Referenzen
- ANDROID-03 (Security Monitor Integration)
- SECURITY_DESIGN.md
- ARCHITECTURE_OVERVIEW.md
