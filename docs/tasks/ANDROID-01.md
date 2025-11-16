# TASK: ANDROID-01 – UI Dummy App & Projektgrundlage

## 1. Ziel des Tasks
Erstellung der Android-Grundstruktur für den SecureCall Client:

- Projekt-Setup (Gradle, Modules, App-Level)
- Basis-Screens (Home, Call, Settings)
- Navigationsstruktur
- Dummy-UI ohne echte Funktionalität
- Minimaler technischer Rahmen für spätere Crypto-, Transport- und Security-Integrationen

Dies ist der Ausgangspunkt aller Android-Entwicklung.

---

## 2. Anforderungen

### 2.1 Projektstruktur
Das Projekt im Ordner `client_android/` soll enthalten:

client_android/
├── app/
│ ├── src/main/
│ │ ├── java/com/securecall/app/
│ │ │ ├── ui/
│ │ │ │ ├── HomeScreen.kt
│ │ │ │ ├── CallScreen.kt
│ │ │ │ └── SettingsScreen.kt
│ │ │ └── MainActivity.kt
│ │ ├── res/layout/
│ │ ├── AndroidManifest.xml
│ └── build.gradle
└── build.gradle

markdown
Code kopieren

### 2.2 Build-System
- Verwendung von **Gradle (KTS)** empfohlen
- Min SDK: **26**
- Target SDK: **34**
- Use Jetpack Compose **optional**, XML ist OK

### 2.3 Dummy-Screens
**Home Screen**
- Buttons: „Call“, „Settings“
- Platzhalter für Identity

**Call Screen**
- Timer (Dummy)
- Button „Start Call“ / „End Call“
- Statusbereich (grün/gelb/rot Platzhalter)

**Settings Screen**
- Toggle für Secure Mode (noch ohne Funktion)
- Platzhalter für Debug-Status

### 2.4 Berechtigungen
Manifest muss enthalten:

<uses-permission android:name="android.permission.INTERNET"/> <uses-permission android:name="android.permission.RECORD_AUDIO"/> ```
2.5 Navigationslogik
MainActivity hostet Navigation

Navigation zwischen den drei Screens

Keine Business-Logik

3. Deliverables
Lauffähiges Android-Projekt unter client_android/

Kompilierbare Dummy-App

Screens vorhanden und navigierbar

Minimaler UI-Flow steht

4. Testkriterien
4.1 Build & Run
App baut ohne Fehler

App startet ohne Abstürze

Navigation zwischen Screens funktioniert

4.2 Architektur
Modular, klare Paketstruktur

Keine Business-Logik (bewusst minimal!)

5. Q&A
F: Soll schon Jetpack Compose verwendet werden?
A: Optional. Klassisches XML ist völlig ausreichend für ANDROID-01.

F: Muss der Call-Screen schon Audio abspielen?
A: Nein, das kommt in ANDROID-02. Jetzt nur UI.

F: Sollen wir schon Dependency Injection (Hilt/Koin) benutzen?
A: Nein — sauberer späterer Task. ANDROID-01 bleibt minimal.

6. Referenzen
docs/ARCHITECTURE_OVERVIEW.md

docs/DEV_ROADMAP.md

PROJECT_MASTER_PLAN.json

