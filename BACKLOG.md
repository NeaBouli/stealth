# stealth — Backlog

## 🔴 Aktiv (diese Session)
<!-- Was gerade in Arbeit ist -->

## 🟡 Nächste Session
<!-- Nächste konkrete Tasks -->

## 🟢 Irgendwann / Ideas
<!-- Ideen ohne Deadline -->

## ✅ Erledigt
<!-- Letzte 7 Tage -->
- **TODO-065** FLAG_SECURE konsistent — DONE (2026-04-07)
  - `WindowSecurityHelper` als zentrale Logik (FREE=off, PREMIUM=on, PRO=toggle)
  - Fehlte auf: QrCodeActivity, OnboardingActivity, EmergencyBroadcastActivity
  - IncomingCallActivity respektierte Pro-Toggle nicht (gefixt)
  - Screenshot-Test: S7=12B, S10=0B (beide blockiert)
- **TODO-066** FCM Push Notifications geprüft — DONE (2026-04-07)
  - Server sendet FCM push korrekt (`pushSent: true`)
  - Device empfängt GCM intent (`c2dm.intent.RECEIVE`)
  - WebSocket-Pfad: IncomingCallActivity + Notifications funktionieren
  - `am force-stop` blockiert FCM (Android-Design, kein reales Szenario)
- **TODO-074** F-Droid APK Build — DONE (2026-04-07)
  - `assembleFdroidRelease` BUILD SUCCESSFUL
  - APK: `releases/app-fdroid-release-v31.apk` (77 MB)
  - Package: `com.securecall.app.fdroid`
  - Kein Google Services, kein FCM, kein AdMob, kein Crashlytics

---
*Zuletzt aktualisiert: 2026-04-07*
