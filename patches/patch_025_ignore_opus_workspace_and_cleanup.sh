#!/bin/bash
set -e

echo "== patch_025: ignore native/securecall_opus and cleanup old local patch =="

# 1) Ensure .gitignore exists and ignores native/securecall_opus/
if [ ! -f .gitignore ]; then
  cat <<'GIT' > .gitignore
# Ignore local Opus workspace (not part of repo)
native/securecall_opus/
GIT
  echo "[OK] Created .gitignore with native/securecall_opus/ ignore"
else
  if grep -q 'native/securecall_opus/' .gitignore; then
    echo "[INFO] .gitignore already ignores native/securecall_opus/"
  else
    printf '\n# Ignore local Opus workspace (not part of repo)\nnative/securecall_opus/\n' >> .gitignore
    echo "[OK] Appended native/securecall_opus/ to .gitignore"
  fi
fi

# 2) Remove old local-only patch if present
if [ -f patches/patch_024_ignore_local_opus_workspace.sh ]; then
  rm patches/patch_024_ignore_local_opus_workspace.sh
  echo "[OK] Removed patches/patch_024_ignore_local_opus_workspace.sh"
else
  echo "[INFO] No old patch_024_ignore_local_opus_workspace.sh to remove"
fi

echo "== patch_025 done =="
