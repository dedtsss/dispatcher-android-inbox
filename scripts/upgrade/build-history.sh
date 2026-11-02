#!/usr/bin/env bash
set -euo pipefail
# Не меняем исторические модели и Room: БД создаёт настоящий старый код.
mkdir -p history-apk
for entry in v1:2c42012c9ba655f74699bf9b7928b5f75c20b006 v2:7f7dbc7c9bc39d69a65baa74a6f51f4ae813c10d v060:87b95fe7a172d56702e48410febd7c7a2c601765; do
  label=${entry%%:*}
  ref=${entry#*:}
  dir="$RUNNER_TEMP/history-$label"
  git worktree add --detach "$dir" "$ref"
  # Только вспомогательный тест, приложение и схема остаются историческими.
  rm -rf "$dir/app/src/androidTest"
  mkdir -p "$dir/app/src/androidTest/java/ru/bruce/dispatcherinbox"
  cp scripts/upgrade/HistoricalSeedTest.kt "$dir/app/src/androidTest/java/ru/bruce/dispatcherinbox/"
  (cd "$dir" && ./gradlew assembleDebug assembleRelease assembleDebugAndroidTest --console=plain)
  mkdir -p "history-apk/$label"
  cp "$dir/app/build/outputs/apk/debug/app-debug.apk" "history-apk/$label/"
  cp "$dir/app/build/outputs/apk/release/app-release.apk" "history-apk/$label/"
  cp "$dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "history-apk/$label/"
  cp "$dir/app/build/generated/source/kapt/debug/ru/bruce/dispatcherinbox/data/InboxDatabase_Impl.java" "history-apk/$label/"
  printf '%s\n' "$ref" > "history-apk/$label/source.txt"
  "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "history-apk/$label/app-release.apk" > "history-apk/$label/certificate.txt"
  sha256sum history-apk/"$label"/*.apk > "history-apk/$label/SHA256SUMS"
done
