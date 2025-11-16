# ANDROID-04 – Policy Engine Implementierung

## Ziel
Eine zentrale Engine, die Sicherheitsregeln pro Produktlinie auswertet:

- Free
- Pro
- Premium
- GHOSTOS

## Umfang

### 1. Policy-Format
Empfohlen: JSON/YAML (build-time eingebettet)

Beispielstruktur:
{
  "root": "warn|block",
  "screen_recording": "warn|block",
  "developer_options": "warn|block",
  "untrusted_wifi": "warn|block",
  "imsi_catcher": "warn|block",
  "multi_hop": true|false
}

### 2. Komponenten
- PolicyLoader (liest JSON)
- PolicyEvaluator (liefert ALLOW/WARN/BLOCK)
- PolicyCache (Runtime)

### 3. Integration
- Security Monitor fragt Policy an
- UI setzt Warnungen um
- Call Control setzt BLOCK um

## Tests
- Free = nur WARN
- Pro = gemischt WARN/BLOCK
- Premium = strenger
- OS = maximal restriktiv

