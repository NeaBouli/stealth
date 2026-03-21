# Session Context — 2026-03-21

## Letzter Stand
- TURN Credentials rotiert ✅ (neue Credentials auf Railway aktiv)
- Firebase API Key gesichert ✅ (google-services.json aus Git entfernt)
- AGP 7.4.2 → 8.7.3 Upgrade ✅ (Gradle 8.14, Kotlin 1.9.22, Java 17)
- WireGuard VPN implementiert ✅ (GoBackend mit Noise_IKpsk2)
- minSdk 23 → 24 ✅
- Rollback: v1.9-turn-rotated

## Offen — heute Abend fortsetzen
1. **S10 anschließen** — war bei letzter Session nicht verbunden
2. **Call-Test mit neuen TURN Credentials** — S7→S10, prüfe ICE_FETCH mit neuen Credentials
3. **WireGuard End-to-End Test** — echte Mullvad Config auf S10 eingeben, VPN-Tunnel prüfen
4. **Play Store Vorbereitung starten** — Screenshots, Beschreibung, Datenschutzerklärung

## Device Serials
- S10 Premium: `RF8N313QMFL`
- S7 Pro: `ce10160adc00152604`
- Tab S4 Free: `ce12182c68644439037e`

## Button Koordinaten S10 (debug build)
- fabAcceptCall: (742, 1807)
- fabDeclineCall: (499, 1807)
- fabEndCall: (539, 1807)
- btnCallContact CHEF: (1280, 855)
- nav_contacts: (405, 1927)
- fabCall Dialer: (539, 1699)

## Button Koordinaten S7 (1440x2560)
- nav_contacts: (540, 2400)
- btnCallContact CHEF: (1280, 855)

## Rollback Tags
```
v1.1-stable
v1.2-tested
v1.3-fcm
v1.4-turn
v1.5-release
v1.7-pre-agp-upgrade
v1.8-vpn
v1.9-turn-rotated  ← aktuell
```

## Lokale Dateien (NICHT im Repo)
- `~/Documents/SecureCall-Release/securecall-release-key.jks`
- `~/Documents/SecureCall-Release/KEYSTORE_INFO.txt`
- `~/Documents/SecureCall-Release/firebase/` (alle Firebase JSONs)
- `client_android/gradle.properties` (Keystore-Passwörter)
- `client_android/app/google-services.json` (Firebase Client Config)
