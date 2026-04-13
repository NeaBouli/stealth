# stealth — Claude Context

## Was ist das?
StealthX/SecureCall — Verschlüsselte Android P2P-Calling-App mit E2E-Encryption (XChaCha20-Poly1305, X25519, DTLS-SRTP).

## Stack
- **Client**: Android, Kotlin, Java, WebRTC, OkHttp
- **Backend**: Node.js, Express, WebSocket (signaling), ethers.js (Ethereum)
- **Website**: Static HTML/CSS/JS (GitHub Pages: stealthx.tech)
- **Infra**: Railway (backend), GitHub (code), GitLab (F-Droid pipeline)

## Aktuelle Phase
**v1.0.18 (vC39)** — Alpha Testing, AAB ready for Play Store Upload

## START FLOW — Lies diese Dateien zuerst:
1. `docs/SESSION_CONTEXT.md` — Vollständiger Projekt-Kontext + Handover
2. `BACKLOG.md` — Offene Tasks + nächste Schritte
3. `docs/FDROID_SETUP.md` — F-Droid Submission Guide (falls F-Droid relevant)

## Wichtige Dateipfade
- **Client Entry**: `client_android/app/src/main/java/com/securecall/app/MainActivity.java`
- **WS Service**: `client_android/app/src/main/java/com/securecall/app/net/WebSocketService.kt`
- **SIWE Client**: `client_android/app/src/withWalletConnect/.../WalletConnectManager.kt`
- **Backend**: `backend/signaling/src/server.js`
- **Website**: `website/` (GitHub Pages → stealthx.tech)
- **F-Droid Metadata**: `fdroid/metadata/com.securecall.app.fdroid.yml`
- **Build Config**: `client_android/app/build.gradle`

## Build Commands
```bash
cd client_android
./gradlew assembleFreeDebug                    # Free debug APK
./gradlew assembleFreeRelease bundleFreeRelease # Free release APK + AAB
./gradlew assembleFdroidRelease                 # F-Droid APK
./gradlew assemblePremiumDebug -Pinternal       # Premium debug (internal only)
```

## Devices (ADB)
- S10: `RF8N313QMFL` (Premium, hat Sperrbildschirm-Muster)
- S7: `ce10160adc00152604` (Pro)
- Tab S4: `ce12182c68644439037e` (Free)

## Do NOT touch
- .env / .env.local
- node_modules/
- .git/
- backend/signaling/data/*.json (runtime data)
- `securecall-release-key.jks` (signing key)

## Architektur-Entscheidungen
- **WalletConnect/Reown SDK entfernt** — Relay 403 Bug (reown-kotlin #240). Stattdessen SIWE via MetaMask deep link. NICHT wieder einbauen. Siehe `memory/walletconnect_siwe.md`.
- **NEVER** call `/admin/broadcast` from AI/dev session — nur Kaspartizan
- Pro/Premium Gradle Flavors hinter `-Pinternal` Guard — nicht in public Releases

## Offene Tasks → siehe BACKLOG.md
