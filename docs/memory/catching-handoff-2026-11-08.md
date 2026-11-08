# Catching: продолжение физической приёмки 08.11.2026


## Итог — опубликован предварительный выпуск 0.6.5/code11

[Скачать проверенный APK и снимки](https://github.com/dedtsss/dispatcher-android-inbox/releases/tag/v0.6.5). **Drive OAuth на физическом телефоне остаётся BLOCKED_OAUTH_CONFIG / НЕ ПРОВЕРЕН.** Причина кода 8 не установлена. PR #8 OPEN, не объединён; требуется project ID/разрешённый Cloud Console и физическая приёмка по списку ниже. Выпуск исправляет оформление и расширяет диагностику, не объявляет исправленным Google OAuth.

- Исходники APK/tag v0.6.5: `7dfacb2cda7a541ad6854b935f97da84b9fce334`. [CI 42431870261](https://github.com/dedtsss/dispatcher-android-inbox/actions/runs/42431870261) SUCCESS: **31/31 unit**, lint, **8/8 Android**, **restart 1/1**, шесть историй install-over, дополнительный install-over опубликованной 0.6.4 после restart. Очередь, Room v3, метаданные, длительность, checkpoint и оригиналы/хеши проверены. Физический телефон не использовался.
- Точный release artifact `13598107097`, ZIP SHA-256 `25cb1694e52d6e80382b22d2158cb8bc195820a5b4ea41fcb5e1082f5ff0d462`. Подтверждены дайджест ZIP и SHA256SUMS обоих APK. Signed SHA-256 `6b11206e9203e4a6ceff7b6b71160ae56186c974cc928ad04e6d691f58383748`; unsigned `b1f583fc471256b7abd8c76112d1f858f82267c378313e305a8dc63667906058`.
- CI проверил package `ru.bruce.dispatcherinbox`, 0.6.5/code11, v2/v3 подпись и прежний сертификат SHA-256 `873bc4580e6d0387a37a155f5c6a6adba71d10408f53fd9d1e8834d54045a645`. Локально JVM/SDK/сборщики и проверки APK через SDK не запускались: подтверждение подписи опубликованного APK основано на CI и побайтовом совпадении скачанного публичного файла с точным проверенным артефактом.
- Полный Android evidence третьего CI не выгружен: сохранённый предохранитель PR #9 остановил прогноз `287482848 > 280000000` Б. Release artifact и unit reports сохранены. Предел не повышался, чужие artifacts не удалялись.
- Для доказательств выполнен отдельный [visual-only CI 42432613702](https://github.com/dedtsss/dispatcher-android-inbox/actions/runs/42432613702), SUCCESS на `685ac4e`, **без компиляции**. Он скачал точный artifact 13598107097, проверил ZIP/source/SHA256SUMS и установил готовый signed release в Android. Артефакт снимков `13598411138`, digest `632a200382ba0d3c8f053da232986e5e7482fb1ffa3538f163939a04fc981f9f`. Все четыре снимка просмотрены. При обычной и узкой ширине оригинал целиком, надпись читается, кнопки панели свободны. Код app между 7dfacb2 и 685ac4e не менялся.
- Создан prerelease `v0.6.5`, release ID `437953208`, 12 assets с проверенными серверными SHA-256. APK и update.json скачаны без авторизации; APK побайтно совпал с CI, manifest совпал с проверенными package/version/hash/cert. Тег указывает на 7dfacb2.
- Только после этого публичный `main/updates/update.json` переключён коммитом `4885325d61dd5ad7c9686163b6a651b9175b5e9b`. Скачанный raw manifest полностью совпал с проверенным release manifest. Проверка updater UI на физическом телефоне пока НЕ ВЫПОЛНЕНА; проверки updater в CI прошли. Предыдущая 0.6.4 сохранена.

Постоянные визуальные доказательства в выпуске:

| Экран | Обычная ширина | Узкая ширина |
|---|---|---|
| Главный | [PNG](https://github.com/dedtsss/dispatcher-android-inbox/releases/download/v0.6.5/logo-main-normal.png) | [PNG](https://github.com/dedtsss/dispatcher-android-inbox/releases/download/v0.6.5/logo-main-narrow.png) |
| Системный ассистент | [PNG](https://github.com/dedtsss/dispatcher-android-inbox/releases/download/v0.6.5/logo-assistant-normal.png) | [PNG](https://github.com/dedtsss/dispatcher-android-inbox/releases/download/v0.6.5/logo-assistant-narrow.png) |

Всего три сборочных CI (два первых выявили ошибки новых тестов геометрии, причины ниже) и один прогон снимков готового APK без сборки. Рабочие VPS, предохранители и постоянные службы не менялись. Следующий шаг: владелец предоставляет project ID/штатный доступ либо проверяет Cloud Console по четырём пунктам ниже, затем физический вход и реальные оригиналы в Catching/Inbox без потери очереди. Не подменять эту проверку эмулятором или Drive-коннектором.

## Исходная точка и ограничения

Единственный исполнитель, без подчинённых агентов. Локальная чистая ветка `issue-7-drive-inbox` исходно на `9b4f9d93719810a5e551eb3b47d278c272fbb583`; remote `4893563afd5993746d8d5b889b75fa5a5f1dae67`, выполнен fast-forward с сохранением объединённого PR #9 и ограничений артефактов. Новые авторские публикации по-русски. Прочитаны текущие §7, README памяти, относящиеся решения и политика VPS, AGENTS, прежний handoff и комментарии PR #8, включая восстановление маршрута 6715084228.

Все сборки, JVM/Gradle и Android-проверки выполняются только Actions этого приложения. Локально — редактирование, git diff --check, YAML и bash -n; JVM/SDK/эмулятор не запускались. Предохранители и службы не менялись. PR #8 не объединять до физической и Google OAuth приёмки.

## Подготовлено 0.6.5/code11

- Точный `catching_logo.png` сохранён. Ограничена ширина всей композиции: главный экран 208dp, панель 184dp; Fit сохраняет пропорции. Белая подложка обеспечивает контраст тёмной надписи. X/шестерёнка, ввод и сохранение не изменены.
- Android-регрессия снимает оба экрана при обычной и узкой ширине, проверяет размеры и отсутствие пересечения с кнопками. Прежние проверки ввода/сохранения продолжают работать.
- Диагностика OAuth сохраняет этап и сводку при пересоздании Activity, добавляет версию приложения/Android/Google Play Services и код доступности GMS. Аккаунты, токены и Intent не выводятся.
- CI экспортирует точный unsigned release и подписанный тем же постоянным ключом QA APK, сверяет сертификат, package/version и записывает source SHA. Публикация артефакта остаётся за зелёными Android QA и бюджетом PR #9.
- Install-over дополнен опубликованной 0.6.4/code10 с точным SHA-256 `2cda0f95b20c43d18a711ab952a7283e038aed128ac26518a8fc1439da0cf9d0`. Подготовка данных использует прежнюю фикстуру 0.6.2, затем настоящий release 0.6.4 открывает их перед обновлением. Повтор после process restart также использует 0.6.4. Это проверка эмулятора, не физическая приёмка.

## Google OAuth: граница доказанного

Физический результат 0.6.4: stage=resolution, resultCode=0, ApiException 8 INTERNAL_ERROR. Это ошибка до Drive HTTP API. Из неё нельзя вывести отсутствие клиента или выключенный Drive API. `hasResolution=true/hasAccessToken=false/driveFileGranted=false` — сводка предварительного authorize, а не успешно разобранного результата разрешения.

По [официальному руководству](https://developer.android.com/identity/authorization) текущая последовательность authorize → PendingIntent → getAuthorizationResultFromIntent соответствует AuthorizationClient. Используется play-services-auth 21.4.0; доказательства, что обновление библиотеки устранит код 8, нет. [CommonStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes) различает внутреннюю ошибку 8 и ошибку конфигурации 10. Смена SDK/добавление Web client ID без доказательств не выполнялись. [drive.file](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) оставлен минимальным scope; оригиналы и Room/очередь не менялись.

Облачная регистрация **не проверена**: gcloud и локальные application_default_credentials/config_default отсутствуют; GitHub secrets содержат только четыре параметра подписи. Коннектор метаданных сейфа вернул INVALID. Штатный `codex-vault exists` для CATCHING_GOOGLE_CLOUD_PROJECT_ID, GOOGLE_CLOUD_PROJECT_ID, GOOGLE_APPLICATION_CREDENTIALS вернул missing. Это не доказывает отсутствия проекта/прав у владельца или других секретов. Значения секретов не запрашивались. Связанные Drive-коннекторы не дают административных прав Cloud Console и не заменяют OAuth Android.

Минимум для владельца в существующем проекте Google Cloud:

1. Указать project ID и предоставить разрешённый доступ к Google Auth Platform/Cloud Console либо самому сверить Android OAuth client: package `ru.bruce.dispatcherinbox`, SHA-1 `A6:C7:D7:85:0C:80:50:A4:B1:51:75:ED:34:A1:7B:7E:D8:80:98:11`. Не создавать дубликат проекта наугад и не передавать приватный ключ подписи.
2. В том же проекте подтвердить Google Drive API enabled, Branding и Audience/режим публикации; для Testing проверить разрешённого пользователя телефона приватно, без email в GitHub; scope `https://www.googleapis.com/auth/drive.file` в Data Access.
3. Повторить подключение на физическом телефоне с новой безопасной диагностикой GMS. При отсутствии/неактуальности Play Services исправить состояние на устройстве; один код 8 не доказывает эту причину.
4. После авторизации выгрузить текст и оригинальное вложение непосредственно в Catching/Inbox, сверить содержимое/хеш, перезапустить приложение и повторить очередь без дублей/потери локальных записей. До этого Drive E2E — НЕ ПРОВЕРЕН.

На подготовительном этапе публичный update.json и выпуск 0.6.4 сохранялись до проверки нового точного артефакта. Итог после ворот зафиксирован в начале документа.

## Первый CI и уточнение теста

[42430500149](https://github.com/dedtsss/dispatcher-android-inbox/actions/runs/42430500149), head `60f7e63`: unit/lint/build и шесть install-over, включая опубликованную 0.6.4, прошли. Android 7/8: новая проверка ошибочно ожидала наружную высоту 56dp, тогда как accessibility Image возвращает внутреннюю высоту 40dp после padding. Bounds `[288,132][792,237]` при density 2.625 точно соответствуют 192×40dp. Снимки главного экрана и панели просмотрены: оригинал целиком, надпись контрастна, кнопки свободны. Исправляется ожидание теста с точной проверкой высоты, снимок/hierarchy сохраняются до assertions. Код приложения и версия при этом не меняются. Restart после неуспешного набора не выполнялся; APK не выгружался/не публиковался, updater не менялся.

[Второй CI 42431214560](https://github.com/dedtsss/dispatcher-android-inbox/actions/runs/42431214560), head `c2b30f6`: вновь 7/8, теперь геометрия логотипа главного экрана и панели прошла. Проверка 44dp ошибочно измеряла Icon вместо IconButton. XML: иконка X `[766,912][829,975]` (24dp), её clickable-родитель `[735,881][861,1007]` (48dp); шестерёнка аналогично. Исправлен выбор clickable-родителя с сохранением требований к площади и отсутствию пересечения. Приложение не менялось. Включён третий обоснованный прогон после этих конкретных фактов; прежние результаты не скрыты. Повтор метаданных сейфа с limit=50 вернул UNAVAILABLE; доступ Cloud Console по-прежнему не подтверждён.
