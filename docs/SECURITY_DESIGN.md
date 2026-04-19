# SecureCall Ecosystem – Security Design Document

## 1. Purpose of This Document

This document describes all security-relevant principles,
mechanisms, architectural decisions, and requirements of the SecureCall Ecosystem.
It serves as the master reference for developers, auditors, and architects.

## 2. Security Goals (High-Level)

1. **End-to-End Encryption**
   No audio may ever leave the device unencrypted.

2. **Metadata Minimization**
   The system generates as few metadata as technically possible.
   No logs, no tracking, no telemetry.

3. **Zero-Retention Principle**
   Cryptographic keys are kept as briefly as possible,
   never persisted (except Identity Key under secure conditions).

4. **Defense in Depth**
   Multiple layers protect independently:
   - Crypto
   - GhostNet Transport
   - Signaling
   - Security Monitor
   - OS Hardening (Premium/OS)

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

End-to-end encryption

Integrity protection of frames

No keys or plaintext in the backend

Optional multi-hop GhostNet routing

3.3 Infrastructure-Adjacent Attackers

Goals:

Compromise of signaling servers and relays

Mass surveillance of metadata

Blocking or redirecting connections

Countermeasures:

Minimalist signaling logic

No long-term logs

Public key directory without real names

Relays without access to keys/plaintext

3.4 Radio Cell and Baseband Attackers

Goals:

Identifying who communicates with whom

Forcing insecure radio modes

IMSI/IMEI capture and movement profiles

Countermeasures (Premium/OS):

IMSI catcher detection (anomaly detection)

Warnings on suspicious cell IDs

Restricted radio profiles (e.g., LTE-only)

Optional complete deactivation of classic GSM telephony


4. Crypto Design & Key Management

The SecureCall Ecosystem uses modern, auditable cryptography modules.
The Core Crypto Engine (Rust) provides all security primitives.

4.1 Identity Keys

Type: Ed25519 (signature) + X25519 (key exchange)

Generation: locally on the device

Storage:

Free/Pro: encrypted in app storage

Premium/OS: in hardware TPM / Secure Element, if available

Export: prohibited

Backup: not provided (security risk)

4.2 Session Setup

Each call uses a new session:

Start via X25519 key agreement

Derivation of a one-time root key

Establishment of a Double Ratchet or Noise_NX / Noise_XX protocol

Automatic key rotation (forward secrecy)

A compromised session key must not:

Allow decryption of previous audio frames (FS)

Compromise future frames (PCS)

4.3 Audio Frame Encryption

An AEAD scheme is used for audio frames:

XChaCha20-Poly1305

192-bit nonce

256-bit key

Nonce increment per frame (overflow never permitted)

Guaranteed integrity protection

4.4 Key Lifecycles

Identity Keys → long-term (1 identity per device)

Session Keys → valid only for one call

Frame Keys → rotate continuously

Keys are deleted from RAM using zeroize/explicit_bzero

4.5 Zero-Retention Principle

The app never stores:

Session keys

Audio frames

Connection metadata

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


6. GhostNet Transport Layer

The GhostNet Layer is the encrypted transport channel of the SecureCall Ecosystem.
It ensures that audio frames leave the device exclusively in encrypted form.

6.1 Architecture

GhostNet consists of three sublayers:

Transport (WebRTC/QUIC)

Audio Engine (Opus)

Routing (Single-Hop / Multi-Hop)

6.2 Transport (WebRTC / QUIC)

GhostNet uses depending on device and product line:

WebRTC with DTLS for maximum compatibility

or QUIC with embedded AEAD layer for Premium/OS

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

6.4 Single-Hop GhostNet (Free/Pro)

P2P connection or relay via TURN

Metadata kept minimal

No relay access to audio (everything encrypted)

6.5 Multi-Hop Routing (Premium/OS)

Premium and OS support multi-hop GhostNet routing:

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

6.6 SilentCarrier Mode

Available for Free/Pro/Premium (OS optionally disabled):

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
It knows no user data and stores no connection metadata.

7.1 Identity Model

Each device has:

Identity Key Pair (Ed25519/X25519)

Generated locally

Never exported

Never stored on backend

Optional: Stealth identities (Premium/OS)

Multiple identities per device

Rotating

Ideal for covert communication

The backend knows only public keys, never real names or phone numbers.

7.2 Registration (pseudonymous)

A client registers as follows:

Sends public key

Receives a random, meaningless user ID

No IP logs (only ephemeral)

No timestamp persistence

The registration serves only to:

Be reachable

Make the public key retrievable

7.3 Signaling Flow

The signaling server performs the following tasks:

Send call invite to target

Call response (Accept/Reject)

Exchange of ICE candidates

Notification "call ended"

Important:
The server never knows the identity of participants beyond a public key.

7.4 Transport Mechanisms

REST for registration / public key lookup

WebSocket for events:

invite

answer

candidate

end-call

The server does not store state data longer than technically necessary.

7.5 Key Directory

The Key Directory allows:

Retrieve public key of user X

Identity consisting of:

random user_id

public_key

The Key Directory does not store:

IP addresses

Timestamps

Communication partners

Device information

7.6 Signaling Security

Signaling itself is not trusted.

Therefore:

All content is signed

Identity-based verification via Ed25519

Replay protection

No sensitive content sent via signaling


8. Policy Engine & Product Line Profiles

The Policy Engine is the central control component of the SecureCall Ecosystem.
It defines how the system responds to risks, configurations, and network conditions.

Each product line receives its own policy profile:

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
  "require_vpn": false,
  "enable_imsi_detection": false,
  "max_ghostnet_hops": 1
}

8.2 Free Policy – GhostTalk Basic

WLAN recommended but not enforced

Rooted devices allowed → warning only

No VPN firewall

GhostNet: Single-Hop

SilentCarrier Mode available

No IMSI detection

Minimal hardening

May run in parallel with other apps

8.3 Pro Policy – GhostShield Advanced

Secure WLAN → preferred

Mobile data allowed

Root optionally blockable

VPN firewall active (app-local VPN)

GhostNet: Single-Hop, optimized

SilentCarrier Mode active

Stronger anti-tampering

Zero-retention key management enforced

Anti-debug / anti-emulator mandatory

8.4 Premium Policy – PhantomLine Elite

WLAN or "dedicated mobile network profile"

No operation on rooted devices

Device-Owner mode mandatory

App whitelist active

GhostNet: Multi-Hop

IMSI catcher detection active

Stealth UI available

All background processes blocked

Radio profiles restricted (LTE-only possible)

SilentCarrier Mode active

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


9. OS-Level Security – GHOSTOS BlackRoot

GHOSTOS BlackRoot is a hardened special-purpose operating system based on Android
for military and high-security deployments.
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

This chapter describes the interaction of modules and defines
what security guarantees each layer provides and where explicit boundaries lie.

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

Store no metadata

GHOSTOS BlackRoot

Enforces OS policies

Prevents manipulation

Controls radio profiles and resources

10.2 Integration Boundaries (Security Boundaries)

Crypto Engine ↔ Android App

Exchange only encrypted payloads

App never receives private keys in plaintext

Communication via FFI is limited & verified

Android App ↔ Backend

Backend is not trusted

Signals are signed/verified

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

OS (BlackRoot)
Trusts app, protects hardware & radio

GhostNet Relays
Untrusted

Signaling Backend
Untrusted

10.4 Minimal Metadata

The system guarantees:

No storage of IP addresses

No storage of communication partners

No persistence of timestamps

No device information

No analysis by relays

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

The architecture ensures:

Plaintext never leaves the device.

No component blindly trusts another.

Metadata is minimized to the limit of technical feasibility.

OS hardening protects hardware, app protects cryptography.

Backend is replaceable & untrusted by design.
