# TASK: ANDROID-03 – Secure Mode Checks (Free-Version)

## 1. Ziel des Tasks
Der Security Monitor der Free-Version erfasst grundlegende Sicherheitszustände
und zeigt Warnungen im UI an. Die Free-Version darf **nicht blockieren**, sondern nur warnen.

---

## 2. Anforderungen

### 2.1 Neue Module

client_android/
└── app/src/main/java/com/securecall/app/security/
├── SecurityMonitor.kt
├── SecurityStatus.kt
├── ScreenRecordingDetector.kt
├── DeveloperOptionsCheck.kt
├── NetworkCheck.kt
└── RootCheck.kt (nur einfache Erkennung, kein Blockieren)

yaml
Code kopieren

### 2.2 SecurityMonitor – Verantwortung
- sammelt Zustände (Recording, WLAN, DeveloperOptions, Root)
- liefert konsolidierten Status
- triggert UI-Warnungen (gelb/rot)
- **keine Blockierung** in Free-Version

---

## 3. Checks (MVP)

### 3.1 Screen Recording Detection
- MediaProjectionManager
- Flag: `isScreenRecordingActive = true/false`
- Warnung im UI bei Call-Bildschirm

### 3.2 Developer Options Detection
Über Settings.Global:

- `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`
- Warnung: „Developer Options active – Reduced Security“

### 3.3 WLAN / Netzwerkprüfung
- Wenn Nutzer nicht über mobile Daten in einer unbekannten WLAN-Umgebung telefoniert  
→ UI-Warnung „Untrusted Network“

### 3.4 Root Detection (leicht)
Nur heuristisch:
- Test: Existenz von `/su/bin` oder `/system/xbin/su`
- Kein Blockieren

---

## 4. UI-Integration

### 4.1 Anzeigeelemente
Auf dem Call-Screen:

- 🔴 Rot = Kritische Gefahr (Recording aktiv)
- 🟡 Gelb = Reduzierte Sicherheit (Developer Options / Root / WLAN)
- 🟢 Grün = Alles ok

### 4.2 Verhalten
- Warnungen müssen sofort sichtbar sein
- Free-Version darf **nichts automatisch abbrechen**

---

## 5. Deliverables

- SecurityMonitor.kt mit zentraler Logik
- alle Checks implementiert
- Call-Screen zeigt Sicherheitsstatus
- Logging ohne sensitive Daten
- Kommentarblock „ANDROID-03“

---

## 6. Tests

### 6.1 Gerätetests
- Screen Recording ein/aus → Status aktualisiert korrekt
- Developer Options togglen → UI zeigt gelb
- Rooted Emulator → gelb
- WLAN testen → gelb/rot je nach Config

### 6.2 Security-Tests
- Keine Logs mit sensiblen Daten
- Keine Speicherung der Zustände
- UI reagiert innerhalb von < 200 ms

---

## 7. Q&A

**F:** Muss die Free-Version bereits blockieren?  
**A:** Nein, nur Premium/OS blockieren. Free zeigt Warnungen.

**F:** Muss Root präzise erkannt werden?  
**A:** Nein, nur einfache Heuristiken. Später in Pro/Premium wird es aggressiver.

**F:** Soll der Nutzer gewarnt werden, wenn er während eines Calls zu WLAN wechselt?  
**A:** Ja, der Statusindikator muss sofort wechseln.

---

## 8. Referenzen
- ANDROID-01
- ANDROID-02
- docs/SECURITY_DESIGN.md
