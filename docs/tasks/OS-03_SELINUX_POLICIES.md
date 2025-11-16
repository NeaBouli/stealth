# OS-03 – SELinux Policies für GHOSTOS BlackRoot

## Ziel
Erstellung eines minimalen SELinux-Policysets, das:

- SecureCall maximale Rechte gewährt
- allen anderen Apps den Zugriff auf Kamera/Mikro/Bluetooth/Netzwerk verweigert
- Systemdienste strikt trennt

## Key Policies

### 1. securecall_app.te
- allow securecall_app audio_device:rw;
- allow securecall_app camera_device:rw;
- deny securecall_app to run shell exec

### 2. default_app.te
- deny camera
- deny mic
- deny network (optional)
- deny clipboard

### 3. system_server.te
- nur minimal erforderliche Rechte
- Logging strikt reduzieren

### 4. Additional Contexts
- /dev/audio restricted
- /dev/video0 restricted
- /dev/radio locked

## Deliverables
- komplette policy-Dateien unter:
  rom_ghostos/selinux/
- Dokumentation: SELINUX_NOTES.md

## Tests
- policy compiles
- SecureCall kann Mic+Camera verwenden
- alle anderen Apps: denied
- keine Policy Loops in dmesg

