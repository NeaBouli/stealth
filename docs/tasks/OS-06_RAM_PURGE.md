# OS-06 – RAM Purge Mechanism

## Ziel
Sicherstellen, dass keinerlei Schlüsselmaterial oder Audioframes im Speicher verbleiben.

## Anforderungen

### 1. Zeroize Hooks
- SecureCall-App:
  - Session-Keys überschreiben
  - Frame-Puffer sofort löschen
- OS:
  - Kernel-memory wipe nach Prozessende

### 2. Purge Trigger
- Call End
- App Kill
- OS Standby
- Policy-Verdachtsfall (Root, Hooking, Funkzellenanomalien)

### 3. Implementierung
- Kernel: memory zeroing on free
- Userland: secure memsets + FFI Calls
- optional: MTE-enabled devices nutzen (Memory Tagging)

## Deliverables
- RAM_PURGE.md
- patched kernel allocator

## Tests
- kein Schlüsselmaterial in „strings /proc/<pid>/mem“
- keine Reste in Audio-Puffer
- Heap nach Call vollständig gelöscht

