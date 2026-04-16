# Google Play Store Upload — v1.0.22

## Voraussetzung: AAB bauen

Die signierte AAB fuer Play Store muss noch gebaut werden:

```bash
cd ~/Desktop/StealthX/securecall/client_android
./gradlew bundleFreeRelease

# AAB Ort nach Build:
# app/build/outputs/bundle/freeRelease/app-free-release.aab
```

Keystore: `/Users/gio/Desktop/stealth/securecall-release-key.jks`

## Upload-Schritte (manuell in Play Console)

1. Oeffne https://play.google.com/console
2. Waehle SecureCall App
3. Release > Production Track (oder Closed Testing)
4. "Create new release"
5. AAB hochladen: `app-free-release.aab`
6. Release Notes einfuegen (siehe unten)
7. "Save" > "Review" > "Start rollout"

## Release Notes — English

```
Security update: REGISTERED-gated connection flow, hardened signaling server (CORS, Stripe idempotency, atomic writes), PII redacted from logs, subscription state auto-resync. Custom Call ID token validation improved. Verified on S10, S7, Tab S4.
```

## Release Notes — Deutsch

```
Sicherheitsupdate: REGISTERED-gesicherte Verbindung, gehaerteter Signaling-Server (CORS, Stripe, atomare Schreibvorgaenge), PII aus Logs entfernt, Abo-Status Resync. Custom Call ID Validierung verbessert. Getestet auf S10, S7, Tab S4.
```

## APK Fingerprint (Verifikation)

SHA-256 Signing Certificate: `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`

Dieser Fingerprint muss mit dem in Play Console hinterlegten uebereinstimmen.

## Checkliste

- [ ] AAB gebaut und signiert
- [ ] AAB in Play Console hochgeladen
- [ ] Release Notes EN + DE eingefuegt
- [ ] Rollout gestartet (100% oder stufenweise)
- [ ] Play Console zeigt "Published" Status
