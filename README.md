# Rams Editor

**Rams Editor is a lightweight Android code editor for building and previewing web projects directly on your phone.** It brings project files, code editing, HTML/CSS/JavaScript tools, and a floating live preview into one mobile workspace.

> Edit your code. Keep the result in view. Build from your phone.

[![Latest Release](https://img.shields.io/github/v/release/Ramsfield30/rams-editor?display_name=tag)](https://github.com/Ramsfield30/rams-editor/releases)
[![Build](https://github.com/Ramsfield30/rams-editor/actions/workflows/build.yml/badge.svg)](https://github.com/Ramsfield30/rams-editor/actions/workflows/build.yml)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)

## Why Rams Editor?

Rams Editor is designed around a practical mobile web-development workflow. One of its central tools is a **floating live preview**: open your web page in a movable preview window, leave it visible over the editor, and continue working on your code. When the preview is open, edits can update it automatically after a short typing pause.

You can also open a full-screen preview or use the responsive device emulator when you want a larger view of the page.

## Features

### Code editing
- Code editor powered by [Sora Editor](https://github.com/Rosemoe/sora-editor).
- Syntax highlighting and code suggestions.
- Automatic closing/indentation helpers for supported markup.
- Multiple open file tabs.
- Project file and folder navigation.
- Create, rename, and delete project files and folders.
- Automatic saving while editing.
- Find and replace, including case-sensitive, whole-word, and regular-expression options.
- Configurable editor settings and quick-insert snippet keys.

### Web development
- HTML, CSS, and JavaScript project workflow.
- Floating HTML live preview that can remain on screen while you edit.
- Automatic preview updates while the floating preview is open.
- Full-screen web preview.
- Responsive device emulator with device presets and custom viewport dimensions.
- JavaScript console output, filters, and command input in preview tools.
- Local project server for serving project files during preview.
- Emmet-style HTML and CSS abbreviation expansion.

### Responsive preview
Use the device emulator to inspect a page at different viewport sizes, including phone, tablet, and desktop-sized dimensions. You can also enter custom width and height values and resize the preview.

## Download

Get the latest Android APK from the **[GitHub Releases page](https://github.com/Ramsfield30/rams-editor/releases/latest)**.

1. Open the latest release.
2. Download the APK asset.
3. Install it on your Android device.
4. If Android asks, allow installation from the source you used to download the APK.

**Note:** The current release asset is named `app-debug.apk`. Android may display a warning because it is a debug build. Only install APKs you trust.

## Android requirements

- Android 5.0 (API 21) or later.
- Internet permission is declared for web content and related preview functionality.
- The floating preview requires Android's **Display over other apps** permission. Rams Editor will need this permission before it can show a floating window.

## Build information

Rams Editor is an Android application built with:

- Kotlin
- Java
- Android SDK (compile/target SDK 34)
- Java 17
- Gradle
- AndroidX
- Sora Editor

The Android application ID is `com.rams.editor`.

To work on the project, clone the repository and open it in Android Studio. Use a compatible Android SDK and Java 17 toolchain.

## Project status

Rams Editor is under active development. Features, interface design, and compatibility may change between builds. Some workflows may still need testing across different Android versions and screen sizes.

If you find a bug, please [open an issue](https://github.com/Ramsfield30/rams-editor/issues) and include:
- Your Android version and device model.
- The Rams Editor version/build number.
- Steps to reproduce the problem.
- A screenshot or screen recording, if it helps explain the issue.

## Repository

- **Source code:** [Ramsfield30/rams-editor](https://github.com/Ramsfield30/rams-editor)
- **Releases:** [Download Rams Editor](https://github.com/Ramsfield30/rams-editor/releases)
- **Issues:** [Report a bug or request a feature](https://github.com/Ramsfield30/rams-editor/issues)

## License

No license has been added to this repository yet. Until a license is provided, the source code is publicly viewable, but reuse, redistribution, and modification are not automatically permitted.
