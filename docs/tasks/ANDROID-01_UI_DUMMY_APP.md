# ANDROID-01 – UI Dummy App & Projektgrundlage

## Ziel
Ein lauffähiges Android-Projekt mit Grundstruktur, Navigationsgraph und drei Dummy-Screens:
- Home
- Call
- Settings

Dies bildet die Basis für alle späteren Features.

---

## Erwartetes Ergebnis

### 1. Projektstruktur (`client_android/`)
- Gradle-Projekt erzeugen
- Standardpaket: `com.securecall.app`
- Screens:
  - `HomeFragment`
  - `CallFragment`
  - `SettingsFragment`
- Navigation über Jetpack Navigation
- Minimaler Theme/Style

### 2. Manifest
- Permissions:
  - `android.permission.INTERNET`
  - `android.permission.RECORD_AUDIO`

### 3. UI-Anforderungen
- einfache Layouts (Buttons/Text)
- kein finales Design
- nur Navigation vorhanden

---

## Tests
- App startet ohne Fehler
- Navigieren zwischen Screens funktioniert
- Build läuft ohne Warnungen oder Abstürze

---

## Developer FAQ

**Frage:** Muss schon Audio verarbeitet werden?  
Antwort: Nein, das kommt erst in ANDROID-02.

**Frage:** Müssen wir Material Design nutzen?  
Antwort: Optional – ein neutrales Layout reicht.

**Frage:** Müssen wir Kotlin oder Java verwenden?  
Antwort: Kotlin ist Standard für alle weiteren Teile.

