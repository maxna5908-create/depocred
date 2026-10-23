# Настройка Apple и выпуск DepoCred

Эти действия выполняются в вашем аккаунте. Пароль Apple ID в репозиторий или чат передавать не нужно. Секреты хранятся в GitHub Actions Secrets; исходники и скриншоты находятся в открытом репозитории.

## 1. Идентификатор приложения

1. Откройте [Apple Developer Account](https://developer.apple.com/account/).
2. В Membership details найдите **Team ID** — 10 символов. Сохраните его для пункта 5.
3. Откройте **Certificates, Identifiers & Profiles → Identifiers → + → App IDs → App**.
4. Description: `DepoCred`. Bundle ID: **Explicit**, `ru.depocred.app`.
5. Дополнительные возможности (Push Notifications, iCloud и т. п.) приложению не нужны. Зарегистрируйте идентификатор.
6. Если `ru.depocred.app` уже занят другой командой, выберите свободный и укажите именно его в переменной `IOS_BUNDLE_ID` в пункте 5. После первой загрузки менять Bundle ID нельзя.

## 2. Карточка в App Store Connect

1. Откройте [App Store Connect](https://appstoreconnect.apple.com/) → **Apps → + → New App**.
2. Платформа: **iOS**. Название: `DepoCred` (если доступно). Основной язык: **Russian**.
3. Выберите Bundle ID из пункта 1. SKU: `depocred-ios`. Доступ пользователей — по вашим правилам команды.
4. Создайте карточку. В **App Information** скопируйте числовой **Apple ID** приложения; это `IOS_APP_STORE_ID`, а не почта вашей учётной записи.

## 3. Сертификат и профиль подписи

Нужны **Apple Distribution** в файле `.p12` с закрытым ключом и профиль **App Store Connect** для Bundle ID этого приложения. Android `.jks` для iOS не используется.

Если действующий сертификат уже есть в связке ключей Mac:

1. Xcode → Settings → Accounts → ваша команда → Manage Certificates. Создайте Apple Distribution, если его ещё нет.
2. Keychain Access → My Certificates → Apple Distribution. Экспортируйте сертификат **вместе с private key** в `.p12` и задайте пароль.
3. Apple Developer → Profiles → **+ → Distribution → App Store Connect**.
4. Выберите App ID DepoCred и тот же сертификат, назовите профиль `DepoCred App Store`, Generate → Download.

Если Mac нет, подготовка возможна на Windows с Python и OpenSSL (OpenSSL есть, например, в установленном Git for Windows):

1. Скачайте/клонируйте ветку `feature/native-ios` и откройте PowerShell в её каталоге.
2. Проверьте `py --version` и `openssl version`. При установленном Git можно добавить OpenSSL в текущий PowerShell:

```powershell
$env:Path += ';C:\Program Files\Git\usr\bin'
```

3. Создайте закрытый ключ и публичный запрос на сертификат. Папка должна быть вне репозитория:

```powershell
py ios/scripts/prepare_signing.py csr --directory C:\DepoCredSigning
```

4. Утилита попросит придумать пароль ключа. Сохраните его. В Apple Developer → Certificates → + → **Apple Distribution** загрузите только `C:\DepoCredSigning\DepoCred.certSigningRequest`.
5. Скачайте сертификат `distribution.cer`. Создайте профиль App Store Connect, как описано выше, и скачайте `.mobileprovision`.
6. Упакуйте сертификат с исходным ключом; реальные пути подставьте вместо примеров:

```powershell
py ios/scripts/prepare_signing.py package --directory C:\DepoCredSigning --certificate C:\Users\YOU\Downloads\distribution.cer --profile C:\Users\YOU\Downloads\DepoCred_App_Store.mobileprovision
```

7. Введите пароль исходного ключа и новый пароль `.p12`. Будут подготовлены `APPLE_CERTIFICATE_BASE64.txt`, `APPLE_PROVISIONING_PROFILE_BASE64.txt` и `.p12`. Пароль `.p12` нужен для GitHub Secrets. Сохраните приватную папку и пароли у себя; в Git её не добавляйте.

Если `.p12` уже получен с Mac, Base64 можно сделать в PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\DepoCredSigning\distribution.p12')) | Set-Clipboard
```

Так же закодируйте `.mobileprovision`. Base64 — способ представления файла, не шифрование.

## 4. Ключ загрузки App Store Connect

1. **App Store Connect → Users and Access → Integrations → App Store Connect API → Team Keys**.
2. Если API ещё не активирован, Account Holder нажимает **Request Access**.
3. Нажмите **Generate API Key**. Имя: `DepoCred GitHub`. Роль: **Developer** — для загрузки сборок. Для управления карточкой/отправки на ревью через API понадобится роль App Manager; текущий процесс делает только загрузку.
4. Скачайте `.p8` (Apple позволяет скачать его один раз). Скопируйте **Key ID** и **Issuer ID**.
5. Ключ команды действует в пределах предоставленной роли на приложения команды, а не только на DepoCred. При необходимости его можно отозвать в этом же разделе.

## 5. GitHub Secrets и Variables

Откройте [настройки Actions](https://github.com/maxna5908-create/depocred/settings/secrets/actions) → **New repository secret**. Создайте:

| Имя | Значение |
|---|---|
| `APPLE_TEAM_ID` | Team ID из пункта 1 |
| `APPLE_CERTIFICATE_BASE64` | Весь текст Base64-файла `.p12` |
| `APPLE_CERTIFICATE_PASSWORD` | Пароль `.p12` |
| `APPLE_PROVISIONING_PROFILE_BASE64` | Весь текст Base64-профиля |
| `APP_STORE_CONNECT_KEY_ID` | Key ID из пункта 4 |
| `APP_STORE_CONNECT_ISSUER_ID` | Issuer ID из пункта 4 |
| `APP_STORE_CONNECT_PRIVATE_KEY` | Полный текст `.p8`, включая BEGIN/END и переносы строк; без Base64 |

Во вкладке **Variables → New repository variable**:

| Имя | Значение |
|---|---|
| `IOS_BUNDLE_ID` | `ru.depocred.app` или зарегистрированный вами альтернативный Bundle ID |
| `IOS_APP_STORE_ID` | Числовой Apple ID карточки из пункта 2 |

Никакие ключи или пароли не добавляются через **Add file** в репозиторий.

## 6. Запуск подписанной сборки

1. GitHub → [Actions → iOS App Store](https://github.com/maxna5908-create/depocred/actions/workflows/ios-release.yml).
2. **Run workflow**, ветка **main**. Точка запуска находится в основной ветке; она вызывает процесс из `feature/native-ios`. Код приложения берётся из iOS-ветки, её SHA фиксируется до тестов и используется для подписи.
3. Установите `release = true`, запустите.
4. Сначала проходят проверки приложения на iPhone и iPad. Затем задача `release` проверяет Team ID, Bundle ID, срок профиля и совпадение сертификата, создаёт подписанный `.ipa` и загружает его в ваш App Store Connect.
5. После обработки Apple сборка появляется в **TestFlight**. Выберите внутреннюю группу тестировщиков. Для внешних тестировщиков Apple может потребовать Beta App Review.
6. Файл IPA и отладочные символы сохраняются в артефакте `depocred-ios-app-store` на странице запуска. Неподписанный simulator ZIP нельзя установить на iPhone.

## 7. Публикация

1. Протестируйте приложение через TestFlight на вашем iPhone; проверьте ввод, расчёты, историю, PDF/«Файлы» и обе темы.
2. Заполните карточку по [готовым текстам](../store/ru-RU.md), укажите реальные контакты владельца и доступную публичную страницу политики конфиденциальности.
3. Добавьте скриншоты iPhone и iPad из проверенной сборки. Требования Apple меняются: [актуальные размеры](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/).
4. App Privacy: данные не собираются. Ответьте на возрастной опрос по фактическим функциям; рекламы, чатов, пользовательского контента и покупок нет.
5. Укажите цену и страны распространения. Если требуется, владелец аккаунта принимает соглашения и заполняет статус продавца.
6. Выберите загруженную сборку. В App Review Information укажите контакт владельца; вход в приложение не требуется. Текст для ревью подготовлен в комплекте.
7. Выберите **Manually release this version**, отправьте на App Review. После одобрения нажмите выпуск версии.

Автоматическая загрузка в TestFlight не отправляет приложение на публичную публикацию. Секреты позволяют подписывать и загружать сборки; действующая сессия Apple ID в браузере GitHub не нужна.

## Официальные источники

- [Apple: профиль App Store](https://developer.apple.com/help/account/provisioning-profiles/create-an-app-store-provisioning-profile/)
- [Apple: App Store Connect API](https://developer.apple.com/help/app-store-connect/get-started/app-store-connect-api)
- [Apple: загрузка сборок](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)
- [GitHub: сертификаты на macOS runners](https://docs.github.com/en/actions/how-tos/deploy/deploy-to-third-party-platforms/sign-xcode-applications)
