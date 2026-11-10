# Catching: повторный разбор подключения Google Drive 08.11.2026

## Итог — предварительная 0.6.8/code14 доставлена, OAuth E2E ожидается

[Скачать APK, метаданные и доказательства](https://github.com/dedtsss/dispatcher-android-inbox/releases/tag/v0.6.8). Путь кнопки переработан и проверен протокольными тестами; **реальная физическая Google OAuth/Drive E2E-приёмка НЕ ПРОЙДЕНА**. Project ID/авторизованный Cloud Console не обнаружены. Код 8 не приписывается установленной облачной причине. Нужна проверка владельцем регистрации/Drive API/consent и физический вход с оригиналами Catching/Inbox. PR #8 OPEN, не объединён.

- Source/tag: `00d5130caa53c12effae3c473c297a12e923faa1`. [Финальный CI 42480873137](https://github.com/dedtsss/dispatcher-android-inbox/actions/runs/42480873137) **SUCCESS**: **35 unit, 0 ошибок/пропусков**, lint, **19/19 Android**, **restart 1/1**, семь install-over, строгая проверка старого Room до обновления и повтор настоящей опубликованной **0.6.5** после restart. В 19 входят 10 протокольных тестов с синтетическим backend и 9 прежних; это не реальный Google consent.
- Exact artifact **13617194510**, ZIP digest `3ef4af0b31e45ad7adcb32b8ead1b06ef1c6e938216a45019e504cf669629363`: скачан по ID, дайджест проверен, оба APK по SHA256SUMS совпали. Signed APK SHA-256 **`317503dbaed8694f7234586b74038820981d2266f8f88cd79a0419ac941724f8`**, unsigned `eec2ba53601512cb3def4d4c6424ec34c08f487017d79ef77ef7cc8b7fe1f9ff`.
- CI проверил package `ru.bruce.dispatcherinbox`, 0.6.8/code14, единственного signer и прежний SHA-256 сертификата **`873bc4580e6d0387a37a155f5c6a6adba71d10408f53fd9d1e8834d54045a645`**, SHA-1 `A6:C7:D7:85:0C:80:50:A4:B1:51:75:ED:34:A1:7B:7E:D8:80:98:11`. Локальные JVM/SDK/сборка/проверка APK через SDK не выполнялись: опубликованные байты сопоставлены с exact CI APK, чья подпись проверена в CI.
- Compact proof artifact **13617363555**, digest `da802f97f94a54843a4ac01764a4aef65e2e9e1fb6eea94b471e9d24a34d4ce3`, unit reports **13617792200**, digest `aa83c9257bcb53520a06bb49259a2b93f3e28a8d6b62cdb56b3c5bab6e67423c`: оба скачаны, дайджесты проверены. Старый writer подтверждён сохранённым logcat `oldDebugWriterConfirmed=true; queuedToSynced=true; fakeRemoteIdConfirmed=true; autoSync=false`. Принудительный manual-probe локализует старый fake writer; точный timing первоначального падения не восстановлен. Отмена прежних работ и строгие проверки фаз устраняют конкурирующую запись до миграции, не меняя ожидаемые queued/error/synced.
- Принятый прозрачный логотип и ассистент побайтно в исходниках прежние (`git diff 54ea3d2..00d5130` для capture/resources/assistant пуст). Четыре CI-снимка просмотрены; статусы «Отправлено» на синтетических записях — фикстуры, не Google Drive. Доказательства постоянно приложены к выпуску, в том числе точный ZIP `android-evidence-0.6.8.zip`.
- Промежуточная 0.6.7/code13 не публиковалась в Releases/updater. Её exact artifact архивирован с проверенным digest и metadata, затем удалён из Actions только по доказанной необходимости места для изменённого финального APK. Владелец ранее удалил два старых failed artifacts; других удалений не было. Исторический архив 13420616150, рабочие 0.6.5 и Releases сохранены. После финального CI активные artifacts **277401101/280000000 Б**. Полный общий Android evidence закономерно остановлен бюджетом, компактные доказательства и exact APK сохранены. Бюджеты/предохранители не менялись.
- Prerelease **v0.6.8**, release ID **438243615**, 13 assets. Все серверные SHA-256 проверены; все 13 файлов скачаны без авторизации и сверены с дайджестами. Публичный APK побайтно совпал с exact CI APK, тег указывает на 00d5130. Только после проверки опубликованного APK public `main/updates/update.json` переключён коммитом **75326f0c777b4e06196dd7f053490bba9cc8e684**; canonical raw manifest скачан и полностью совпал с release/update.json.
- Исследование по search-default-policy сохранено также в [памяти agent-dispatch](https://github.com/dedtsss/agent-dispatch/blob/main/docs/memory/catching-oauth-flow-review-2026-11-08.md), индексе и решениях. Два осмысленных CI этого продолжения, оба SUCCESS. Ни новых задач/исполнителей, ни локальной компиляции, ни изменений постоянных служб.

| Принятый экран | Обычная ширина | Узкая ширина |
|---|---|---|
| Главный | [PNG](https://github.com/dedtsss/dispatcher-android-inbox/releases/download/v0.6.8/logo-main-normal.png) | [PNG](https://github.com/dedtsss/dispatcher-android-inbox/releases/download/v0.6.8/logo-main-narrow.png) |
| Ассистент | [PNG](https://github.com/dedtsss/dispatcher-android-inbox/releases/download/v0.6.8/logo-assistant-normal.png) | [PNG](https://github.com/dedtsss/dispatcher-android-inbox/releases/download/v0.6.8/logo-assistant-narrow.png) |

Следующий шаг владельца: установить поверх 0.6.5 без удаления приложения; выбрать Google-аккаунт, подтвердить drive.file и проверить настоящие оригиналы/хеши/повтор очереди. При отказе сначала сверить существующий Cloud project/Android client/API/consent по списку в конце документа; не менять signer/package/SDK наугад. Разрешённого маршрута к физическому телефону в текущих tools нет, проверка телефона/его updater для 0.6.8 не заявляется выполненной.

## Границы и baseline

Продолжение Issue #7 / PR #8, один прежний исполнитель, без подчинённых агентов. Baseline local/remote — чистая `issue-7-drive-inbox`, `54ea3d2a728a461224214ea1f26a76456a9237b5`. §7, AGENTS и search-default-policy перечитаны. Прозрачное оформление **принято владельцем и не меняется**. Промежуточная версия — **0.6.7/code13**, финальная — **0.6.8/code14**, стабильные package/ключ/Room/оригиналы/очередь сохраняются. Все сборки/JVM/SDK/эмуляторы — только Actions приложения.

Управляющий чат удалил два согласованных failed release artifacts: API подтвердил 404 для 13430232908 и 13430180381. Активный объём до нового CI — 246517060 Б, предел 280000000 Б. Дополнительных удалений и изменения бюджета нет. Исторический архив 13420616150, рабочие 0.6.5 artifacts и Releases сохранены. До всех фактических ворот публичные 0.6.5 и updater не меняются.

## Исследование и выбор

Прочитаны все шесть заданных первоисточников Google:

- [Authorization guide](https://developer.android.com/identity/authorization): authorize → PendingIntent при необходимости → getAuthorizationResultFromIntent; токен вновь получается через authorize. Авторизация данных и аутентификация личности различаются.
- [Credential Manager SIWG](https://developer.android.com/identity/sign-in/credential-manager-siwg): ID-токен относится ко входу/идентификации, не заменяет Drive access token.
- [AuthorizationClient](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient): при отсутствии грантов возвращается разрешение; ошибка разбора результата передаётся ApiException. clearToken — для инвалидированного токена, revokeAccess — для явного отзыва доступа, не универсальные действия при любой ошибке.
- [Android OAuth client](https://support.google.com/cloud/answer/15549257?hl=en): Android-регистрация требует package и SHA-1 установленного релиза; нативному приложению не нужен встроенный client secret.
- [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth): минимальный drive.file. Используется тот же точный URI, что у DriveScopes.DRIVE_FILE; библиотека полного Drive SDK ради одной константы не добавляется.
- [CommonStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes): 8 INTERNAL_ERROR не устанавливает отсутствующий клиент/consent/API как причину.

Дополнительно прочитаны [AccountManager.newChooseAccountIntent](https://developer.android.com/reference/android/accounts/AccountManager), [официальные identity-samples](https://github.com/android/identity-samples), реализация AuthorizationClient в [flutter/packages](https://github.com/flutter/packages/blob/main/packages/google_sign_in/google_sign_in_android/android/src/main/java/io/flutter/plugins/googlesignin/GoogleSignInPlugin.java) и первичный [Flutter issue 188062](https://github.com/flutter/flutter/issues/188062) о повторной доставке результатов. Это основание явно проверять однократное потребление результата; не доказательство причины Catching status 8.

Первый проход выполнен через GitHub-коннектор (поиск кода/Issues), прямой GitHub CLI/API и независимый web-поиск. Коннектор вернул пустые совпадения; прямой поиск обнаружил живые проекты/миграционные обсуждения (в том числе AndBible #3850, FatLossTrack #25, capacitor-plugins #1135). Контрольный проход по getAuthorizationResultFromIntent, NEED_REMOTE_CONSENT, standalone account picker и refresh token выявил [обсуждение разделения входа/consent](https://stackoverflow.com/questions/77878155/oauth-consent-screen-in-android-app-with-oauth2-0-using-credential-manager-api) и [NEED_REMOTE_CONSENT](https://stackoverflow.com/questions/79573308/notice-requesttokenmanager-gettoken-need-remote-consent-when-authorizing). Чужие кейсы, особенно requestOfflineAccess/неверный server client ID, не переносятся на Catching, где offline access не запрашивается. Совпадения Spotify/других одноимённых AuthorizationClient исключены как другой API. Ни один найденный отчёт не доказывает первопричину именно этого физического status 8.

| Вариант | Пригодность для Catching |
|---|---|
| Credential Manager + AuthorizationClient | Корректен для аутентификации личности, но добавляет ненужный здесь ID-токен/Web client ID. Не выбран без такой продуктовой потребности |
| Штатный AccountManager chooser API23+ + AuthorizationClient | Выбран: явный выбор Google-аккаунта без чтения списка аккаунтов/GET_ACCOUNTS, затем отдельный запрос прав данных. Не является legacy GoogleSignIn |
| Только authorize без отдельного выбора | SDK допускает, но не даёт требуемого владельцем явного этапа выбора и восстановления |
| Legacy GoogleSignIn / WebView / обязательный VPS | Исключены прямым требованием владельца, не внедряются |

SDK 21.4.0 не обновляется наугад; все используемые API в нём уже доступны. Нет утверждения, что перестройка кнопочного кода устранит status 8.

## Сравнение старого кода и реализации

| Область | Было | Стало |
|---|---|---|
| Выбор | authorize с пустым либо прежним сохранённым аккаунтом | Отдельный системный chooser, проверка типа com.google; выбор ещё не считается грантом |
| Жизненный цикл | coroutine/флаги в Activity | Retained ViewModel + SavedStateHandle: только этап/аккаунт/признак окна, без токенов/Intent |
| Возврат consent | Разбор SDK был корректным, но без защиты повторов | Оба ActivityResult-контракта до STARTED, однократная обработка по фазе, игнорирование позднего результата |
| Отмена/ошибка | UI часто показывал технические коды | Отмена без payload отдельно от ApiException в непустом Intent даже при resultCode=0; понятное сообщение, числовые коды отдельно |
| Сворачивание/гибель процесса | Частичные флаги, потеря текущей операции | Pending ActivityResult возвращается в сохранённую фазу; незавершённые сетевые фазы требуют нового authorize по «Продолжить» |
| Грант | Проверялся scope/token | Обе проверки сохранены, нет Drive API до гранта; сначала сверка аккаунта и Catching/Inbox, затем commit destination/очередь |
| Фон | authorize при отправке | Сохранён новый вызов authorize для токена, добавлена строгая проверка drive.file; clearToken только после HTTP401 |
| Диагностика | Произвольные providerMessage/detail с маскированием | Только whitelist этапов/типов/числовых кодов/булевых флагов и параметры установленного APK/GMS; никаких токенов, Intent или email |

Выделен общий DriveFolders, используемый и подключением, и прежним загрузчиком. Алгоритм checkpoint/preallocated IDs/проверки папок сохраняется. Успех подключения выставляется только после действительного разрешения, сверки аккаунта через Drive и подготовки Catching/Inbox. Сохраняется прежний аккаунт при неуспешной смене; старые локальные записи не удаляются/не переписываются.

## Историческая фикстура queued → synced

Старый debug 0.5.1 имеет **настоящий** путь: SyncWorker → SyncItemProcessor(isDebug=true, пустой API URL) → DebugFakeDispatcherGateway → Success("debug-<id>") → UPDATE status=synced/remoteId/updatedAt. Параметр autoSync читается worker один раз. Запись false в helper не отменяет уже работающий/поставленный worker; API сетевого ограничения сам фиктивный отправитель не вызывает. Архивный helper действительно содержит setAutoSync, но не cancelAllWork (проверены DEX-строки неизменённого архива, без JVM/SDK).

Следовательно, историческая фикстура допускает конкурирующего writer **до текущего APK**. Точное расписание исходного падения из его логов восстановить нельзя. Для доказательства и устранения этой причины CI теперь:

1. На неизменённом debug APK 0.5.1 создаёт отдельный probe-race, выполняет старый SyncWorker без сетевых constraints и доказывает queued→synced/фиктивный remoteId при autoSync=false. Этот probe удаляется до настоящих upgrade-записей; их ожидаемые значения не меняются.
2. Новый общий test helper отменяет и дожидается завершения прежних WorkManager задач **до** вставки upgrade-записей, сохраняет false и проверяет их сразу.
3. Проверяет состояния/updatedAt/retry/remoteId ещё в старом Room после открытия старого release **до** установки текущего APK. Затем прежний MigrationTest со строгими инвариантами проверяет реальное обновление.

Старые application APK/модели/Room не пересобираются и не изменяются при повторном использовании архива. Общий androidTest helper компилируется в CI вместе с текущими тестами и обращается к бинарно совместимым Room/Settings API реального исторического target APK. Хеши, source refs и подписи архивных APK по-прежнему проверяются. Скрипты upgrade теперь явно отказывают вне Actions этого репозитория. Результат новой диагностической проверки ещё должен быть подтверждён CI.

## Проверки и внешние границы

Подготовлены восемь протокольных Android-тестов: правильный аккаунт/грант до Drive; отсутствие scope/token; error8 при resultCode0; восстановление consent и duplicate reply; восстановление прерванных authorize/verify через новый токен; отмена/неверный тип аккаунта; late reply; отменённый запрос. Это **синтетический backend для проверки протокола**, не Google OAuth/Drive E2E. Девять прежних Android-тестов, restart и install-over, включая опубликованную 0.6.5, сохраняются.

Cloud-доступ повторно проверен разрешёнными средствами: gcloud не обнаружен, GitHub secrets содержат только параметры подписи, project/client vars не найдены; коннектор метаданных сейфа UNAVAILABLE, точечный штатный exists project ID не дал обнаруженного проекта. Значения секретов не запрашивались/не выводились. Это не доказывает отсутствия проекта у владельца. BLOCKED_OAUTH_CONFIG сохраняется.

Владельцу требуется указать существующий project ID/разрешённый Cloud Console либо проверить в одном проекте: Android client `ru.bruce.dispatcherinbox` + SHA-1 `A6:C7:D7:85:0C:80:50:A4:B1:51:75:ED:34:A1:7B:7E:D8:80:98:11`, Drive API enabled, Branding/Audience/Testing и drive.file. Затем на физическом телефоне пройти выбор/consent, выгрузить оригиналы в Catching/Inbox, сверить байты и повтор очереди без дублей/потери. Секреты и email в GitHub не нужны. PR не объединять до этого E2E.

Фактические результаты CI/артефакта/публикации приведены в начале документа.

## Первый полный CI и завершающая проверка HTTP401

[CI 42479862781](https://github.com/dedtsss/dispatcher-android-inbox/actions/runs/42479862781), `228008ec57445a5c1314ff3dbe18c2b5d399c38b`, SUCCESS: 17/17 Android (8 новых протокольных + 9 прежних), restart 1/1, семь install-over, строгие проверки старого Room до обновления и повтор опубликованной 0.6.5 после restart. Диагностический probe на неизменённом debug APK 0.5.1 прошёл: фактически выполненный старый SyncWorker при autoSync=false с принудительным manual-входом записал synced и debug-probe-race в отдельный probe. Это доказывает исторического writer и отсутствие изоляции лишь настройкой; точное расписание старого падения не воспроизводилось и не приписывается этому принудительному сценарию.

Финальная проверка кода обнаружила отдельный детерминированный пробел: HTTP401 начальной проверки Drive раньше не очищал SDK-кеш, тогда как отправка в фоне уже очищала. Добавлен withInvalidTokenCleanup только для HTTP401; отмена/403/status8 не очищают кеш и не отзывают разрешения. Ошибка очистки не скрывает исходный 401, CancellationException передаётся наверх. Четыре unit-регрессии проверяют эти случаи. Это не гипотеза устранения status8. Новый изменённый APK получает **0.6.8/code14**; промежуточная 0.6.7 в Releases/updater не публиковалась.

Для финального artifact доказана необходимость освободить место именно промежуточного, уже заменённого APK 0.6.7: активный объём после первого CI 275921253 Б, ещё один release ~29 МБ не проходит прежний предел 280000000. Artifact **13616229461**, ZIP digest `0e8fc936fa7503fdd0482ed2c4c55391762a8f6af6f31f7375428a83047710a2`, предварительно сохранён вместе с метаданными в `/home/codex/github-artifact-archive/dispatcher-android-inbox-13616229461.zip` / `.json`, digest проверен. Разрешённая владельцем очистка применяется только к этому новому superseded artifact; исходный архив истории, 0.6.5, все Releases, CI logs и компактные доказательства сохраняются. Бюджет не повышается. Перед удалением проверены exact ID/name/run. Старые два failed artifacts уже удалены управляющим чатом, повторного удаления нет.

Перед завершающим CI дополнительно закрыта возможность запуска второго системного окна при «Продолжить»: повторная проверка authorize во время ожидаемого consent может получить новый токен, но при повторном hasResolution не запускает новое окно поверх прежнего. Ожидающий chooser не перезапускается автоматически. Явное «выбрать и повторить» начинает новую Activity/ActivityResultRegistry, отделяя поздний ответ старой попытки от другого аккаунта. Добавлены два протокольных Android-теста, всего запланировано 19. Эти изменения включены в тот же окончательный APK 0.6.8/code14 до его первой сборки.

## Обновление физической приёмки 10.11

После регистрации Android OAuth client владелец впервые успешно подключил Drive в 0.6.8. Прежний внешний BLOCKED_OAUTH_CONFIG выше — исторический статус, а не текущее препятствие. Полная сверка удалённых оригиналов ещё ожидается. Текущие P1/P2 и продолжение: [handoff 10.11](catching-ux-regressions-2026-11-10.md), [визуальный контракт](../catching-visual-contract.md). OAuth и алгоритм загрузки в этой UX-правке сохраняются.
