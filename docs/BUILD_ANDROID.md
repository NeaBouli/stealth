# Android Build Guide – SecureCall Client

Dieser Leitfaden beschreibt alle Schritte, um das Android-Projekt (`client_android/`)
lokal zu bauen, auszuführen und für die weitere Entwicklung vorzubereiten.

---

## 1. Systemvoraussetzungen

### Betriebssystem
- macOS oder Linux empfohlen
- Windows funktioniert mit WSL2

### Java / JDK
- **JDK 17** erforderlich  
(Neuere JDKs funktionieren nicht zuverlässig mit gradle-plugin < 8)

### Android Studio
Erforderlich:
- Android Studio **Hedgehog oder Iguana**
- Gradle Plugin **8.0+**
- Gradle Version **8.x**

### Android SDK Komponenten
In Android Studio installieren:
- SDK 34 (Android 14)
- SDK 33 (Android 13)
- Build-Tools 34.x.x
- Platform Tools
- NDK (nur für CRYPTO-02 benötigt)

---

## 2. Projektstruktur

client_android/
├── app/
│ ├── src/main/java/com/securecall/app/
│ ├── src/main/res/layout/
│ ├── AndroidManifest.xml
│ └── build.gradle
└── build.gradle

yaml
Code kopieren

---

## 3. Projekt bauen

Aus dem Projekt-Root:

cd client_android
./gradlew assembleDebug

yaml
Code kopieren

APK liegt danach unter:

client_android/app/build/outputs/apk/debug/app-debug.apk

yaml
Code kopieren

---

## 4. App installieren (Emulator oder echtes Gerät)

### Emulator:
adb install -r client_android/app/build/outputs/apk/debug/app-debug.apk

markdown
Code kopieren

### Echtes Gerät:
- USB-Debugging aktivieren
- Verbindung prüfen:
adb devices

diff
Code kopieren
- Dann installieren:
adb install -r app-debug.apk

yaml
Code kopieren

---

## 5. Häufige Fehler

### Fehler: „Unsupported Java Version“
→ Falsches JDK.  
Java auf 17 setzen:

macOS:
export JAVA_HOME=$(/usr/libexec/java_home -v17)

yaml
Code kopieren

---

### Fehler: „NDK not installed“
→ Für jetzt irrelevant.  
Wird erst in **CRYPTO-02** benötigt.

---

### Fehler: Gradle Cache kaputt

./gradlew --stop
rm -rf ~/.gradle/caches/
./gradlew build

yaml
Code kopieren

---

## 6. Status

Dieser Guide deckt Phase ANDROID-01 ab.

Spätere Updates:
- JNI/FFI Setup für Rust (CRYPTO-02)
- GhostNet Transport (ANDROID-02)
- Secure Mode Monitor (ANDROID-03)
- VPN Firewall (ANDROID-05)

