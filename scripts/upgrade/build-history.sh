#!/usr/bin/env bash
set -euo pipefail
# Не меняем исторические модели и Room: БД создаёт настоящий старый код.
mkdir -p history-apk
# Данные создаёт старая 0.6.2, затем их открывает настоящий опубликованный APK 0.6.4.
prepare_published_064() {
  mkdir -p history-apk/v064
  cp history-apk/v062/app-debug.apk history-apk/v062/app-debug-androidTest.apk history-apk/v064/
  gh release download v0.6.4 --repo dedtsss/dispatcher-android-inbox --pattern catching-0.6.4-release.apk --dir "$RUNNER_TEMP/original-064" --clobber
  cp "$RUNNER_TEMP/original-064/catching-0.6.4-release.apk" history-apk/v064/app-release.apk
  echo '2cda0f95b20c43d18a711ab952a7283e038aed128ac26518a8fc1439da0cf9d0  history-apk/v064/app-release.apk' | sha256sum -c -
  "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs history-apk/v064/app-release.apk > history-apk/v064/certificate.txt
  grep -qi '873bc4580e6d0387a37a155f5c6a6adba71d10408f53fd9d1e8834d54045a645' history-apk/v064/certificate.txt
}
# При изменении только текущего приложения можно повторно взять неизменённые подписанные фикстуры.
if [ -n "${HISTORY_RUN_ID:-}" ] && gh run download "$HISTORY_RUN_ID" --repo dedtsss/dispatcher-android-inbox -n исторические-APK -D history-apk; then
  for entry in v1:2c42012c9ba655f74699bf9b7928b5f75c20b006 v2:7f7dbc7c9bc39d69a65baa74a6f51f4ae813c10d v060:87b95fe7a172d56702e48410febd7c7a2c601765 v061:f048ee469b8267500800daf8a629bee0c70cd38a v062:5a71a6c3ad7e31e5e971f792e12d29f5e73f7de9; do
    label=${entry%%:*}
    ref=${entry#*:}
    test "$(cat "history-apk/$label/source.txt")" = "$ref"
    sha256sum -c "history-apk/$label/SHA256SUMS"
    "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify "history-apk/$label/app-release.apk"
  done
  echo 'ad71d7586c356d902f426847afb02002e132cfb0795f5b5fd5a3fc5c2a19a6bd  history-apk/v060/app-release.apk' | sha256sum -c -
  echo 'a0c6c6e6dd822484efd690bda6e6a9bf58632ad6250fd6da1b943492d6611aa1  history-apk/v061/app-release.apk' | sha256sum -c -
  echo 'ff15fec3f817711fb18bab5a04d3fa56aee2a2971f0722e008bedd6d99ee0836  history-apk/v062/app-release.apk' | sha256sum -c -
  prepare_published_064
  exit 0
fi
if [ -n "${HISTORY_RUN_ID:-}" ]; then
  echo 'Архив исторических APK недоступен; собираем прежние фиксированные версии заново'
fi
for entry in v1:2c42012c9ba655f74699bf9b7928b5f75c20b006 v2:7f7dbc7c9bc39d69a65baa74a6f51f4ae813c10d v060:87b95fe7a172d56702e48410febd7c7a2c601765 v061:f048ee469b8267500800daf8a629bee0c70cd38a v062:5a71a6c3ad7e31e5e971f792e12d29f5e73f7de9; do
  label=${entry%%:*}
  ref=${entry#*:}
  dir="$RUNNER_TEMP/history-$label"
  git worktree add --detach "$dir" "$ref"
  # Только вспомогательный тест, приложение и схема остаются историческими.
  rm -rf "$dir/app/src/androidTest"
  mkdir -p "$dir/app/src/androidTest/java/ru/bruce/dispatcherinbox"
  cp scripts/upgrade/HistoricalSeedTest.kt "$dir/app/src/androidTest/java/ru/bruce/dispatcherinbox/"
  # В ранней v1 ещё не было зависимостей инструментальных тестов.
  cat >> "$dir/app/build.gradle.kts" <<'GRADLE'

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
GRADLE
  (cd "$dir" && ./gradlew assembleDebug assembleRelease assembleDebugAndroidTest --console=plain)
  mkdir -p "history-apk/$label"
  cp "$dir/app/build/outputs/apk/debug/app-debug.apk" "history-apk/$label/"
  cp "$dir/app/build/outputs/apk/release/app-release.apk" "history-apk/$label/"
  cp "$dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "history-apk/$label/"
  cp "$dir/app/build/generated/source/kapt/debug/ru/bruce/dispatcherinbox/data/InboxDatabase_Impl.java" "history-apk/$label/"
  if [ "$label" = v060 ]; then
    gh release download migration-fixtures-issue-7 --repo dedtsss/dispatcher-android-inbox --pattern catching-0.6.0-release.apk --dir "$RUNNER_TEMP/original-060" --clobber
    cp "$RUNNER_TEMP/original-060/catching-0.6.0-release.apk" "history-apk/$label/app-release.apk"
    echo 'ad71d7586c356d902f426847afb02002e132cfb0795f5b5fd5a3fc5c2a19a6bd  history-apk/v060/app-release.apk' | sha256sum -c -
  fi
  if [ "$label" = v061 ]; then
    gh release download v0.6.1 --repo dedtsss/dispatcher-android-inbox --pattern catching-0.6.1-release.apk --dir "$RUNNER_TEMP/original-061" --clobber
    cp "$RUNNER_TEMP/original-061/catching-0.6.1-release.apk" "history-apk/$label/app-release.apk"
    echo 'a0c6c6e6dd822484efd690bda6e6a9bf58632ad6250fd6da1b943492d6611aa1  history-apk/v061/app-release.apk' | sha256sum -c -
  fi
  if [ "$label" = v062 ]; then
    gh release download v0.6.2 --repo dedtsss/dispatcher-android-inbox --pattern catching-0.6.2-release.apk --dir "$RUNNER_TEMP/original-062" --clobber
    cp "$RUNNER_TEMP/original-062/catching-0.6.2-release.apk" "history-apk/$label/app-release.apk"
    echo 'ff15fec3f817711fb18bab5a04d3fa56aee2a2971f0722e008bedd6d99ee0836  history-apk/v062/app-release.apk' | sha256sum -c -
  fi
  printf '%s\n' "$ref" > "history-apk/$label/source.txt"
  "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "history-apk/$label/app-release.apk" > "history-apk/$label/certificate.txt"
  sha256sum history-apk/"$label"/*.apk > "history-apk/$label/SHA256SUMS"
done

prepare_published_064
