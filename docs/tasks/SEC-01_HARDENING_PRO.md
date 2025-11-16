# SEC-01 – Hardening & Anti-Manipulation (Pro-Version)

## Ziel
Erweiterter Schutz gegen Manipulation und Angriffe auf App-Ebene:
- Root/Magisk Detection
- Emulator Detection
- Hooking Detection (Frida/Xposed)
- Debugger Detection
- App Signature Validation

## Umfang

### 1. Root Detection
- Check: su binary
- Check: bekannte root-Pfade
- Check: Magisk-Installationsartefakte
- Check: SELinux Modus

### 2. Emulator Detection
- Check: ro.hardware, ro.kernel.qemu
- Check: fehlende Sensoren
- Check: ungewöhnliche CPU-Modelle

### 3. Hooking Detection
- Frida-Server Portscan (27042/27043)
- typische Native Libraries (frida-gadget)
- verdächtige Sockets
- verdächtige Prozesse

### 4. Debugger Detection
- isDebuggerConnected()
- ptrace (native check)

### 5. Signature Validation
- Hash der eigenen APK verifizieren
- Abbruch bei Abweichungen

## Policy-Verhalten (Pro-Version)
- Root: Warnung oder Block (abhängig von Policy)
- Emulator: immer Block
- Hooking: sofortiger Block
- Debugger: sofortiger Block

## Tests
- App auf normalem Gerät → OK
- App unter Emulator → Block
- App mit Frida → Block
- App mit Debugger → Block

