#!/bin/bash
set -e

echo "== patch_040: cleanup android media + ignore build artifacts =="

# 1) Remove leftover backup / java duplicates that should not be compiled
if [ -f client_android/app/src/main/java/com/securecall/app/MainActivity.java.bak_patch034 ]; then
  rm client_android/app/src/main/java/com/securecall/app/MainActivity.java.bak_patch034
  echo "[OK] Removed MainActivity.java.bak_patch034"
fi

if [ -f client_android/app/src/main/java/com/securecall/app/ghostnet/media/AudioPlaybackStub.java ]; then
  rm client_android/app/src/main/java/com/securecall/app/ghostnet/media/AudioPlaybackStub.java
  echo "[OK] Removed legacy AudioPlaybackStub.java"
fi

if [ -f client_android/app/src/main/java/com/securecall/app/ghostnet/media/MediaRouterInboundStub.java ]; then
  rm client_android/app/src/main/java/com/securecall/app/ghostnet/media/MediaRouterInboundStub.java
  echo "[OK] Removed legacy MediaRouterInboundStub.java"
fi

# Entferne nicht mehr benötigten Java-Stubs-Patch, falls vorhanden
if [ -f patches/patch_039_media_pipeline_java_stubs.sh ]; then
  rm patches/patch_039_media_pipeline_java_stubs.sh
  echo "[OK] Removed obsolete patch_039_media_pipeline_java_stubs.sh"
fi

# 2) .gitignore erweitern für Android-Build-Artefakte + local.properties
if [ ! -f .gitignore ]; then
  touch .gitignore
fi

ensure_ignored() {
  local PATTERN="$1"
  if grep -Fxq "$PATTERN" .gitignore; then
    echo "[INFO] .gitignore already has: $PATTERN"
  else
    echo "$PATTERN" >> .gitignore
    echo "[OK] Added to .gitignore: $PATTERN"
  fi
}

echo "[INFO] Updating .gitignore for Android artifacts..."
ensure_ignored "client_android/.gradle/"
ensure_ignored "client_android/build/"
ensure_ignored "client_android/app/.cxx/"
ensure_ignored "client_android/app/build/"
ensure_ignored "client_android/local.properties"

echo "== patch_040 done =="
