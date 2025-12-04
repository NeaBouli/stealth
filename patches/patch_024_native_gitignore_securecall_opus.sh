#!/bin/bash
set -e

echo "== patch_024: add native/.gitignore for securecall_opus workspace =="

mkdir -p native

# Falls .gitignore schon existiert und der Eintrag vorhanden ist → nichts tun
if [ -f native/.gitignore ] && grep -q "securecall_opus/" native/.gitignore; then
  echo "[INFO] native/.gitignore already contains securecall_opus/ entry"
else
  cat <<'GIT' >> native/.gitignore
# Local Opus/NDK workspace (not committed)
securecall_opus/
GIT
  echo "[OK] Updated native/.gitignore with securecall_opus/ ignore rule"
fi

echo "== patch_024 done =="
