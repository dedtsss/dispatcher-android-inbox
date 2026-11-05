# Catching: контрольная точка 2026-11-05

Продолжение Issue #7 / PR #8, ветка `issue-7-drive-inbox`. PR #8 не объединять до физической и Google OAuth приёмки. Второй исполнитель не запущен.

## Исходное состояние

Рабочее дерево было чистым на `dc1e8ee`; выполнен fast-forward до `cfb1aa3923579b4cd80978593f8174928ad8b6f5`. Репозиторий публичный. В `main/updates/update.json` опубликована 0.6.2/code8, SHA-256 `ff15fec3f817711fb18bab5a04d3fa56aee2a2971f0722e008bedd6d99ee0836`. Сертификат SHA-256 `873bc4580e6d0387a37a155f5c6a6adba71d10408f53fd9d1e8834d54045a645`.

Запуск [42037542998](https://github.com/dedtsss/dispatcher-android-inbox/actions/runs/42037542998): подписанная сборка SUCCESS; Android 6/7, ошибка `CaptureIntegrationTest.captureOfflineAndSystemEntries:62`. Исторические install-over, включая опубликованную 0.6.2, прошли; process restart после общего набора ещё не выполнялся из-за его ошибки.

## Наблюдения при диагностике

Артефакт `проверки-на-Android` (13419982968) скачан в `/tmp/catching-run42037542998`; точные APK того запуска — `/tmp/catching-exact42037542998`. В артефакте нет hierarchy или снимка после попытки ввода. `01-main.png` сделан перед открытием поля.

В logcat: кнопка «Текст» нажата в `(320,1014)` в 13:53:06.622, затем показана IME. В 13:53:08.031 UIAutomator кликает выбранное `EditText` в `(540,65)`, в 13:53:08.107 выполняет ACTION_SET_TEXT с ASCII; ожидаемый текст не появляется. Замена Unicode на ASCII причину не устранила. Сообщение `grep -q OK` — следствие ошибки теста, не первопричина.

Для проверки выбранного accessibility node и координат запущено локальное воспроизведение на точном APK. Первый эмулятор завершился при дефиците памяти хоста, второй запущен с меньшей памятью. Физический телефон не использовался.

## Ограничения выпуска

Если меняются исходники приложения относительно проверяемой 0.6.3/code9, поднять версию до 0.6.4/code10. Изменённый APK не выдавать за прежнюю сборку. Публикация prerelease и переключение manifest разрешены только после полного зелёного device CI, process restart и install-over; SHA-256 и сертификат сверить с точным опубликованным APK.

Draft PR #9 содержит экономию переноса APK между job. Не переносить его устаревшие файлы приложения поверх PR #8. Drive OAuth `APIStatus=8 INTERNAL_ERROR` не диагностирован; Drive E2E не подтверждён.

## Подготовленная проверка причины

В тесте ввод теперь направлен свежему `EditText` пакета Catching с подтверждённым фокусом; повторный координатный click убран. До и после ввода сохраняются screenshot и hierarchy, а список кандидатов EditText (package/focus/bounds/text) попадает в logcat. Проверки видимого текста после ACTION_SET_TEXT, после закрытия IME и сохранения в Room оставлены. Это пока проверяемая гипотеза о сбое автоматизации, не окончательное доказательство причины.

Из PR #9 перенесён только workflow с общей рабочей областью build/device; app-файлы PR #9 не переносились. Исторические APK берутся из run 42037542998 с прежними проверками source/SHA-256 вместо пяти повторных сборок. Обычный запуск не загружает дубликаты APK transport/history в artifacts; diagnostics — 3 дня. После process restart добавлен отдельный повтор install-over опубликованной 0.6.2. Исходники приложения пока совпадают с `cfb1aa3`, версия остаётся 0.6.3/code9.

Локальная проверка: 31 unit-тест, 0 failures; lintDebug — 0 ошибок, 41 предупреждение и 1 подсказка; assembleDebug — успешно. На хосте с заполненным swap процесс Gradle остановлен после этих проверок; оставшийся assembleDebugAndroidTest выполнен отдельным процессом с Xmx512m и успешно завершён за 40 секунд. YAML, bash-сценарий install-over и 14 встроенных shell-шагов workflow проверены синтаксически, git diff --check пройден. Контрольная точка в PR: https://github.com/dedtsss/dispatcher-android-inbox/pull/8#issuecomment-6661794883 .

## Уточнённая причина и исправление 0.6.4/code10

[Диагностический run 42054956393](https://github.com/dedtsss/dispatcher-android-inbox/actions/runs/42054956393) снова дал 6/7. Гипотеза, что достаточно сфокусировать правильный узел в тесте, **не подтвердилась**. Его артефакт скачан в `/tmp/catching-evidence42054956393`.

`01-before-input.xml` и `01-after-input.xml` содержат единственный EditText пакета `ru.bruce.dispatcherinbox`, focused=true, enabled=true, bounds `[53,0][1027,131]`, text пуст до и после ACTION_SET_TEXT. На обоих screenshots видна лишь нижняя часть рамки у статус-бара; основная область поля и остальной контент уехали вверх при появлении IME. Это дефект обработки окна/viewport полноэкранного Capture, не Unicode, не чужой узел и не grep.

В MainActivity не задан windowSoftInputMode, а Compose самостоятельно обрабатывает IME через imePadding и прокрутку поля; родительский Scaffold padding применён без consumeWindowInsets. Исправление явно выбирает `adjustResize` для MainActivity и потребляет уже применённые inset-поля Scaffold перед дочерним Capture. [Документация Android по insets](https://developer.android.com/develop/ui/compose/system/insets-ui), [Scaffold и consumeWindowInsets](https://developer.android.com/develop/ui/compose/system/material-insets).

Поскольку меняется приложение, версия поднята до **0.6.4/code10**. Тест дополнительно проверяет, что поле полностью находится ниже status bar и выше IME, а видимая высота составляет хотя бы 100dp. В доказательства добавляется dumpsys window в момент ввода. Проверки видимого текста и реального сохранения сохранены. До зелёного CI этот APK не публикуется и manifest не меняется.

Локально для 0.6.4: unit — 31/31; lintDebug — 0 ошибок; assembleDebug и assembleDebugAndroidTest — успешно. Первый packageDebug завершился без детальной причины, повтор со stacktrace успешно завершил оба APK за 51 секунду без изменения кода. YAML/bash и git diff --check пройдены. Уточнение причины опубликовано в PR: https://github.com/dedtsss/dispatcher-android-inbox/pull/8#issuecomment-6662163014 .
