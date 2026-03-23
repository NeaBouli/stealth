# Session Context — 2026-03-23

## Aktueller Stand — v2.5-release-ready
- TURN Call-Test ✅ (S7→S10, 26s E2E-encrypted, ICE host+srflx)
- WireGuard VPN BUG-006 fixed ✅ (onActivityResult + client address)
- WireGuard Handshake COMPLETE ✅ (Noise_IKpsk2, keepalive bidirektional)
- Call über VPN ✅ (21s E2E während aktivem WireGuard Tunnel)
- WalletConnect v2 ✅ (Sign SDK, Project ID 32f56ab..., Relay connected, Pairing URI)
- AdMob Free Flavor ✅ (Banner + Interstitial, Test-IDs)
- Emergency Broadcast System ✅ (8 Templates, WS+FCM, Activity)
- F-Droid Flavor ✅ (com.securecall.app.fdroid, baut erfolgreich)
- Gift Link System ✅ (Backend /admin/gift Endpoints)
- Donation Screen ✅ (ETH/BTC/SOL in Settings)
- Play Store Listing ✅ (EN + DE in docs/)
- Privacy Policy ✅ (7 Sprachen: EN DE EL IT ES RU ZH)
- Landing Page ✅ (Footer korrigiert, IFR Lock Optionen, Emergency Section)
- Wiki Nav sync ✅ (alle 13 Wiki-Seiten + faq.html + privacy.html)
- Release APKs ✅ (Free/Pro/Premium in ~/Documents/SecureCall-Release/)
- compileSdk 33→35, Kotlin 1.9.22

## Offen
1. **Play Store Screenshots** — auf Geräten machen
2. **Echte AdMob IDs** — Test-IDs vor Release ersetzen
3. **Server Deploy** — /admin/broadcast + /admin/gift nach Railway deployen
4. **WalletConnect Wallet-Test** — MetaMask QR Code scannen
5. **Multilingual Landing Page** — reverted, braucht anderen Ansatz

## Device Serials
- S10 Premium: `RF8N313QMFL`
- S7 Pro: `ce10160adc00152604`
- Tab S4 Free: `ce12182c68644439037e`

## Tags
```
v2.0-launch-ready
v2.2-walletconnect-verified
v2.3-admob
v2.4-broadcast
v2.5-release-ready  ← aktuell
```

## Lokale Dateien (NICHT im Repo)
- `~/Documents/SecureCall-Release/securecall-release-key.jks`
- `~/Documents/SecureCall-Release/app-free-release.apk`
- `~/Documents/SecureCall-Release/app-pro-release.apk`
- `~/Documents/SecureCall-Release/app-premium-release.apk`
- `client_android/app/google-services.json` (Firebase)
- `client_android/gradle.properties` (Keystore-Passwörter)
