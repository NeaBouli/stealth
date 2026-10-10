# SecureCall Ecosystem – Security Design Document

> **Current release boundary (Android 1.0.50):** This document separates current
> behavior from research and long-range architecture concepts. The released
> Android client uses WebRTC with STUN/TURN and application-layer end-to-end encryption.
> The Google Play edition contains no built-in VPN or WireGuard code. The direct
> Premium APK can run a consent-gated, app-only WireGuard tunnel using a locally
> supplied configuration whose private key is encrypted with Android Keystore.
> Neither edition contains a multi-hop relay, SilentCarrier, QUIC transport,
> device-owner controls, or OS-level hardening. Both can operate over a VPN
> managed by Android or another trusted app. Chapters explicitly marked
> **Research roadmap** describe ideas, not shipped or purchasable functionality.

## 1. Purpose of This Document

This document describes all security-relevant principles,
mechanisms, architectural decisions, and requirements of the SecureCall Ecosystem.
It serves as the master reference for developers, auditors, and architects.

## 2. Security Goals (High-Level)

1. **End-to-End Encryption**
   No audio may ever leave the device unencrypted.

2. **Metadata Minimization**
   The system generates as few metadata as technically possible.
   Call content is not stored. Limited routing, delivery, entitlement and
   operational data is handled as documented in the privacy policy.

3. **Zero-Retention Principle**
   Per-call key material is kept only for the active call and then discarded.
   Long-term authenticated identity keys are not a current product guarantee.

4. **Defense in Depth**
   Multiple layers protect independently:
   - Crypto
   - WebRTC/STUN/TURN transport
   - Signaling
   - Security Monitor
   - Android platform controls

5. **Fail-Safe Default**
   When in doubt → block, never continue in a weakened state.

6. **Transparency & Auditability**
   Code shall be testable, reproducible, and auditable.


3. Threat Model

The SecureCall Ecosystem considers multiple attacker classes:

Local attackers on the end device

Network-adjacent attackers in the transport path

Infrastructure-adjacent attackers at the server/relay level

Radio cell/baseband attackers (IMSI catchers etc.)

OS/firmware-based attacks (particularly relevant for GHOSTOS)

3.1 Local Attackers

Goals:

Access to plaintext audio

Access to key material

Manipulation of the app (hooking, code injection)

Typical tools:

Malware, keyloggers

Rootkits, Magisk modules

Frida, Xposed, emulators

Screen recording and UI capture

Countermeasures:

Security Monitor & root/hooking detection

Prevention of screen recording

Minimal attack surface in the UI

No debug build in production

3.2 Network-Adjacent Attackers

Goals:

Interception of audio

Man-in-the-middle attacks

Manipulation or replay of packets

Typical scenarios:

Malicious WLANs

Compromised routers

State/carrier-based surveillance

Countermeasures:

Application-frame encryption against passive network observers

Integrity protection of frames

No call audio or per-call private key material sent to the signaling service

Research-only multi-hop relay concept (not part of the current release)

Current limitation: the per-call exchange is not authenticated by a long-term
identity signature, so an active signaling-server key-substitution attack is not
yet prevented.

3.3 Infrastructure-Adjacent Attackers

Goals:

Compromise of signaling servers and relays

Mass surveillance of metadata

Blocking or redirecting connections

Countermeasures:

Minimalist signaling logic

Documented retention limits and log redaction

Pseudonymous SecureIDs rather than mandatory real names

Relays without access to keys/plaintext

3.4 Radio Cell and Baseband Attackers

Goals:

Identifying who communicates with whom

Forcing insecure radio modes

IMSI/IMEI capture and movement profiles

Research requirements (not implemented in the current release):

IMSI catcher detection (anomaly detection)

Warnings on suspicious cell IDs

Restricted radio profiles (e.g., LTE-only)

Optional complete deactivation of classic GSM telephony


4. Crypto Design & Key Management

The SecureCall Ecosystem uses modern, auditable cryptography modules.
The Core Crypto Engine (Rust) provides all security primitives.

4.1 Identity Authentication Status

The current SecureCall call setup does not bind the ephemeral X25519 exchange to
a long-term authenticated identity signature. Ed25519-backed identity binding,
verified device migration and hardware-backed identity storage are research
requirements, not current product guarantees.

4.2 Session Setup

Each call uses X25519 and HKDF-SHA256 to derive per-call key material. That
material is used with XChaCha20-Poly1305 for application media frames and is
discarded when the call ends.

The current release does not implement a Double Ratchet, Noise handshake,
per-frame key rotation, authenticated identity-key exchange or post-compromise
security. A malicious signaling service is outside the current cryptographic
protection boundary.

4.3 Audio Frame Encryption

An AEAD scheme is used for audio frames:

XChaCha20-Poly1305

192-bit nonce

256-bit key

Nonce increment per frame (overflow never permitted)

AEAD integrity verification under the documented key and nonce assumptions

4.4 Key Lifecycles

Identity Keys → long-term (1 identity per device)

Session Keys → valid only for one call

Frame nonces → unique per frame within the per-call key lifecycle

Keys are deleted from RAM using zeroize/explicit_bzero

4.5 Zero-Retention Principle

The app never stores:

Session keys

Audio frames

Call content; limited connection metadata is handled as documented in the privacy policy

Network statistics (except technical metrics without identity)

After call end:

All keys are overwritten

All call data is discarded

Memory is purged

4.6 Cryptographic Constraints

All operations constant-time

No fallback to insecure modes

No use of hardware-accelerated functions that could produce side-channel leaks

No external dependency on cloud services


5. Security Monitor & Hardening Layer

The Security Monitor is a local security component that monitors the device status
and the runtime environment. The goal is to detect manipulations and insecure
states and respond appropriately.

5.1 Root and Jailbreak Detection

Detection of Magisk, SuperSU, KingRoot

Check for manipulated /system partitions

Detection of suspicious binaries (su, busybox in atypical paths)

Check for write access in protected areas

Policy:

Free: warning

Pro: optional block

Premium/OS: block mandatory

5.2 Hooking and Debugger Protection

Detection of Frida servers & Frida gadgets

Detection of Xposed / LSPosed

Detection of active ptrace/debugging sessions

Anti-breakpoint checks (timing, debugger flags)

Protection against code injection through app signature verification

Response:

Abort session

Warning message

Locked state until restart

5.3 Emulator Detection

Check for typical emulator properties (ro.product.device, Google API Level Images)

Detection of missing sensors

CPU architecture inconsistencies

Unusual network configurations (10.0.2.x etc.)

Response:

Free: warning

Pro: optional block

Premium/OS: block

5.4 Screen-Recording Detection

Monitoring "FLAG_SECURE"

Detection of active screen capture APIs

Detection of background recording apps

Automatic UI lock when recording is active

5.5 Baseband & Radio Cell Checks (Premium/OS)

IMSI catcher detection via:

Suspicious LAC/CID changes

Unusually strong signal levels

Unencrypted 2G downswitches

Missing network authentication

Warning on suspicious base stations

Optional automatic deactivation of GSM module

5.6 Device Status Monitoring

USB debugging enabled? → warning/block

Developer options active? → warning/block

Screenshot APIs active? → block

External keyboards/controllers → check (keylogging risk)

5.7 Policy-Based Responses

The Security Monitor reports a state, the Policy Engine decides how to respond:

Allow

Warn

Block

Do not start call at all

Terminate app

Isolate device (Premium/OS)


6. Transport Layer

The current Android release uses WebRTC with STUN/TURN plus application-layer
encryption. Older code and documents use the internal name GhostNet for several
transport experiments; that name does not imply a deployed relay network or IP
masking product.

6.1 Architecture

The current path consists of:

Transport (WebRTC)

Audio Engine (Opus)

Routing (peer-to-peer where possible, otherwise TURN relay)

6.2 Transport (WebRTC / QUIC)

The current release uses WebRTC. QUIC transport is research-only and is not a
current tier feature.

Properties:

NAT traversal via STUN/TURN

Congestion Control

Packet prioritization (Audio > Metadata)

Reconnect mechanism during network fluctuations

6.3 Audio Engine

Codec: Opus

Bitrate adaptive (12–32 kbps)

Lowest possible latency (20–40 ms)

FEC (Forward Error Correction) enabled

Jitter buffer with dynamic size

6.4 Current WebRTC/TURN Routing

P2P connection or relay via TURN

Metadata kept minimal

No relay access to audio (everything encrypted)

6.5 Multi-Hop Routing (Research Roadmap - Not Implemented)

No current SecureCall edition supports or sells multi-hop routing. The following
chain is a research concept only:

Example chain:

Client A → Relay 1 → Relay 7 → Relay 3 → Client B

Each hop sees only:

IP of the previous relay

IP of the next relay

NO audio content (E2E encrypted)

Goals:

Obfuscation of origin

Making traffic correlation more difficult

Protection against state surveillance measures

6.6 SilentCarrier Mode (Research Roadmap - Not Implemented)

No current SecureCall edition implements SilentCarrier. The following is a
research concept only:

The app initiates a normal GSM call

Microphone access to GSM is blocked

GSM call carries no audio

The real audio call runs over GhostNet

Result: externally it appears like a normal phone call

6.7 Transport Security Rules

No plaintext audio outside the app

No fallback to unencrypted transport modes

On transport errors → abort call, never continue in "insecure" mode

Implement frame limits & nonce monitoring


7. Signaling, Identity & Key Directory

The signaling system serves exclusively to connect
two devices with each other.
It processes pseudonymous identifiers and transient routing/delivery metadata.
The current privacy policy defines what is retained or logged.

7.1 Identity Model

The current app uses a SecureID and per-call X25519 material. It does not yet
provide a cryptographically authenticated long-term identity-key binding. The
backend must not be described as knowing no user data; it processes network and
routing data required to deliver signaling and push events.

7.2 Registration (pseudonymous)

A client registers as follows:

Registers its SecureID and current delivery/routing data through the documented
WebSocket protocol. Authentication and identity-key binding remain active audit
work and are not claimed as complete.

The registration serves only to:

Be reachable

Make the public key retrievable

7.3 Signaling Flow

The signaling server performs the following tasks:

Send call invite to target

Call response (Accept/Reject)

Exchange of ICE candidates

Notification "call ended"

Important: the server sees the pseudonymous identifiers and network metadata
required for signaling. It does not receive call plaintext.

7.4 Transport Mechanisms

REST for registration / public key lookup

WebSocket for events:

invite

answer

candidate

end-call

The server does not store state data longer than technically necessary.

7.5 Key Directory

Long-term authenticated identity-key directory behavior is a future design
requirement. It is not a guarantee of the current release.

7.6 Signaling Security

Signaling is not intended to receive call plaintext. The current release does
not cryptographically authenticate all signaling content with Ed25519 and does
not protect against an actively malicious signaling service. Registration and
key-binding hardening are tracked release blockers.


8. Policy Engine & Product Line Profiles (Research Roadmap)

The Policy Engine is the central control component of the SecureCall Ecosystem.
It defines how the system responds to risks, configurations, and network conditions.

The following profiles are design goals, not shipped tier guarantees. Current
tier behavior must be taken only from tested Android flavor code and the active
distribution matrix.

Free (GhostTalk Basic)

Pro (GhostShield Advanced)

Premium (PhantomLine Elite)

OS (GHOSTOS BlackRoot)

8.1 Policy Structure

A policy consists of:

Network requirements

Device requirements

Root/debug behavior

Permitted transport modes

Allowed UI features

Activation of security features

Responses to risks

Example (simplified):

{
  "require_secure_wlan": true,
  "allow_mobile_data": false,
  "allow_rooted_device": false,
  "allow_external_vpn": true,
  "enable_imsi_detection": false,
  "max_ghostnet_hops": 1
}

8.2 Free Policy – GhostTalk Basic

WLAN recommended but not enforced

Rooted devices allowed → warning only

No built-in VPN service; external device VPN supported with active-route indicator

WebRTC/TURN only; no multi-hop or SilentCarrier implementation

No IMSI detection

Minimal hardening

May run in parallel with other apps

8.3 Pro Policy – GhostShield Advanced

Secure WLAN → preferred

Mobile data allowed

Root optionally blockable

No built-in VPN service; external device VPN supported with active-route indicator

WebRTC/TURN only; no multi-hop or SilentCarrier implementation

Stronger anti-tampering

Zero-retention key management enforced

Anti-debug / anti-emulator mandatory

8.4 Premium Policy – PhantomLine Elite

WLAN or "dedicated mobile network profile"

Direct APK may use an optional consent-gated, app-only WireGuard tunnel; Play builds remain VPN-service-free

No operation on rooted devices

Device-Owner mode mandatory

App whitelist active

No multi-hop implementation

IMSI catcher detection is not implemented

Stealth UI available

All background processes blocked

Radio profiles restricted (LTE-only possible)

SilentCarrier is not implemented

8.5 OS Policy – GHOSTOS BlackRoot

Full control over the device

No root possible (hardened kernel)

All third-party apps blocked

No browser/WebView components

Kernel-level firewall

GhostNet Multi-Hop deeply integrated in system

Microphone/camera exclusive for SecureCall

Strictest radio profile control

No background processes

RAM purging after each session

8.6 Policy Engine Evaluation

The Policy Engine returns decisions:

ALLOW (operation uncritical)

WARN (user must confirm risk)

BLOCK (operation is aborted)

HARD_BLOCK (app/OS terminates operation immediately)

These decisions are enforced by the Security Monitor
and apply globally in the app.


9. OS-Level Security - GHOSTOS BlackRoot (Research Roadmap)

GHOSTOS BlackRoot is a research concept for a hardened special-purpose Android
operating system. It is not implemented, distributed or sold as part of the
current SecureCall release.
It follows the principle "Maximum Security – Minimum Surface".

9.1 Core Principles

No third-party apps

No browser, no WebView

No Google Services

No background processes

No telemetry

Microphone & camera exclusive for SecureCall

RAM purging after each session

System-wide firewall

Reproducible builds

No debug interfaces whatsoever

9.2 Kernel Hardening

The kernel is modified and hardened:

Disabled debug functions

Hardened SELinux policies (enforcing)

Disabled ptrace

Restrictive syscall whitelists

Restrictive seccomp configuration

Restrictive cgroups for resource management

Disabled unused kernel code

9.3 System-Level Restrictions

No Play Store

No package installer

No third-party apps

No background services except SecureCall

No synchronization services

No filesystem access for user apps

9.4 Secure System Partition

Read-only system partition

Verified Boot active

No bootloader unlock possible

Recovery only with signature

No ADB, no Fastboot (except in Manufacturing Mode)

9.5 Network and Radio Profile Control

System-wide firewall (iptables/nftables)

Allowed connections:

SecureCall Signaling

GhostNet Relays

All other IP connections blocked

Radio profiles:

LTE-only possible

2G/GSM deactivatable

Automatic block on suspicious cell IDs

9.6 Resource Isolation

Microphone and camera exclusively through SecureCall app

No API access for other processes whatsoever

No screenshot or screen recording function in the OS

Restrictive access to sensors (accelerometer, gyro optionally disabled)

9.7 RAM Purge & Secret Management

After each call:

Complete memory trees are cleared

Zeroize for cryptographic structures

Termination of all services

Restart optionally enforceable (Enterprise Option)

9.8 Device-Owner Mode

SecureCall is system app + Device-Owner

No uninstallation possible

No app additions possible

Policy enforces system-wide rules

9.9 Build Chain & Transparency

Deterministic builds

Reproducible build scripts

Hash comparison for OTA updates

Signed system images

Internal audit process


10. Integration, API Boundaries & Security Architecture

This chapter records target boundaries. Statements about GHOSTOS, multi-hop
relays and signed signaling are research requirements unless a current-release
note explicitly says otherwise.

10.1 API Boundaries & Responsibilities

Each component has strictly defined responsibilities:

Crypto Engine (Rust)

Generates & manages keys

Encrypts & decrypts audio frames

Performs zeroize

Has no network access

Android Client

Provides UI, call control, error handling

Enforces policies

Communicates with signaling & GhostNet

Stores no sensitive data

Signaling Backend

Knows only public keys & temporary session data

No logs, no telemetry

Serves exclusively for connection, not for routing

GhostNet Relays

Forward exclusively encrypted frames

Know neither identity nor destination of the user

Store only the documented minimum metadata required for operation and safety

GHOSTOS BlackRoot

Enforces OS policies

Prevents manipulation

Controls radio profiles and resources

10.2 Integration Boundaries (Security Boundaries)

Crypto Engine ↔ Android App

Exchange only encrypted payloads

Private key material must remain inside the documented crypto boundary

Communication via FFI is limited & verified

Android App ↔ Backend

Backend is not trusted for call plaintext. Current signaling is not fully
authenticated and remains release-blocking audit work.

GhostNet ↔ Backend

Relays cannot view frame content

Only forwarding, no access to session keys

OS ↔ SecureCall App

OS protects app

App protects data

App controls microphone/camera exclusively

10.3 Trust Model

The trust chain is defined as follows:

Crypto Engine
Highest trust level

SecureCall App
Trusts crypto, but not the OS

OS (BlackRoot research concept)
Target only; not part of the current release

GhostNet Relays
Untrusted

Signaling Backend
Untrusted

10.4 Minimal Metadata

The current system aims to minimize metadata. It does not guarantee zero
metadata: network endpoints, pseudonymous identifiers, delivery state,
entitlement facts and operational records may be processed or retained as
described in the privacy policy and server configuration.

10.5 Error Handling & Fail-Safe

When in doubt:

Never switch to an insecure state

No fallbacks to unencrypted channels

Rather abort call than continue in a weakened state

Inform user (unless OS policy requires silent block)

10.6 Auditability

Deterministic builds

Traceable release chains

Internal code review process

Security tests mandatory

Premium/OS: external audits required

10.7 Security Architecture Summary

The current architecture is intended to ensure:

Plaintext never leaves the device.

Call content is protected independently of the signaling transport, subject to
the current unauthenticated key-exchange limitation.

Metadata is minimized to the limit of technical feasibility.

The Android app protects its local cryptographic state within documented
platform limits. GHOSTOS and additional OS hardening remain research concepts.

The backend is outside the call-plaintext trust boundary, but current signaling
authentication hardening is still required before a final release claim.
