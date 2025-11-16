# OS-02 – Kernel Hardening & Exploit Mitigation

## Ziel
Härtung des Kernels gegen lokale und Remote-Angriffe für GHOSTOS BlackRoot.

## Anforderungen

### 1. Entfernen unnötiger Kernel-Module
- Bluetooth
- NFC
- USB-OTG (optional)
- alte Filesystemtreiber
- Debug- und Tracing-Features

### 2. Aktivieren sicherheitsrelevanter Features
- SELinux Enforcing
- ExecShield / W^X
- Kernel Stack Canary
- Memory Tagging (für ARMv8.5+)
- Restrict Kernel Module Loading

### 3. Deaktivieren unsicherer Syscalls
- ptrace
- kexec_load
- perf_event_mlock_kB (abhängig von Build)

### 4. Netzwerk-Hardening
- IPv6 optional entfernen
- ICMP Rate Limit
- SYN Cookies aktiv
- Disable Reverse Path Filtering

### 5. App-Isolation
Kein Prozess außer SecureCall darf Mikrofon/Kamera öffnen:
- CAP_SYS_RESOURCE entziehen
- restriktive SELinux Contexts

## Deliverables
- modifizierte Kernel-Konfiguration (.config)
- Dokumentation aller Änderungen: rom_ghostos/KERNEL_HARDENING.md
- diff/patch Dateien

## Tests
- Kernel baut erfolgreich
- Mikrofon nur für SecureCall zugänglich
- keine Boot-Loops
- dmesg: keine „context denied“-Loops

