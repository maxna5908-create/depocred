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
запускает публикацию в Console. Workflow запускается вручную или при push в отдельную ветку release/google-play/**.
Обычные push в feature/native-android публикацию не запускают.

Артефакт depocred-signed-android содержит APK, AAB и mapping.txt.
Все стадии используют один SHA, зафиксированный в начале запуска.
Подписывание и загрузка выполняются при ручном запуске или push в release/google-play/**.
Push и pull request запускают лишь проверки.

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

## Запуск через подключение GitHub без CLI

Создать релизную ветку release/google-play/1.0.N из предыдущего коммита, затем
перевести её на проверенный коммит Android. Push запускает сборку с publication
в production/completed и отправкой на проверку. Этот путь использует обычные
права записи в репозиторий и существующие Actions secrets. Не коммитить в
релизную ветку посторонние изменения: каждый подходящий push запускает выпуск.
