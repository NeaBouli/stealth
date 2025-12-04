#!/bin/bash
set -e

echo "== patch_005: cleanup old patch_004 file =="

# Remove old, unused patch file if it exists
if [ -f patches/patch_004_developer_onboarding.sh ]; then
  rm patches/patch_004_developer_onboarding.sh
  echo "[OK] Removed patches/patch_004_developer_onboarding.sh"
else
  echo "[INFO] No old patch_004 file to remove."
fi

echo "== patch_005 done =="
