# SecureCall — Test Plan

## Overview
- **Unit Test Klassen:** 12
- **Unit Test Methoden:** ~76
- **Instrumented Test Klassen:** 3
- **Instrumented Test Methoden:** 7
- **Manuelle Test-Cases:** 10

## Test-Matrix

| Komponente | Typ | Tests | Android? | Datei |
|---|---|---|---|---|
| CallSessionManager | JVM Unit | 10 | Nein | `CallSessionManagerTest.java` |
| NonceManager | JVM Unit | 5 | Nein | `NonceManagerTest.kt` |
| ReplayDetector | JVM Unit | 8 | Nein | `ReplayDetectorTest.kt` |
| HkdfSha256 | JVM Unit | 8 | Nein | `HkdfSha256Test.kt` |
| SessionCipherEngine | JVM Unit | 11 | Nein | `SessionCipherEngineTest.kt` |
| SessionCipherContext | JVM Unit | 3 | Nein | `SessionCipherContextTest.kt` |
| FrameHeaderV1 | JVM Unit | 4 | Nein | `FrameHeaderV1Test.kt` |
| FrameSerializer | JVM Unit | 5 | Nein | `FrameSerializerTest.kt` |
| FrameParser | JVM Unit | 6 | Nein | `FrameParserTest.kt` |
| GhostNetSessionManager | JVM Unit | 8 | Nein | `GhostNetSessionManagerTest.kt` |
| GhostNetWebSocketClient | JVM Unit | 9 | Nein | `GhostNetWebSocketClientTest.java` |
| CoreCrypto Fallback | JVM Unit | 1 | Nein | `CoreCryptoFallbackTest.java` |
| MainActivity | Espresso | 5 | Ja | `MainActivityInstrumentedTest.java` |
| CallActivity | Espresso | 1 | Ja | `CallActivityInstrumentedTest.java` |
| SettingsActivity | Espresso | 1 | Ja | `SettingsActivityInstrumentedTest.java` |
| Manuelle Tests | Manual | 10 | Ja (Gerät) | `docs/MANUAL_TESTS.md` |

## Build & Run

### Unit Tests (JVM)
```bash
cd client_android
export JAVA_HOME=$(/usr/libexec/java_home -v17)
./gradlew testDebugUnitTest
```
Report: `app/build/reports/tests/testDebugUnitTest/index.html`

### Instrumented Tests (Device/Emulator)
```bash
./gradlew connectedDebugAndroidTest
```
Report: `app/build/reports/androidTests/connected/index.html`

### Debug-APK
```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### Manual Tests
```bash
bash tools/test_manual_android.sh
```
Siehe `docs/MANUAL_TESTS.md` für die 10 Test-Cases.

## Prerequisites
- JDK 17
- Android SDK 33
- Gradle Wrapper 7.6.3 / AGP 7.4.2
- Device/Emulator für Instrumented Tests
- `node backend/ghostnet_echo_server.js` für MT-03 und MT-06

## Known Limitations
- **CoreCrypto JNI** nicht verfügbar in JVM Unit Tests → nur Fallback-Pfad getestet
- **GhostMediaRouter** nicht direkt unit-testbar (tiefe Singleton-Abhängigkeiten)
- **AudioCapturePlaceholder** braucht echte Hardware → nur manuell testbar
- **FrameType.fromId()** musste nachträglich hinzugefügt werden (fehlte im Original)

## Singleton-Reset Strategie
Alle Singletons werden zwischen Tests via Reflection zurückgesetzt:
- Java: `INSTANCE = null` via `Field.set(null, null)`
- Kotlin Objects: Interne Felder via `DeclaredField` + `isAccessible`
- `ReplayDetector` hat `reset()` Methode
- `android.util.Log` → `unitTests.returnDefaultValues = true` in Gradle
