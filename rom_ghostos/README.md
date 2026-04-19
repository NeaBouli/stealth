# GHOSTOS BlackRoot – Hardened Secure OS

This directory contains all work on the hardened special-purpose operating system
"GHOSTOS BlackRoot", the highest security tier of the SecureCall ecosystem.

## Goals
- Complete OS hardening (kernel + system services)
- Minimal attack surface (no third-party apps)
- Exclusive focus on secure communication
- SecureCall as system app with elevated privileges
- No Google Services, no WebView, no Play Store

## Main Components
### 1. Kernel & System Hardening
- SELinux strict
- No debug interfaces
- Only essential system services active
- RAM purge after each session

### 2. Network Restrictions
- Kernel-level firewall
- LTE-only / no-GSM modes
- Disabled background connections
- Optionally disabled Wi-Fi/Bluetooth/NFC

### 3. SecureCall System Integration
- System app with exclusive microphone access
- GhostNet transport deeply integrated
- Device Owner functions unnecessary (OS takes control)

## Relevant Documents
- docs/SECURITY_DESIGN.md
- docs/ARCHITECTURE_OVERVIEW.md
- docs/tasks/ROM-01.md
