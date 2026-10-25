# GlassLauncher43

GlassLauncher43 is a lightweight Android TV launcher designed for older, low-memory televisions running Android 4.3 (API 18). It uses the Android framework directly and has no third-party runtime dependencies.

## Features

- Remote-control navigation with D-pad, OK, Back, and Menu keys
- Five featured apps on the home screen
- Five-column application library
- Reorder apps on both screens
- Add or remove home-screen shortcuts
- Open the system uninstall screen for installed apps
- Automatically discovers newly installed launcher applications
- Optional rotating wallpaper folder
- Lightweight canvas-based interface and fallback desert background

## Wallpaper rotation

Create a folder named `壁纸` in the device's shared internal storage:

```text
/sdcard/壁纸
```

Add JPG, JPEG, PNG, or WebP images. Images are sorted by filename and rotate every 30 minutes. If the folder is empty, the built-in desert background is shown.

For low-end televisions, 1280 × 720 JPG images around 300 KB to 1.5 MB are recommended. The launcher keeps only the current wallpaper in memory and decodes it as RGB_565.

## Requirements

- Android 4.3 or later
- Android SDK 28 for compilation
- JDK 17 for the included Gradle setup

## Build

Set `ANDROID_HOME` to your Android SDK, then run:

```powershell
./gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installation, choose **Glass Home** as the default Home application in the system launcher prompt or Android settings.

## Notes for Android 4.3 televisions

Some vendor ROMs override the selected Home application during boot or when the Home key is pressed. This repository contains only the launcher application and does not include vendor firmware patches, proprietary applications, device backups, or recovery images.

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).

