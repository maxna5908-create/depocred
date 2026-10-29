# Google Play через GitHub Actions

Пакет: `ru.depocred.app`. Исходники: `feature/native-android`.
В main находится ручной launcher `Android Google Play`; iOS workflow независим.

## Секреты репозитория

Settings → Secrets and variables → Actions → Repository secrets:

- DEPOCRED_KEYSTORE_BASE64 — исходный keystore, Base64.
- DEPOCRED_STORE_PASSWORD — пароль хранилища.
- DEPOCRED_KEY_PASSWORD — пароль ключа (alias key0).
- GOOGLE_PLAY_SERVICE_ACCOUNT_JSON — полный JSON сервисного аккаунта.

Google Cloud: включить Android Publisher API. Сервисному аккаунту предоставить
в Play Console доступ только к DepoCred: просмотр приложения, выпуск рабочих
и тестовых версий, управление страницей приложения. Cloud IAM роли для этого не нужны.
Не коммитить ключи, JSON и пароли.

## Первый запуск

Actions → Android Google Play → Run workflow, ветка main.
По умолчанию publish=true, track=production, status=completed, submit_review=true.
Будут выполнены unit/lint, 14 проверок на эмуляторе, съёмка экранов на трёх
размерах Android-устройств, release-сборка и загрузка в рабочий канал.
Бандл, mapping, русское описание, 24 скриншота и feature graphic отправляются
одним edit в Google Play. После одобрения Google выпуск публикуется согласно
настройкам Managed publishing. При включённой управляемой публикации владелец
запускает публикацию в Console. Workflow публикации запускается только вручную.
Обычные push в feature/native-android публикацию не запускают.

Артефакт depocred-signed-android содержит APK, AAB и mapping.txt.
Все стадии используют один SHA, зафиксированный в начале запуска.
Подписывание выполняется при ручном запуске или push в validation/android/**.
Загрузка в магазин выполняется только при явном publish=true.
Обычные push и pull request запускают лишь проверки.

## Следующие выпуски

До загрузки увеличить BUILD_VERSION: versionCode должен быть выше всех ранее
загруженных версий, включая черновики и тестирование. Повторная загрузка того же
versionCode не допускается; существующий выпуск продвигать через Play Console.

- Только собрать файлы: publish=false.
- Черновик в выбранном канале: status=draft, submit_review=false.
- Готовый выпуск без отправки на проверку: status=completed, submit_review=false.
- Отправить выпуск: status=completed, submit_review=true. Для production после
  одобрения Google возможна публикация согласно настройкам Managed publishing.

Отправка на проверку не гарантирует одобрение. Обязательные декларации,
политика конфиденциальности и карточка приложения заполняются в Play Console.
Этот workflow обновляет русскую карточку, примечания, feature graphic и по 8
скриншотов для телефона, 7- и 10-дюймового планшета. Иконка и другие локали
сохраняются. Исходные скриншоты снимаются с текущего Android SHA, креативы
собираются скриптом android/scripts/render-play-creatives.py и сохраняются
в Actions artifacts play-creatives-*. Скриншоты iOS не используются.
RuStore этим workflow не обслуживается.

## Ошибки

- 403: проверить API, email и права сервисного аккаунта для ru.depocred.app.
- Wrong signing key: сравнить SHA-256 с Upload key certificate в Play Console.
- Version code already used: увеличить счётчик и собрать новый выпуск.
- Changes not sent for review / incomplete app: проверить Publishing overview
  и обязательные поля Play Console; не переключать отправку вслепую.
- При ошибке подписи не создавать новый ключ: проверить пароль и alias key0.

Пароли и содержимое JSON не выводить в логи. Секреты доступны только runtime.

## Проверка без публикации

Push в validation/android/1.0.N запускает проверки и подписанную сборку APK/AAB.
Этот путь не загружает приложение ни в Google Play, ни в RuStore.
Публикация через push в release/google-play/** отключена.

## Общий выпуск (обновление 29.10.2026)

Рекомендуется Actions → Android stores: одна подписанная сборка для Google Play и RuStore. Оба флага магазинов по умолчанию выключены. Для всех трёх магазинов — Release all stores, publish=true только при разрешённой публикации. Автопубликация по push в release/google-play/** отключена: обычные коммиты и validation/android/** не отправляют версии в магазины. Исходный SHA передаётся артефактом android-source-provenance.
