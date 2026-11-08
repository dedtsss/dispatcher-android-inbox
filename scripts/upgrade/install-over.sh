#!/usr/bin/env bash
set -euo pipefail
# Только выделенный эмулятор: очистка между независимыми историческими сценариями.
# Исторический debug содержит фиктивный отправитель; блокируем его до первого старта.
adb shell svc wifi disable
adb shell svc data disable
sleep 3
mkdir -p upgrade-evidence
runner=ru.bruce.dispatcherinbox.test/androidx.test.runner.AndroidJUnitRunner
for label in ${UPGRADE_LABELS:-v1 v2 v060 v061 v062 v064}; do
  adb uninstall ru.bruce.dispatcherinbox >/dev/null 2>&1 || true
  adb uninstall ru.bruce.dispatcherinbox.test >/dev/null 2>&1 || true
  adb install history-apk/"$label"/app-debug.apk
  adb install history-apk/"$label"/app-debug-androidTest.apk
  timeout 120s adb shell am instrument -w -e class ru.bruce.dispatcherinbox.HistoricalSeedTest "$runner" | tee "upgrade-evidence/$label-seed.txt"
  grep -q 'OK (1 test)' "upgrade-evidence/$label-seed.txt"
  adb shell am force-stop ru.bruce.dispatcherinbox
  adb install -r history-apk/"$label"/app-release.apk
  adb shell am start -W -n ru.bruce.dispatcherinbox/.MainActivity
  sleep 3
  adb shell pidof ru.bruce.dispatcherinbox
  if [ "$label" = v2 ]; then
    adb install -r history-apk/v060/app-release.apk
    adb shell am start -W -n ru.bruce.dispatcherinbox/.MainActivity
    sleep 5
    adb logcat -d -s AndroidRuntime > upgrade-evidence/v2-to-060-crash.txt
    grep -q 'Room cannot verify the data integrity' upgrade-evidence/v2-to-060-crash.txt
    adb logcat -c
  fi
  adb shell am force-stop ru.bruce.dispatcherinbox
  adb install -r apk/release/app-release.apk
  adb install -r apk/androidTest/debug/app-debug-androidTest.apk
  duration=false; drive=false
  if [ "$label" = v2 ] || [ "$label" = v061 ] || [ "$label" = v062 ] || [ "$label" = v064 ]; then duration=true; fi
  if [ "$label" = v060 ] || [ "$label" = v061 ] || [ "$label" = v062 ] || [ "$label" = v064 ]; then drive=true; fi
  timeout 120s adb shell am instrument -w -e class ru.bruce.dispatcherinbox.MigrationTest -e historicalDuration "$duration" -e historicalDrive "$drive" "$runner" | tee "upgrade-evidence/$label-verify.txt"
  grep -q 'OK (1 test)' "upgrade-evidence/$label-verify.txt"
  adb shell am start -W -n ru.bruce.dispatcherinbox/.MainActivity
  sleep 5
  adb shell pidof ru.bruce.dispatcherinbox > "upgrade-evidence/$label-pid.txt"
  adb shell dumpsys package ru.bruce.dispatcherinbox > "upgrade-evidence/$label-package.txt"
  adb logcat -d -s AndroidRuntime > "upgrade-evidence/$label-logcat.txt"
done
# Подготовка независимых capture-тестов после завершения upgrade-проверок.
adb uninstall ru.bruce.dispatcherinbox
adb uninstall ru.bruce.dispatcherinbox.test
