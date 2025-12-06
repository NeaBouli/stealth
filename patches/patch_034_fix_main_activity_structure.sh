#!/bin/bash
set -e

echo "== patch_034: fix MainActivity.java class structure (truncate trailing garbage) =="

python3 - <<'PY'
from pathlib import Path

path = Path("client_android/app/src/main/java/com/securecall/app/MainActivity.java")
txt = path.read_text(encoding="utf-8")

marker = "class MainActivity"
idx = txt.find(marker)
if idx == -1:
    raise SystemExit("ERROR: 'class MainActivity' not found in MainActivity.java")

# Finde erste '{' nach 'class MainActivity'
brace_start = txt.find("{", idx)
if brace_start == -1:
    raise SystemExit("ERROR: opening '{' for MainActivity not found")

depth = 0
close_idx = None

for i, ch in enumerate(txt[brace_start:], start=brace_start):
    if ch == "{":
        depth += 1
    elif ch == "}":
        depth -= 1
        if depth == 0:
            close_idx = i
            break

if close_idx is None:
    raise SystemExit("ERROR: could not find matching closing '}' for MainActivity")

# Optional: Backup anlegen
backup_path = path.with_suffix(".java.bak_patch034")
backup_path.write_text(txt, encoding="utf-8")

# Alles nach dem schließenden '}' der Klasse wegwerfen
new_txt = txt[:close_idx + 1] + "\n"

path.write_text(new_txt, encoding="utf-8")
print(f"[OK] Truncated MainActivity.java after index {close_idx}, backup at {backup_path}")
PY

echo "== patch_034 done =="
