> **CLASSIFICATION: RESTRICTED** | **DOCUMENT: SC-DEPLOY** | **DIVISION: StealthX // SecureCall**

---

# FIELD DEPLOYMENT GUIDE

---
#### ████ OPTION 1: GOOGLE PLAY STORE (RECOMMENDED) ████
---

The official and recommended way to deploy SecureCall.

| Version | Description | Link |
|---------|-------------|------|
| **SecureCall** (Free) | E2E encrypted calls, 10 contacts, 15 min limit | [Google Play](https://play.google.com/store/apps/details?id=com.securecall.app.free) |
| **SecureCall Pro** | Unlimited calls, HD audio, anti-recording | In-App Upgrade |
| **SecureCall Premium** | Everything + GhostNet IP masking | In-App Upgrade |

---
#### ████ OPTION 2: GITHUB RELEASES (ADVANCED USERS) ████
---

> **Important:** SecureCall uses a Source-Available License. Building from source is not permitted. Download the official APK from GitHub Releases instead.

1. Go to [Releases](https://github.com/NeaBouli/stealth/releases)
2. Download the latest `.apk` file
3. On your Android device: Settings → Security → Enable "Unknown Sources"
4. Open the downloaded APK and deploy
5. Disable "Unknown Sources" after deployment

---
#### ████ MINIMUM ASSET SPECIFICATIONS ████
---

| Requirement | Minimum |
|-------------|---------|
| **Android Version** | Android 10 (API 29) or higher |
| **Free Space** | 50 MB |
| **RAM** | 2 GB |
| **Permissions** | Microphone (required for calls) |
| **Network** | WiFi or mobile data |

---
#### ████ INITIAL FIELD SETUP ████
---

### 1. Grant Permissions

When you first open SecureCall, you'll be asked to grant the **Microphone** permission. This is required for voice calls.

### 2. Key Generation

SecureCall automatically generates your encryption key pair:
- An **X25519 key pair** is created on your device
- The **private key** never leaves your phone
- The **public key** is used to establish encrypted connections

### 3. Get Your SecureCall ID

Your unique, anonymous SecureCall ID is displayed in the app. Share this ID with your contacts through any channel (messaging app, in person, etc.).

### 4. Add Contacts

1. Tap the "+" button
2. Enter your contact's SecureCall ID
3. The contact appears in your list

### 5. Make Your First Call

1. Tap on a contact
2. Tap the call button
3. Wait for connection — the encryption handshake takes 1-2 seconds
4. The lock icon turns green when the call is encrypted

---
#### ████ FAULT DIAGNOSIS ████
---

### App won't deploy
- Check that you have enough storage space (50 MB)
- Ensure "Unknown Sources" is enabled (for APK deployments)
- Try restarting your device

### Microphone permission denied
- Go to Settings → Apps → SecureCall → Permissions → Microphone → Allow

### Can't connect to server
- Check your internet connection (WiFi or mobile data)
- Try switching between WiFi and mobile data
- The signaling server may be temporarily down — try again in a few minutes

### No audio during call
- Check that the microphone permission is granted
- Ensure your volume is turned up
- Try using a headset
- Check if another app is using the microphone

---

> DOCUMENT END // CLASSIFICATION: RESTRICTED

[← Return to Operations Center](Home.md)
