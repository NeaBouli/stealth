# Matrix Integration — Architecture & Developer Specification

**Wiki Page:** `docs/WIKI/Matrix-Integration.md`  
**Status:** Draft — Pending Review  
**Author:** Vendetta Labs  
**Last Updated:** 2026-03-28  
**Classification:** Internal Developer Document

---

## Table of Contents

1. [Vision & Strategic Context](#1-vision--strategic-context)
2. [Integration Overview](#2-integration-overview)
3. [Option C — Matrix as Identity Layer (TODO / Near-term)](#3-option-c--matrix-as-identity-layer)
4. [Option A — Matrix as Signaling Backend (Roadmap / Long-term)](#4-option-a--matrix-as-signaling-backend)
5. [Security Considerations](#5-security-considerations)
6. [Roadmap Entries](#6-roadmap-entries)
7. [Glossary](#7-glossary)

---

## 1. Vision & Strategic Context

### 1.1 Why Matrix?

SecureCall is currently a closed communication silo. Users can only call other SecureCall users. This is by design for the initial phase — it keeps the attack surface small and the product focus clear. However, for long-term adoption, this is also a limitation.

[Matrix](https://matrix.org) is an open, federated communication protocol with over 28 million registered accounts across thousands of independently operated servers (called "homeservers"). It is the infrastructure backbone for encrypted communication used by:

- The **German Bundeswehr** (BwMessenger — classified document sharing)
- The **German national healthcare system** (Ti-Messenger)
- **Mozilla** (replaced their entire IRC infrastructure)
- **KDE, Red Hat, Uber, Samsung** and many others

Matrix is not a product — it is a **protocol standard** (comparable to SMTP for email, or HTTP for the web). Any application that speaks the Matrix protocol can communicate with any other Matrix-compatible application across any server, without a central authority.

### 1.2 Why this matters for SecureCall

SecureCall already uses the technical primitives that Matrix is built on:

| SecureCall | Matrix |
|---|---|
| WebRTC + DTLS-SRTP (call transport) | WebRTC + DTLS-SRTP (call transport) |
| Double Ratchet (forward secrecy) | Megolm / Olm (Double Ratchet variant) |
| X25519 key exchange | Curve25519 / X25519 |
| Node.js signaling server (SDP relay) | `m.call.*` events (SDP relay over rooms) |
| Anonymous SecureCall ID | `@user:homeserver` Matrix ID |
| Zero-knowledge server | Zero-knowledge federation |

The architectural overlap is not coincidental — both systems converge on the same cryptographic best practices. The delta between SecureCall and Matrix is primarily in the **signaling layer** and the **identity layer**, not in the cryptographic core.

### 1.3 Integration Philosophy

This document defines two distinct integration paths:

**Option C** (near-term, low complexity): Use Matrix as an out-of-band identity exchange channel. No changes to the server or crypto stack. SecureCall users can share their anonymous SecureCall ID through a Matrix Direct Message, removing the friction of manual ID exchange. This is a UX improvement, not an architectural change.

**Option A** (long-term, high complexity): Replace SecureCall's Node.js signaling server with a Matrix homeserver as the signaling backbone. SecureCall users receive permanent Matrix IDs (`@alice:stealthx.tech`). Any Matrix user on any server can initiate an encrypted call to a SecureCall user. This opens SecureCall to 28+ million potential users without any change to the cryptographic core.

> **Important constraint:** Neither option touches XChaCha20-Poly1305, the Rust crypto engine, or Double Ratchet. The crypto layer is non-negotiable and stays exactly as it is. Network traffic follows Android's active connection, including an optional external device VPN.

---

## 2. Integration Overview

### 2.1 What Matrix does in a call

Matrix's role in a voice call is purely **signaling** — it never handles audio. In a WebRTC call, two parties need to exchange three things before audio can flow:

1. **SDP Offer/Answer** — a description of each party's audio capabilities (codecs, encryption parameters)
2. **ICE Candidates** — network addresses that the peers can try to connect through (direct IP, STUN-reflexive, TURN relay)
3. **Call state events** — invite, answer, hangup, hold

Matrix standardizes these exchanges as JSON events sent through a Matrix room or as `to_device` messages. The Matrix spec for this is [MSC2746](https://github.com/matrix-org/matrix-spec-proposals/blob/main/proposals/2746-voice-calling.md) (stable) and [MSC3401](https://github.com/matrix-org/matrix-spec-proposals/blob/main/proposals/3401-group-voice-calls.md) (group calls, still maturing).

Once signaling is complete and WebRTC peers have connected, **Matrix drops out entirely**. The audio stream is encrypted end-to-end (DTLS-SRTP at transport level, XChaCha20-Poly1305 at application level via SecureCall's Rust core) and flows directly between clients — or via TURN relay if direct P2P is not possible.

### 2.2 What SecureCall's Node.js server does today

The current Node.js signaling server in `backend/` performs the following functions:

```
Client A connects via WebSocket
  → sends: { type: "offer", sdp: "...", to: "SECURECALL_ID_B", pubkey: "X25519_PUBKEY" }

Server relays to Client B:
  → sends: { type: "offer", sdp: "...", from: "SECURECALL_ID_A", pubkey: "X25519_PUBKEY" }

Client B responds:
  → sends: { type: "answer", sdp: "...", to: "SECURECALL_ID_A" }

Server relays to Client A, then ICE candidates are exchanged the same way.
```

This is structurally identical to what Matrix `m.call.invite` / `m.call.answer` / `m.call.candidates` do, with the difference that Matrix events are JSON objects sent to a Matrix room or via `to_device` API, rather than raw WebSocket messages.

The signaling server **never decrypts or processes** the SDP payload — it is an opaque relay. This is the key insight that makes Option A feasible: Matrix can replace this relay without touching any crypto.

---

## 3. Option C — Matrix as Identity Layer

### 3.1 Problem statement

Currently, two SecureCall users who want to call each other must exchange their SecureCall ID through some external channel (SMS, in-person, another chat app). This is friction — especially for privacy-conscious users who don't want to use insecure channels just to bootstrap a secure call.

Matrix provides a large, existing, privacy-respecting network of users who are already comfortable with encrypted communications. Option C makes Matrix that bootstrapping channel, officially.

### 3.2 What this implements

A user in SecureCall can tap **"Share ID via Matrix"** (or scan a QR code from a Matrix message) to exchange their SecureCall ID with a contact. After this one-time exchange, all calls happen natively through SecureCall — Matrix is only involved in the initial contact exchange.

No Matrix server ever learns that a call happened. No Matrix server sees audio. Matrix sees only: "User A sent User B a text message containing a SecureCall ID."

### 3.3 Implementation Specification

#### 3.3.1 Share Intent (Android → Matrix)

The simplest implementation is a standard Android Share Intent. No Matrix SDK needs to be integrated into SecureCall.

```kotlin
// In ContactsFragment.kt or similar
fun shareSecureCallIdViaMatrix(context: Context, secureCallId: String) {
    val shareText = buildSharePayload(secureCallId)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        // Optionally restrict to known Matrix clients
        // putExtra(Intent.EXTRA_SUBJECT, "SecureCall Contact")
    }
    context.startActivity(Intent.createChooser(intent, "Share SecureCall ID"))
}

private fun buildSharePayload(id: String): String {
    return """
        SecureCall Contact:
        
        ID: $id
        
        To call me, download SecureCall:
        https://stealthx.tech
        
        Or open directly: securecall://contact/$id
    """.trimIndent()
}
```

The `securecall://contact/<id>` deep link (see section 3.3.3) allows the recipient to tap the link in their Matrix client and have SecureCall open with the contact pre-filled.

#### 3.3.2 Receive via Deep Link (Matrix → SecureCall)

When a user receives a `securecall://contact/<id>` link in any Matrix client (Element, FluffyChat, etc.) and taps it, Android routes it to SecureCall via the deep link handler.

**AndroidManifest.xml** — add intent filter to the main Activity:

```xml
<activity android:name=".MainActivity">
    <!-- ... existing filters ... -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="securecall" android:host="contact" />
    </intent-filter>
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <!-- HTTPS fallback for web-based Matrix clients -->
        <data
            android:scheme="https"
            android:host="stealthx.tech"
            android:pathPrefix="/add/" />
    </intent-filter>
</activity>
```

**Deep link handler in MainActivity.kt:**

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleDeepLink(intent)
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleDeepLink(intent)
}

private fun handleDeepLink(intent: Intent) {
    val uri = intent.data ?: return
    
    when {
        // securecall://contact/<id>
        uri.scheme == "securecall" && uri.host == "contact" -> {
            val contactId = uri.lastPathSegment ?: return
            handleAddContact(contactId)
        }
        // https://stealthx.tech/add/<id>
        uri.scheme == "https" && uri.host == "stealthx.tech" 
            && uri.path?.startsWith("/add/") == true -> {
            val contactId = uri.lastPathSegment ?: return
            handleAddContact(contactId)
        }
    }
}

private fun handleAddContact(id: String) {
    if (!isValidSecureCallId(id)) {
        showError("Invalid SecureCall ID")
        return
    }
    // Navigate to Add Contact screen with ID pre-filled
    navController.navigate(
        R.id.addContactFragment,
        bundleOf("prefilled_id" to id)
    )
}

private fun isValidSecureCallId(id: String): Boolean {
    // SecureCall IDs are base64url-encoded X25519 public keys (43 chars)
    return id.matches(Regex("^[A-Za-z0-9_-]{43}$"))
}
```

> **Security note:** Always validate the ID format before processing. A malformed deep link should show an error, not crash or silently fail. The validation regex above matches 32-byte X25519 public keys encoded as base64url (43 characters without padding).

#### 3.3.3 QR Code Generation

QR codes are a natural complement to the deep link scheme. The QR code encodes the same `securecall://contact/<id>` URI.

```kotlin
// In ProfileFragment.kt or similar
// Uses: implementation("com.google.zxing:core:3.5.2")

fun generateContactQrCode(secureCallId: String, sizePx: Int = 512): Bitmap {
    val uri = "securecall://contact/$secureCallId"
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2
    )
    val bitMatrix = MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    return BarcodeEncoder().createBitmap(bitMatrix)
}
```

The QR code can be:
- Displayed in the app's profile screen for other users to scan
- Exported as an image and shared via Matrix (or any other channel)
- Printed on physical material (business cards, op cards)

#### 3.3.4 Scan incoming QR (from Matrix image)

When a user receives a screenshot of a SecureCall QR code in a Matrix chat and wants to scan it:

```kotlin
// Launch camera-based QR scanner
fun launchQrScanner(activity: Activity) {
    val options = ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt("Scan a SecureCall QR code")
        setCameraId(0)
        setBeepEnabled(false)
        setBarcodeImageEnabled(false)
    }
    barcodeLauncher.launch(options)
}

// Handle scan result
val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
    if (result.contents != null) {
        val uri = Uri.parse(result.contents)
        if (uri.scheme == "securecall" && uri.host == "contact") {
            val contactId = uri.lastPathSegment
            handleAddContact(contactId ?: return@registerForActivityResult)
        }
    }
}
```

#### 3.3.5 Backend changes

**None required.** The Node.js signaling server is completely unaffected by Option C. This is a purely client-side Android feature.

#### 3.3.6 Files to modify

| File | Change |
|---|---|
| `client_android/app/src/main/AndroidManifest.xml` | Add intent filters for `securecall://contact/*` and `https://stealthx.tech/add/*` |
| `client_android/app/src/main/java/…/MainActivity.kt` | Add `handleDeepLink()` and `handleAddContact()` |
| `client_android/app/src/main/java/…/ProfileFragment.kt` | Add "Share via Matrix / Share ID" button, QR code display |
| `client_android/app/src/main/java/…/AddContactFragment.kt` | Support `prefilled_id` argument from deep link |
| `build.gradle (app)` | Add ZXing dependency if not already present |
| `website/` | Add `https://stealthx.tech/add/<id>` redirect handler (simple server-side redirect to deep link) |

#### 3.3.7 Estimated effort

| Task | Estimate |
|---|---|
| AndroidManifest intent filters | 1 hour |
| Deep link handler in MainActivity | 2 hours |
| QR code generation in ProfileFragment | 3 hours |
| QR code scanner integration | 2 hours |
| Share Intent button in UI | 2 hours |
| Website redirect (`/add/<id>`) | 1 hour |
| Testing on 3 devices | 3 hours |
| **Total** | **~14 hours** |

#### 3.3.8 Roadmap entry (Option C)

```
[ ] FEAT: Matrix contact exchange (Option C)
    Priority: Medium
    Milestone: v1.7
    Labels: ux, matrix, contacts
    
    - Add "Share ID via Matrix" intent in ProfileFragment
    - Implement securecall://contact/<id> deep link handler
    - Add QR code generation for SecureCall ID
    - Add QR code scanner for incoming contact QR codes
    - Add https://stealthx.tech/add/<id> web redirect
    - No backend changes required
    
    Acceptance criteria:
    - User can share their ID from SecureCall to any Matrix client
    - User can tap a securecall:// link from Element/FluffyChat and
      have the contact pre-filled in SecureCall
    - QR code scan works bidirectionally
    - All validation passes (ID format, error states)
```

---

## 4. Option A — Matrix as Signaling Backend

### 4.1 Problem statement and strategic rationale

SecureCall's signaling server is a single-server, proprietary WebSocket relay. It:

- Creates a central point of dependency (if `stealthx.tech` goes down, calling is impossible)
- Limits the addressable user base to SecureCall users only
- Requires users to exchange IDs through out-of-band channels before the first call
- Does not benefit from the Matrix ecosystem's existing identity graph

Option A replaces this server with a Matrix homeserver (Synapse or Dendrite). The result:

- SecureCall users get a permanent, globally unique Matrix ID: `@alice:stealthx.tech`
- Any of the 28+ million Matrix users can call a SecureCall user directly, using Element or any other Matrix-compatible VoIP client
- SecureCall users can call each other and any Matrix user
- Federation means no single server is a point of failure
- The signaling infrastructure is maintained by the Matrix Foundation and community — not by Vendetta Labs alone
- **Nothing changes in the application security layer**: XChaCha20-Poly1305, Rust JNI engine, X25519, Double Ratchet, and anti-recording controls remain unchanged; network traffic continues to use Android's active connection

### 4.2 Matrix VoIP specification

#### 4.2.1 Relevant Matrix Spec Changes

| MSC | Status | Purpose |
|---|---|---|
| MSC2746 | Stable (in spec) | 1:1 voice/video call signaling |
| MSC3401 | Experimental | Group calls (future use) |
| MSC2747 | Merged | DTMF support (optional) |

For Phase 1 of Option A, only MSC2746 (1:1 calls) needs to be implemented. MSC3401 can be deferred to a future "Group Calls" feature.

#### 4.2.2 Matrix call event sequence (MSC2746)

The complete signaling sequence for a 1:1 Matrix VoIP call:

```
Alice                    Matrix HS-A              Matrix HS-B                Bob
  |                          |                        |                       |
  |-- m.call.invite -------->|                        |                       |
  |   { call_id, offer,      |-- federation sync ---->|                       |
  |     version, lifetime }  |                        |-- m.call.invite ----->|
  |                          |                        |                       |
  |                          |                        |<-- m.call.answer -----|
  |<-- m.call.answer --------|<-- federation sync ----|   { call_id, answer } |
  |   { call_id, answer }    |                        |                       |
  |                          |                        |                       |
  |-- m.call.candidates ---->|-- federation sync ---->|-- m.call.candidates ->|
  |<- m.call.candidates -----|<-- federation sync ----|<- m.call.candidates --|
  |                          |                        |                       |
  |====== WebRTC P2P (or TURN relay) audio stream ============================|
  |       [Matrix is no longer involved after this point]                     |
  |                          |                        |                       |
  |-- m.call.hangup -------->|                        |                       |
```

**Key event schemas:**

`m.call.invite`:
```json
{
  "type": "m.call.invite",
  "content": {
    "call_id": "12345678-1234-1234-1234-123456789012",
    "lifetime": 60000,
    "version": 1,
    "offer": {
      "type": "offer",
      "sdp": "v=0\r\no=- ... [full SDP] ..."
    }
  }
}
```

`m.call.answer`:
```json
{
  "type": "m.call.answer",
  "content": {
    "call_id": "12345678-1234-1234-1234-123456789012",
    "version": 1,
    "answer": {
      "type": "answer",
      "sdp": "v=0\r\no=- ... [full SDP] ..."
    }
  }
}
```

`m.call.candidates`:
```json
{
  "type": "m.call.candidates",
  "content": {
    "call_id": "12345678-1234-1234-1234-123456789012",
    "version": 1,
    "candidates": [
      {
        "sdpMid": "audio",
        "sdpMLineIndex": 0,
        "candidate": "candidate:... [ICE candidate string] ..."
      }
    ]
  }
}
```

`m.call.hangup`:
```json
{
  "type": "m.call.hangup",
  "content": {
    "call_id": "12345678-1234-1234-1234-123456789012",
    "version": 1,
    "reason": "user_hangup"
  }
}
```

### 4.3 Architecture Design

#### 4.3.1 Component overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    stealthx.tech Domain                          │
│                                                                  │
│  ┌──────────────────────┐    ┌────────────────────────────────┐  │
│  │   Matrix Homeserver  │    │   SecureCall Backend Services  │  │
│  │   (Synapse/Dendrite) │    │                                │  │
│  │                      │    │  ┌──────────────────────────┐  │  │
│  │  _matrix._tcp SRV    │    │  │  TURN/STUN Server        │  │  │
│  │  DNS delegation      │    │  │  (coturn)                │  │  │
│  │                      │    │  └──────────────────────────┘  │  │
│  │  Handles:            │    │                                │  │
│  │  - User registration │    │  ┌──────────────────────────┐  │  │
│  │  - m.call.* events   │    │  │  IFR Wallet Verification │  │  │
│  │  - Federation        │    │  │  Railway backend         │  │  │
│  │  - to_device msgs    │    │  └──────────────────────────┘  │  │
│  └──────────────────────┘    │                                │  │
│                              │  ┌──────────────────────────┐  │  │
│                              │  │  FCM Push Notification   │  │  │
│                              │  │  Gateway                 │  │  │
│                              │  └──────────────────────────┘  │  │
│                              └────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

Android Client (SecureCall App)
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ┌────────────────────┐   ┌────────────────────────────────────┐ │
│  │  Matrix SDK        │   │  Existing SecureCall Modules       │ │
│  │  (matrix-android   │   │                                    │ │
│  │   -sdk or custom   │   │  core_crypto (Rust JNI)            │ │
│  │   HTTP client)     │   │  XChaCha20-Poly1305                │ │
│  │                    │   │  X25519 key exchange               │ │
│  │  Handles:          │   │  Double Ratchet                    │ │
│  │  - Login / sync    │   │  Anti-Recording                    │ │
│  │  - Sending events  │   │  STEALTH-DELETE                    │ │
│  │  - Receiving       │   │  External VPN compatibility        │ │
│  │    to_device msgs  │   │  Emergency Broadcast               │ │
│  │  - Push rules      │   │                                    │ │
│  └────────────────────┘   └────────────────────────────────────┘ │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  WebRTC Engine (existing) + XChaCha20 application layer     │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

#### 4.3.2 Where XChaCha20 fits in the WebRTC stack

This is the most critical architectural point to understand. WebRTC already provides DTLS-SRTP as its transport-layer encryption. SecureCall adds an application-layer encryption pass using XChaCha20-Poly1305 on top of DTLS-SRTP. This is intentional — defense in depth.

```
Audio Frame (raw PCM/Opus)
        │
        ▼
┌───────────────────────────────┐
│  XChaCha20-Poly1305 encrypt   │  ← SecureCall Rust core (JNI)
│  key = session key from       │    key never leaves device
│        Double Ratchet         │    nonce = per-frame counter
└───────────────────────────────┘
        │
        ▼  encrypted audio frame (opaque blob)
┌───────────────────────────────┐
│  WebRTC audio pipeline        │  ← standard Android WebRTC lib
│  (packetization, jitter buf.) │
└───────────────────────────────┘
        │
        ▼  RTP packet
┌───────────────────────────────┐
│  DTLS-SRTP transport encrypt  │  ← WebRTC's own encryption
│  (TLS 1.3 over DTLS)          │    standard compliance layer
└───────────────────────────────┘
        │
        ▼  encrypted UDP packet
   Network / TURN relay

Matrix Homeserver sees: NOTHING of the above.
Matrix role ends when WebRTC ICE completes (before any audio flows).
```

When Option A is implemented, this stack is **completely unchanged**. Matrix only replaces the mechanism by which the SDP offer/answer and ICE candidates are exchanged before this stack activates.

#### 4.3.3 Homeserver selection: Synapse vs. Dendrite

| | Synapse | Dendrite |
|---|---|---|
| Language | Python | Go |
| Maturity | Production-proven (2014) | Maturing (reached v1.0 2023) |
| Federation | Full, battle-tested | Full, lighter weight |
| Resource usage | Higher (PostgreSQL + Python) | Lower (Go, same DB) |
| MSC2746 support | Full | Full |
| Recommended for | Large deployments | Small-medium deployments |
| **Recommendation** | **Use for production** | **Use for development / staging** |

For a Vendetta Labs deployment serving SecureCall users, **Synapse with PostgreSQL** is recommended for production. Dendrite is appropriate for development and CI/CD environments.

#### 4.3.4 DNS and federation configuration

For SecureCall users to have Matrix IDs of the form `@user:stealthx.tech`, the following DNS records are required:

```dns
; Matrix federation SRV record
_matrix._tcp.stealthx.tech.  3600  IN  SRV  10 5 8448 matrix.stealthx.tech.

; Matrix homeserver CNAME/A
matrix.stealthx.tech.  3600  IN  A  <homeserver-ip>

; Well-known delegation (alternative to SRV — easier with CDN/proxy)
; Serve the following at: https://stealthx.tech/.well-known/matrix/server
; Content: { "m.server": "matrix.stealthx.tech:8448" }

; Also required for client auto-discovery:
; https://stealthx.tech/.well-known/matrix/client
; Content: { "m.homeserver": { "base_url": "https://matrix.stealthx.tech" } }
```

The `/.well-known/matrix/server` and `/.well-known/matrix/client` endpoints must be served from the `stealthx.tech` domain (not `matrix.stealthx.tech`) with correct CORS headers:

```
Access-Control-Allow-Origin: *
Content-Type: application/json
```

#### 4.3.5 User account model

Under Option A, each SecureCall user has a corresponding Matrix account. There are two models:

**Model 1: SecureCall creates Matrix accounts automatically (recommended)**

When a user generates their X25519 keypair on first launch, SecureCall also registers a Matrix account on the `stealthx.tech` homeserver. The Matrix account username is derived from the SecureCall ID (a base62-encoded hash of the public key, truncated to 12 characters for readability):

```kotlin
fun deriveMatrixUsername(secureCallPublicKey: ByteArray): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(secureCallPublicKey)
    val base62chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    var value = BigInteger(1, hash.copyOf(8)) // use first 8 bytes
    val sb = StringBuilder()
    while (value > BigInteger.ZERO) {
        sb.append(base62chars[(value % BigInteger.valueOf(62)).toInt()])
        value /= BigInteger.valueOf(62)
    }
    return sb.reverse().toString().take(12)
}

// Result: @sc_a7fk29mxqr3b:stealthx.tech
// Prefix "sc_" disambiguates SecureCall auto-accounts from manual registrations
```

The Matrix access token is stored in SecureCall's encrypted keystore alongside the X25519 private key. The user never sees or interacts with the Matrix account directly — it is fully managed by the SecureCall app.

**Model 2: User links existing Matrix account (optional, Phase 2)**

In Phase 2, users could link their existing Matrix account (`@gio:matrix.org`) to their SecureCall identity, enabling contacts to call them at their existing Matrix ID. This requires a Matrix account linking flow and is deferred.

#### 4.3.6 Call initiation flow (detailed)

The following describes exactly what happens when Alice (`@sc_alice:stealthx.tech`) calls Bob (`@sc_bob:matrix.org` — note: Bob is on a different server):

```
Step 1: Alice opens dialer in SecureCall, enters Bob's SecureCall ID or Matrix ID

Step 2: SecureCall Android client
  - Looks up Bob's Matrix ID (from contacts)
  - Creates a Matrix DM room with Bob (or reuses existing)
    POST /_matrix/client/v3/createRoom
    { "is_direct": true, "invite": ["@sc_bob:matrix.org"] }
  - NOTE: The DM room is created with encryption enabled (m.room.encryption)
    to protect even the signaling metadata within the Matrix network

Step 3: SecureCall generates call parameters
  - Generates new X25519 ephemeral keypair for this session (via Rust JNI)
  - Creates WebRTC PeerConnection, generates SDP offer
  - Generates call_id (UUID v4)

Step 4: Send m.call.invite to the DM room
  PUT /_matrix/client/v3/rooms/{roomId}/send/m.call.invite/{txnId}
  {
    "call_id": "uuid",
    "version": 1,
    "lifetime": 60000,
    "offer": { "type": "offer", "sdp": "..." },
    "securecall_pubkey": "<base64 X25519 ephemeral pubkey>"
    // ^ custom extension field — Matrix ignores unknown fields
  }

Step 5: Matrix federation
  - Matrix HS-A (stealthx.tech) syncs the event to Matrix HS-B (matrix.org)
  - Matrix HS-B delivers the event to Bob's clients via /sync or push

Step 6: Bob's SecureCall (or compatible Matrix VoIP client) receives invite
  - If Bob uses SecureCall: full XChaCha20 path (see Step 7a)
  - If Bob uses Element or other Matrix VoIP client: standard WebRTC path,
    XChaCha20 application-layer encryption is skipped (graceful degradation)
    The call still uses DTLS-SRTP — it is not unencrypted

Step 7a: Bob accepts (SecureCall client)
  - Bob's Rust core generates responding X25519 keypair
  - ECDH key agreement: shared_secret = X25519(alice_pubkey, bob_privkey)
  - HKDF-SHA256 derives session key from shared_secret + call_id as salt
  - Bob sends m.call.answer with his X25519 pubkey in custom field
  - Both parties now have the same session key (never transmitted)

Step 7b: ICE candidate exchange
  - Both clients send m.call.candidates events as ICE candidates are discovered
  - WebRTC resolves the best connection path (direct P2P or TURN)

Step 8: Audio flows
  - Matrix is done. Audio flows encrypted end-to-end.
  - If Bob uses SecureCall: XChaCha20-Poly1305 application layer active
  - If Bob uses Element: DTLS-SRTP only (standard WebRTC security)

Step 9: Hangup
  - Either party sends m.call.hangup
  - PeerConnection closed, session key discarded (forward secrecy)
```

#### 4.3.7 Push notifications (FCM integration with Matrix)

Matrix has a defined push notification system (Matrix Push Gateway spec). SecureCall's existing FCM integration must be connected to the Matrix push system.

**Matrix push configuration:**

Each Android device registers a Matrix push rule that causes the homeserver to send a push notification when an `m.call.invite` event is received:

```kotlin
// Register push gateway with homeserver
fun registerMatrixPushGateway(matrixClient: MatrixClient, fcmToken: String) {
    matrixClient.pushersApi.setPusher(
        Pusher(
            pushkey = fcmToken,
            kind = "http",
            appId = "tech.stealthx.android",
            appDisplayName = "SecureCall",
            deviceDisplayName = Build.MODEL,
            lang = Locale.getDefault().language,
            data = PusherData(
                url = "https://backend.stealthx.tech/_matrix/push/v1/notify",
                format = "event_id_only"
            ),
            append = false
        )
    )
}
```

**Push gateway server (`backend/matrix-push-gateway/`):**

A small Node.js or Go service that receives Matrix push notifications from the homeserver and forwards them to FCM. This replaces (or wraps) the existing FCM notification sender:

```javascript
// POST /_matrix/push/v1/notify
app.post('/_matrix/push/v1/notify', async (req, res) => {
    const { notification } = req.body;
    
    // Extract call_id and caller from the notification
    // Matrix sends event_id_only format — we fetch the full event
    const event = await matrixClient.getEvent(
        notification.room_id, 
        notification.event_id
    );
    
    if (event.type === 'm.call.invite') {
        await fcm.send({
            token: notification.devices[0].pushkey,
            data: {
                type: 'incoming_call',
                call_id: event.content.call_id,
                caller_matrix_id: event.sender,
                room_id: notification.room_id
            },
            android: {
                priority: 'high',
                ttl: '60s'  // match m.call.invite lifetime
            }
        });
    }
    
    res.json({ rejected: [] });
});
```

#### 4.3.8 Graceful degradation: calling non-SecureCall Matrix users

When a SecureCall user calls an Element user (or any Matrix VoIP client), the custom `securecall_pubkey` field in `m.call.invite` is simply ignored by the non-SecureCall client. The call proceeds as a standard WebRTC call with DTLS-SRTP encryption. 

This graceful degradation is important:
- SecureCall users can call any Matrix user without error
- The call UI should indicate encryption tier: "SecureCall E2E" vs. "Standard Matrix Call"
- No crash, no failed call — just a different encryption badge

```kotlin
enum class CallEncryptionTier {
    XCHACHA20_E2E,      // Both parties are SecureCall — full XChaCha20 stack
    MATRIX_STANDARD,    // Remote party is Matrix non-SecureCall — DTLS-SRTP only
    UNKNOWN             // During negotiation
}
```

### 4.4 Implementation Plan

#### Phase 1: Infrastructure (estimated: 3–4 weeks)

| Task | Owner | Notes |
|---|---|---|
| Deploy Synapse on Railway or VPS | Backend | PostgreSQL, TLS via nginx, Federation enabled |
| Configure DNS (SRV + well-known) | DevOps | stealthx.tech /_matrix delegation |
| Test federation with matrix.org | Backend | `federation-tester.matrix.org` tool |
| Deploy coturn TURN server | Backend | Verify direct and external-VPN network paths |
| Set up Matrix push gateway service | Backend | FCM forwarding for incoming call notifications |
| User account provisioning API | Backend | Endpoint for Android to register Matrix accounts |

#### Phase 2: Android SDK integration (estimated: 4–6 weeks)

| Task | Owner | Notes |
|---|---|---|
| Choose Matrix client library | Android | See section 4.4.1 |
| Replace WebSocket signaling with Matrix events | Android | `m.call.invite`, `m.call.answer`, `m.call.candidates`, `m.call.hangup` |
| Auto-provision Matrix account on keypair generation | Android | See section 4.3.5 |
| Connect FCM to Matrix push gateway | Android | Replaces direct FCM signaling |
| Implement `securecall_pubkey` X25519 extension | Android | Custom field in call events |
| CallEncryptionTier detection and UI badge | Android | See section 4.3.8 |
| Contact lookup: Matrix ID ↔ SecureCall ID mapping | Android | Local encrypted contact DB |
| E2E encrypt the Matrix DM room (Olm device keys) | Android | Protects signaling metadata |
| Regression test suite update (44 tests) | QA | All existing tests must still pass |

#### Phase 3: Feature parity and hardening (estimated: 2–3 weeks)

| Task | Owner | Notes |
|---|---|---|
| STEALTH-DELETE: also wipe Matrix credentials | Android | Include Matrix access token in wipe |
| Verify TURN behavior over direct and external-VPN network paths | Android/Backend | Document expected IP visibility for each path |
| Anti-recording: unchanged (not affected by signaling layer) | — | No action required |
| Migration path for existing SecureCall users | Android/Backend | Map existing SecureCall IDs to Matrix accounts |
| Rate limiting on Matrix homeserver (prevent abuse) | Backend | Synapse rate limiting config |
| Load test federation | Backend | Simulate 1000 concurrent registrations |

#### 4.4.1 Android Matrix client library evaluation

| Library | Language | Notes |
|---|---|---|
| `matrix-android-sdk2` | Kotlin | Official Element SDK. Full-featured, heavy (~8MB AAR). Best for full compliance. |
| `matrix-rust-sdk` (Android bindings) | Rust | Native Rust, very active, smaller binary. Experimental Android bindings. |
| Custom lightweight HTTP client | Kotlin | Implement only the 4 call events manually. Smallest footprint. Best for SecureCall's narrow use case. |

**Recommendation: Custom lightweight HTTP client** for Phase 1.

SecureCall only needs 6 Matrix API endpoints for Option A Phase 1:

```
POST /_matrix/client/v3/register                    # provision account
POST /_matrix/client/v3/createRoom                  # create DM room
PUT  /_matrix/client/v3/rooms/{roomId}/send/{type}  # send call events
GET  /_matrix/client/v3/sync                        # receive call events
POST /_matrix/client/v3/pushers/set                 # register FCM pusher
POST /_matrix/client/v3/logout                      # cleanup
```

Implementing these 6 endpoints with Retrofit (already in the project) is ~500 lines of Kotlin and adds zero external dependencies. The full `matrix-android-sdk2` is 50,000+ lines and pulls in significant additional dependencies. For SecureCall's focused scope, the custom approach is correct.

```kotlin
interface MatrixSignalingApi {
    @POST("_matrix/client/v3/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("_matrix/client/v3/createRoom")
    suspend fun createRoom(
        @Header("Authorization") bearer: String,
        @Body request: CreateRoomRequest
    ): CreateRoomResponse

    @PUT("_matrix/client/v3/rooms/{roomId}/send/{eventType}/{txnId}")
    suspend fun sendEvent(
        @Header("Authorization") bearer: String,
        @Path("roomId") roomId: String,
        @Path("eventType") eventType: String,
        @Path("txnId") txnId: String,
        @Body content: JsonObject
    ): SendEventResponse

    @GET("_matrix/client/v3/sync")
    suspend fun sync(
        @Header("Authorization") bearer: String,
        @Query("timeout") timeout: Long = 30000,
        @Query("since") since: String? = null,
        @Query("filter") filter: String? = null
    ): SyncResponse

    @POST("_matrix/client/v3/pushers/set")
    suspend fun setPusher(
        @Header("Authorization") bearer: String,
        @Body pusher: PusherRequest
    ): Unit
}
```

### 4.5 Security analysis for Option A

#### 4.5.1 What changes in the threat model

| Threat vector | Before Option A | After Option A |
|---|---|---|
| Server compromise (stealthx.tech) | Server sees: encrypted WebSocket signaling | Server sees: Matrix signaling events (SDP, ICE, call_id). Audio: unchanged — never reaches server |
| Network attacker on signaling | WebSocket TLS protects signaling | Matrix HTTPS + room encryption (Olm) protects signaling |
| Matrix homeserver admin | N/A | Can see: call metadata (who called whom, when, duration) if DM room is NOT E2E encrypted. Room E2E encryption (Phase 2) eliminates this. |
| Third-party homeserver (Bob's server) | N/A | Bob's homeserver receives the `m.call.invite` event. With E2E room encryption, the content is opaque — only the room ID and event type are visible. |
| Replay attack on m.call.invite | N/A | `call_id` (UUID v4) + `lifetime` field (60 seconds) prevents replay. |
| TURN server IP visibility | TURN sees the connection source address | Verify and document direct and externally managed VPN paths — see section 4.3.7 |

#### 4.5.2 Olm device encryption for signaling (Phase 2 requirement)

To fully protect signaling metadata from homeserver admins (including the stealthx.tech Synapse admin), the DM room used for call signaling must have E2E encryption enabled (`m.room.encryption` with `m.megolm.v1.aes-sha2`). This means:

- The SDP offer/answer content is encrypted with Megolm (Olm group session)
- The homeserver only sees an opaque ciphertext blob
- Call metadata (who called whom) is still visible in room membership — this is a known limitation of Matrix E2E encryption

Implementing Olm requires either using `matrix-android-sdk2` (which handles Olm/Megolm internally) or integrating `libolm` or `vodozemac` (Rust reimplementation of Olm) directly. This is the primary reason Olm is deferred to Phase 2 — it represents significant complexity and should be done after the basic signaling integration is stable and tested.

#### 4.5.3 Cryptographic non-regression checklist

Before Option A ships, the following must be verified unchanged:

- [ ] XChaCha20-Poly1305 encryption is applied to every audio frame (Rust unit tests)
- [ ] X25519 key exchange happens on-device, keypairs never transmitted (audit `core_crypto/`)
- [ ] Double Ratchet ratchets forward on every session (per-call key derivation test)
- [ ] STEALTH-DELETE wipes Matrix credentials in addition to existing data
- [x] No app-owned VPN routing: Matrix traffic follows Android's active network, including an optional external device VPN
- [ ] Certificate pinning applies to `matrix.stealthx.tech` endpoint (add to pinned certs)
- [ ] No plaintext fallback: if Matrix signaling fails, call must fail — not fall back to unencrypted WebSocket

### 4.6 Roadmap entry (Option A)

```
[ ] EPIC: Matrix Signaling Integration (Option A)
    Priority: High (Strategic)
    Milestone: v2.0
    Labels: matrix, architecture, federation, signaling
    
    Strategic goal: SecureCall users addressable from entire Matrix network (28M+ users)
    
    Sub-tasks:
    
    [ ] INFRA-01: Deploy Synapse homeserver on stealthx.tech
        - PostgreSQL backend
        - nginx TLS termination
        - DNS SRV + well-known delegation
        - Federation testing with federation-tester.matrix.org
    
    [ ] INFRA-02: Deploy coturn TURN server
        - External device-VPN compatibility verification
        - Dynamic TURN credentials (already implemented for existing TURN)
    
    [ ] INFRA-03: Matrix push gateway service
        - Receives Matrix push from Synapse
        - Forwards m.call.invite notifications to FCM
        - Replaces existing direct FCM signaling notification
    
    [ ] INFRA-04: Matrix account provisioning API
        - POST /api/v1/provision-matrix-account
        - Called by Android on first keypair generation
        - Returns Matrix access token (stored in encrypted keystore)
    
    [ ] ANDROID-01: Retrofit Matrix Signaling API client
        - 6 endpoints only (see section 4.4.1)
        - No full Matrix SDK dependency
    
    [ ] ANDROID-02: Replace WebSocket signaling with Matrix events
        - m.call.invite / m.call.answer / m.call.candidates / m.call.hangup
        - Custom securecall_pubkey extension field
        - Backward compat: existing WebSocket path for users not yet migrated
    
    [ ] ANDROID-03: CallEncryptionTier detection
        - XCHACHA20_E2E vs MATRIX_STANDARD UI badge
        - Graceful degradation when calling non-SecureCall Matrix users
    
    [ ] ANDROID-04: STEALTH-DELETE update
        - Include Matrix access token in wipe
        - POST /_matrix/client/v3/logout (best effort, offline-tolerant)
    
    [ ] ANDROID-05: Certificate pinning for matrix.stealthx.tech
    
    [ ] QA-01: Full regression test suite pass (all 44 existing tests)
    [ ] QA-02: Federation test: SecureCall → Element call (cross-server)
    [ ] QA-03: Federation test: Element → SecureCall call (inbound from Matrix network)
    [ ] QA-04: TURN IP-visibility test on direct and external-VPN paths
    [ ] QA-05: STEALTH-DELETE verification (Matrix credentials wiped)
    
    [ ] PHASE-2 (post-launch):
        - Olm/Megolm E2E encryption for signaling room
        - Link existing Matrix account to SecureCall identity
        - MSC3401 group calls
    
    Acceptance criteria (Phase 1):
    - SecureCall user can call any @user:server Matrix user
    - Any Element user can call a SecureCall user by their @sc_xxx:stealthx.tech ID
    - All existing SecureCall features work unchanged
    - STEALTH-DELETE wipes Matrix credentials
    - Matrix API and TURN IP visibility is documented for direct and external-VPN paths
    - No increase in binary size > 500KB
    - All 44 regression tests pass
    
    Acceptance criteria (Phase 2):
    - Matrix signaling room is E2E encrypted (Olm)
    - Homeserver admin cannot read SDP content of any call
```

---

## 5. Security Considerations

### 5.1 What Matrix does NOT provide

It is important to be clear in external communications about what the Matrix integration adds and what it does not add.

| Claim | Accurate? | Notes |
|---|---|---|
| "Matrix encrypts SecureCall calls" | INCORRECT | Matrix provides signaling transport. Audio encryption is SecureCall's XChaCha20-Poly1305, unchanged. |
| "Matrix adds another layer of encryption to calls" | PARTIALLY | Matrix HTTPS + optional Olm encrypt the signaling layer. The audio layer is already encrypted by SecureCall. |
| "Your calls are on the Matrix network" | INCORRECT | Signaling uses Matrix. Audio uses WebRTC P2P/TURN. Matrix has no access to audio. |
| "SecureCall is now decentralized" | CORRECT (with nuance) | Signaling is federated via Matrix. Audio relay (TURN) is still Vendetta Labs-operated. |
| "SecureCall users can call Element users" | CORRECT | With graceful degradation (DTLS-SRTP, no XChaCha20 for non-SecureCall clients). |

### 5.2 Communication guidelines

When communicating Option A to the community and press, use this framing:

> "SecureCall is integrating with the Matrix protocol to bring federated, interoperable voice calling to our users. This means SecureCall users will be reachable from any Matrix client — Element, FluffyChat, and many others. Our cryptographic core — XChaCha20-Poly1305, Double Ratchet, and our Rust native crypto engine — remains unchanged and continues to provide best-in-class end-to-end encryption for all SecureCall-to-SecureCall calls."

---

## 6. Roadmap Entries

### 6.1 Summary roadmap

| Option | Feature | Milestone | Effort | Priority |
|---|---|---|---|---|
| C | Matrix contact exchange (Share ID, QR, deep link) | v1.7 | ~14 hours | Medium |
| A Phase 1 | Matrix signaling backend (federation, interop) | v2.0 | ~10–13 weeks | High (Strategic) |
| A Phase 2 | Olm E2E encryption for signaling room | v2.1 | +3–4 weeks | High (Security) |
| A Phase 3 | MSC3401 group calls via Matrix | v2.2 | TBD | Low |

### 6.2 CHANGELOG entries to add

```markdown
## [Unreleased — v1.7]
### Added
- Share SecureCall ID via Matrix (or any app) with deep link support
- `securecall://contact/<id>` deep link for contact exchange
- QR code generation for SecureCall ID in profile screen
- QR code scanner for incoming SecureCall contact QR codes
- `https://stealthx.tech/add/<id>` web redirect for web-based Matrix clients

## [Roadmap — v2.0]
### Added (planned)
- Matrix federation: SecureCall users are now reachable at @sc_xxx:stealthx.tech
- Interoperability: SecureCall users can call any Matrix VoIP client (Element, FluffyChat, etc.)
- Inbound calls from Matrix network to SecureCall users
- CallEncryptionTier indicator: XChaCha20 E2E badge vs. Standard Matrix Call badge
- STEALTH-DELETE now also wipes Matrix credentials
```

---

## 7. Glossary

| Term | Definition |
|---|---|
| **Matrix** | Open, federated communication protocol (matrix.org). Not a product — a standard. |
| **Homeserver** | A Matrix server instance. Users have accounts on a homeserver (`@user:homeserver`). |
| **Federation** | The ability of different Matrix homeservers to exchange messages and events with each other. |
| **Synapse** | The reference Matrix homeserver implementation (Python). Most widely deployed. |
| **Dendrite** | Alternative Matrix homeserver (Go). Lighter weight. |
| **MSC** | Matrix Spec Change — a proposal to change or extend the Matrix spec. MSC2746 covers VoIP. |
| **SDP** | Session Description Protocol — describes WebRTC connection parameters (codecs, IPs). Exchanged during signaling. |
| **ICE** | Interactive Connectivity Establishment — WebRTC's mechanism for finding the best network path between two clients. |
| **TURN** | Traversal Using Relays around NAT — a relay server used when direct P2P WebRTC fails. |
| **STUN** | Session Traversal Utilities for NAT — helps clients discover their external IP. |
| **Olm** | Matrix's 1:1 encryption protocol (based on Double Ratchet). Used for E2E encrypting room content. |
| **Megolm** | Matrix's group encryption protocol. Used for encrypting room events in multi-party rooms. |
| **to_device** | A Matrix API for sending encrypted messages directly to a specific device, bypassing room history. Used for key exchange. |
| **Application Service (AS)** | A Matrix integration service that can bridge Matrix to external systems. Used in Option B. |
| **External VPN** | Optional device-managed network layer. SecureCall does not create or operate a VPN tunnel; Matrix traffic follows Android's active network. |
| **DTLS-SRTP** | WebRTC's transport-layer encryption. Present in all WebRTC calls regardless of SecureCall's application layer. |
| **XChaCha20-Poly1305** | SecureCall's application-layer AEAD cipher. 256-bit key, 192-bit nonce. Applied on top of DTLS-SRTP. |
| **Deep link** | A URI scheme (`securecall://`) that routes a URL tap directly to a specific screen in the SecureCall Android app. |

---

*This document is maintained in `docs/WIKI/Matrix-Integration.md`. For questions, open an issue in the GitHub repo or contact the Vendetta Labs core team.*
