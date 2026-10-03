# Pastille

Pastille is an open-source Android keyboard for pasting your own text snippets and recent screenshots. It is not a keyboard for typing: you switch to it when you need to drop in a saved reply, address or screenshot, then hop back to your usual keyboard.

Pastille never connects to the network. The manifest requests no internet permission, there are no analytics and no network libraries. Snippets live in a local Room database on your device, and backup files only move through the Storage Access Framework when you ask for an export or import.

## Features

- Paste keyboard (IME) with your snippets in a pinned-first grid; tapping one commits it to the current field and long-pressing opens it in the editor.
- Six most recent screenshots in the panel; tap to paste into apps that accept images, otherwise the image is copied so you can long-press to paste.
- Save the current clipboard contents as a snippet straight from the keyboard.
- Snippet manager app: create, edit, pin, search and delete; JSON export and import (`{ "version": 1, "snippets": [...] }`) via the system file picker.
- Onboarding card covering enabling the IME, switching to it, and photo access, including the Android 14 partial-access case.
- Material 3 with light and dark themes plus dynamic colour where available.

## How to enable the keyboard

1. Open the Pastille app and work through the setup card, or do it manually:
2. Enable Pastille under Settings → System → Keyboard → On-screen keyboard (or `Settings.ACTION_INPUT_METHOD_SETTINGS`).
3. Switch to Pastille from any text field with the keyboard switcher.
4. Grant photo access if you want recent screenshots in the panel.

## Build

- Android Studio: open this directory and run the `app` configuration on a device or emulator (minSdk 28).
- CI builds every push and pull request to `main` with `gradle --no-daemon assembleDebug testDebugUnitTest lintDebug`.
- There is no Gradle wrapper checked in yet (no `gradlew`, no wrapper jar), so command-line builds need a local Gradle 8.10+ install.

## Roadmap (out of v1)

Typing keys, cloud sync and espanso import are deliberately out of v1. Everything stays local until sync is designed properly.

## Licence

MIT, Copyright (c) 2026 Erwann Mest. See LICENSE.
