# Инструкция по настройке Capacitor Share и Filesystem для Android

Чтобы механизм создания и отправки (share) PDF работал корректно на Android (исключая проблему "открывается выбор канала, но файл не отправляется, а мессенджер повисает"), необходимо учесть три ключевых аспекта: правильный способ передачи файла в Share API, настройку FileProvider и конфигурацию разрешений.

## 1. Избегайте конфликта Intent-типов (Уже исправлено в коде)

Самая частая причина "повисания" приложения при попытке отправить файл (особенно в WhatsApp/Telegram) — это отправка текста и файла *одновременно*.
Когда мы отправляем и `text`, и `files` массив, Capacitor формирует Android Intent. Но `text` подразумевает тип `text/plain`, а PDF — `application/pdf`. Смешанные типы вызывают сбой при обработке Intent'а мессенджерами.

**Как мы это решили кодом:**
Для нативных платформ (Android/iOS) при шаринге PDF мы передаем URI файла через параметр `url` (а не массив `files`) и **не** передаем параметр `text`. Это гарантирует, что Intent будет строго типа `application/pdf`.

## 2. Настройка FileProvider в Android-проекте

Чтобы сторонние приложения (мессенджеры, почта) могли получить доступ к PDF-файлу, сохраненному в папку "Кэш" (Cache), необходимо настроить FileProvider. Capacitor делает это частично, но для уверенности нужно явно указать `file_paths.xml`.

**Шаг 2.1. Создайте файл путей**

В папке `android/app/src/main/res/xml/` (создайте папку `xml`, если ее нет) создайте файл `file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- Доступ к кэш директории приложения (Directory.Cache) -->
    <cache-path name="my_cache_files" path="." />
    <external-cache-path name="my_external_cache_files" path="." />
    <!-- Доступ к публичным директориям (если понадобится) -->
    <external-path name="my_external_files" path="." />
    <files-path name="my_files" path="." />
</paths>
```

**Шаг 2.2. Пропишите FileProvider в AndroidManifest.xml**

Откройте файл `android/app/src/main/AndroidManifest.xml` и внутри блока `<application>` добавьте (или проверьте наличие):

```xml
<application ...>
    ...
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
    ...
</application>
```

*(Обратите внимание: `${applicationId}` подставится автоматически).*

## 3. Настройка разрешений в AndroidManifest.xml

Для записи PDF и чтения его перед отправкой иногда требуются явные разрешения на работу с памятью (даже для Cache `Directory` на версиях Android < 11).
Добавьте перед тегом `<application>`:

```xml
<!-- Необходимые разрешения для записи PDF файлов и шаринга -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_DOCUMENTS" />
```

## 4. Конфигурация capacitor.config.ts

Убедитесь, что ваш плагин настроен. Обычно ничего больше не требуется, но для Share плагина не должно быть блокирующих опций.
В `package.json` должно быть установлено:
- `@capacitor/core`
- `@capacitor/filesystem`
- `@capacitor/share`

После внесения этих правок нужно пересобрать Android проект:
```bash
npx cap sync android
```
И затем собрать скомпилированный APK в Android Studio.
