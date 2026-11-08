# Catching: повторный разбор подключения Google Drive 08.11.2026

## Границы и baseline

Продолжение Issue #7 / PR #8, один прежний исполнитель, без подчинённых агентов. Baseline local/remote — чистая `issue-7-drive-inbox`, `54ea3d2a728a461224214ea1f26a76456a9237b5`. §7, AGENTS и search-default-policy перечитаны. Прозрачное оформление **принято владельцем и не меняется**. Новая функциональная версия — **0.6.7/code13**, стабильные package/ключ/Room/оригиналы/очередь сохраняются. Все сборки/JVM/SDK/эмуляторы — только Actions приложения.

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

Результаты CI/артефакта/публикации будут дописаны только по факту.
