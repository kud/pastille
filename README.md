<div align="center">

<img src="assets/icon.png" alt="Pastille icon" width="120" />

# Pastille

**A keyboard you switch to only when you want to paste.**

[![CI](https://github.com/kud/pastille/actions/workflows/ci.yml/badge.svg)](https://github.com/kud/pastille/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/kud/pastille?include_prereleases)](https://github.com/kud/pastille/releases)
[![Licence: GPL-3.0](https://img.shields.io/github/license/kud/pastille)](LICENSE)
![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF)

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1-snippet-list.jpg" alt="Snippet list" width="240" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2-keyboard-snippets.jpg" alt="Keyboard showing snippets" width="240" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3-keyboard-settings.jpg" alt="Keyboard settings" width="240" />

</div>

Pastille is an open-source Android keyboard for pasting, not typing. Tap a saved snippet or a recent image and it goes straight into the text field, then Pastille hands you back to your usual keyboard.

## Features

- Your own snippets, written in the app or saved from the clipboard.
- Clipboard: the last 10 things you copied while Pastille's keyboard or app was open, for an hour, in the keyboard's own Clipboard view (the clipboard button in its toolbar, as in Gboard). Tap to paste, long-press to save one as a snippet or remove it. Kept in memory only, never backed up; passwords and copies marked sensitive are skipped. Settings › Keyboard turns it off.
- Folders for snippets, with an "All" view, and drag to reorder snippets and folders right in the keyboard.
- Recent images from the folders you choose (Screenshots, Camera, Downloads and so on), inserted as images where the app accepts them and copied to the clipboard where it does not.
- Your own stickers in their own tab, from the photo picker or the share sheet, with transparent PNG, WebP and animated GIF/WebP kept as they are. Never packs.
- Share text or an image from any app to Pastille to keep it as a snippet.
- A Quick Settings tile that opens your snippets as a sheet to copy from, in any app.
- Choose, separately for snippets and images, whether to return to your usual keyboard after inserting, and which keyboard that is.
- Long-press the keyboard key to pick any other keyboard from Android's list.
- Turn snippets, stickers or images off entirely if you only need some of them.
- Gboard-style dark, light and Material You themes, with a choice of panel heights.
- Share a snippet, sticker or image through Android's share sheet (Quick Share included), or the whole collection as one backup file. Open a backup someone sent you and Pastille imports it, skipping what you already have.
- Show a text snippet as a QR code, full screen, for another phone to read with its camera.
- Sync snippets, stickers and images through a file you choose, in Google Drive or on the phone: Pastille reads it and updates it after every change, so two phones that choose the same file keep the same collection (Settings › Backup).

## Install

- **GitHub Releases:** download the latest APK from the [releases page](https://github.com/kud/pastille/releases) and open it on your phone. You may need to allow installs from your browser or file manager.
- **F-Droid:** coming soon.

Releases are cut by one workflow run; see [docs/RELEASING.md](docs/RELEASING.md).

Pastille needs Android 9 (API 28) or later.

## How to enable the keyboard

1. Open the Pastille app and follow the setup card, or do it by hand in the next steps.
2. Enable Pastille under Settings → System → Keyboard → On-screen keyboard.
3. In any text field, switch to Pastille with the keyboard switcher.
4. Optionally, grant photo access in Pastille's Settings to see recent images in the panel.

### Quick Settings tile

The Pastille Quick Settings tile opens a sheet of your snippets and recent images over whatever app is open. Tap one to copy it to the clipboard; long-press for its actions. If the phone is locked, it asks you to unlock first. The keyboard button in the sheet's header opens the system keyboard picker.

## Privacy

- Pastille requests no `INTERNET` permission and has no analytics or network libraries. Your snippets, stickers and images stay on your phone unless you export, share or back them up yourself.
- Snippets live in a local Room database on your device.
- Pastille opts out of Android's cloud backup, so nothing goes to Google's backup servers. Moving to a new phone with Android's device-to-device transfer (by cable or over Wi-Fi) still brings your snippets, stickers, images and settings.
- Backups only move when you ask: export, import and automatic backup go through the system file picker (Storage Access Framework), and sharing through the system share sheet, so you choose where every file goes. If that place is Google Drive, Drive's own app does the uploading, not Pastille.
- Photo access is optional and only used to show recent images in the panel.

## Build

Requires JDK 17 or later and the Android SDK. The Gradle wrapper is checked in.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Or open the directory in Android Studio and run the `app` configuration on a device or emulator. CI runs the same three tasks on every push and pull request to `main`.

## Documentation

- [Design spec](docs/design-spec.md): how the keyboard and app look and behave.
- [Architecture](docs/architecture.md): schema, storage, image handling and the Quick Settings tile mechanics.
- [Third-party notices](docs/third-party-notices.md): code from others that Pastille includes.

## Support

Pastille is free and stays free. If it saves you time, you can [buy me a coffee on Ko-fi](https://ko-fi.com/kud).

## Licence

Copyright (c) 2026 Erwann Mest. Released under the GNU General Public License v3.0 or later; see [LICENSE](LICENSE).
