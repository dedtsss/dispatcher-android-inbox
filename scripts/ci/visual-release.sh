#!/usr/bin/env bash
set -euo pipefail
# Сценарий предназначен только для выделенного Android runner этого приложения.
[ "${GITHUB_ACTIONS:-}" = true ] && [ "${GITHUB_REPOSITORY:-}" = dedtsss/dispatcher-android-inbox ]
adb install release/catching-0.6.7-release.apk
adb shell svc wifi disable
adb shell svc data disable
adb shell dumpsys package ru.bruce.dispatcherinbox > visual/installed-package.txt
for width in normal narrow; do
  if [ "$width" = narrow ]; then adb shell wm size 864x1920; fi
  adb shell am force-stop ru.bruce.dispatcherinbox
  adb shell am start -W -n ru.bruce.dispatcherinbox/.MainActivity
  sleep 3
  adb shell uiautomator dump /sdcard/window.xml
  adb pull /sdcard/window.xml "visual/logo-main-$width.xml"
  adb exec-out screencap -p > "visual/logo-main-$width.png"
  adb shell am start -W -a android.intent.action.ASSIST -n ru.bruce.dispatcherinbox/.assistant.AssistantEntryActivity
  sleep 3
  adb shell input keyevent 4
  sleep 2
  adb shell uiautomator dump /sdcard/window.xml
  adb pull /sdcard/window.xml "visual/logo-assistant-$width.xml"
  adb exec-out screencap -p > "visual/logo-assistant-$width.png"
done
adb shell wm size reset
python3 - <<'PY'
import xml.etree.ElementTree as ET
from pathlib import Path
for p in Path('visual').glob('logo-*.xml'):
    labels={n.get('content-desc') for n in ET.parse(p).iter('node')}
    assert 'Catching' in labels, p
    if 'assistant' in p.name:
        assert {'Закрыть ассистент', 'Настройки ассистента'} <= labels, p
PY
