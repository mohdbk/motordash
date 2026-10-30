# AGENTS.md

Notes for anyone (human or AI agent) picking up MotorDash. Read this first, then
[docs/development.md](docs/development.md) and [docs/releasing.md](docs/releasing.md).

## What it is
An Android app for **BYD DiLink** car head units (Android 13, 1920×1080 landscape). The car runs a
small HTTP server and shows a QR code. Any device on the same network opens it in a browser to send
files and text to the car or fetch files back. Nothing is installed on the other device.
The main use is sideloading **APKs** onto the car, then media.

## Hard constraints of the target
- **No ADB** on the car. You can't read logs or crashes there. Every APK reaches the car by USB stick
  or the in-app updater.
- **No "All files access"**: the app only writes to `Download/` or `Documents/` (any file type).
  Other public folders are type-restricted, and Android 13 hides other apps' non-media files.
- **No Play Store**: updates can't install silently; the user taps Install once.
- No reliable system folder picker (SAF) on DiLink; don't build features on it.

## Architecture (three modules)
- `app/`: installed shell. `FileServer` (NanoHTTPD), `FileShareHost` (implements `Host`),
  `StorageFolders`, `Thumbnails`, `LanAddresses`, `update/` (Updater + UpdateClient), the device
  web page `assets/web/index.html`, and `AppText` for messages the shell shows itself.
  `src/debug` has the hot reload client; `src/release` just shows the bundled dashboard.
- `dashboard/`: all car-screen UI (Compose). `Strings.kt` (English/Arabic), `FilesPage`,
  `SettingsPage`, `Theme` (palette, `AppDialog`, `LogoMark`, fonts).
- `dashboard-api/`: the contract: `Host`, `DashboardUi`, data classes, `API_VERSION`.
- `devserver/serve.py`: continuous `:dashboard:hotPayload` build, served to the car over an
  HTTPS tunnel (ngrok static domain).

Two apps install side by side: **MotorDash** (`app.motordash`, release, updater) and
**MotorDash Dev** (`app.motordash.dev`, debug, hot reload, own folder `Download/MotorDash Dev`).

## Rules that prevent real bugs
1. **Bump `API_VERSION`** for any change in `dashboard-api/`. The dev server then refuses stale
   payloads, so the car shows "APK outdated, reinstall" instead of crashing.
2. **Never wrap `Host` with `object : Host by host`.** Kotlin's incremental compiler didn't
   regenerate the delegate when `Host` grew. That shipped an `AbstractMethodError` launch crash
   (twice). Debug code feeds `FileShareHost.devStatusState` / `onOpenDevSettings` instead.
3. **Run `AppStartTest` before handing over any APK** (`:app:testDebugUnitTest` and
   `:app:testReleaseUnitTest`). It is the only way to see a launch crash without ADB.
4. **Don't run Gradle while the dev server's continuous build is busy.** Overlapping builds once
   produced an empty hot payload. `hotPayloadClasses` now fails instead of shipping an empty jar.
   If a payload looks stale, run `./gradlew :dashboard:hotPayload`.
5. **Car UI text lives in `Strings.kt`, not `res/values`.** Hot-loaded code can't add Android
   resources. Every string needs English and Arabic.
6. **Arabic and right-to-left:** wrap Latin text inside Arabic strings with `ltr()` / `isolate()`
   (Unicode isolates); use `LtrText` for addresses and PINs; no letter spacing on Arabic
   (`tracking()`); use `AppDialog`, never a raw `AlertDialog` (dialog windows reset direction);
   the "MotorDash" wordmark always stays left-to-right.
7. **Declare inputs on Gradle tasks that package files.** `releaseFiles` once kept an old APK
   because it had none.
8. A `409` from the server without reading the body must close the connection (`.closing()`),
   or the leftover body corrupts the next keep-alive request.

## The user's preferences
- Say **"device"**, never "phone": UI (جهاز in Arabic), README, docs, code names, comments.
- Headings in **Title Case** ("File Sharing", "Technical Details"); no all-caps labels.
- Keep technical info out of the main Settings page. It goes in Technical Details; developer
  items appear only in the debug build.
- Main idea is APKs: list "APKs" first in copy about what can be sent.
- Verify visually: render screenshots and look at them before saying a UI change is done.
- Wants things done end to end (build, test, rebuild APKs) and told what to install.

## How to check work here
- `./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :dashboard:testDebugUnitTest`.
  CI (`.github/workflows/test.yml`) runs the same without fonts or `local.properties`.
- Screenshots: `dashboard/build/screenshots/*.png` (1920×1080, English + Arabic, portrait).
  To see a dialog, temporarily default its `remember` flag to open and capture with
  `captureScreenRoboImage` (dialogs are separate windows). Restore the source afterwards.
- Device web page: render it with headless Chromium and a mocked `fetch`. The snap Chromium only
  reads files under `~/snap/chromium/common/`, so serve the page with `python3 -m http.server`.
- Test version text shows "?" and package `app.motordash.dashboard.test`: that's Robolectric,
  not a bug.

## Local setup (this machine)
- `JAVA_HOME` isn't set by default: JDK 17 is in `~/tools/jdk-17…`, the SDK in
  `~/tools/android-sdk`, ngrok in `~/tools/ngrok`.
- `local.properties` (git-ignored) holds: `sdk.dir`, dev server URL and token, release keystore
  path and password, `motordash.updateUrl`. The release keystore is outside the repo.
  **Both must be backed up**; losing the key means users must uninstall to update.
- Dubai font files (`Dubai-{Light,Regular,Medium,Bold}.{ttf,woff2}`) sit in
  `app/src/main/assets/fonts/` locally but are **git-ignored** (licence: embed, don't
  redistribute). Release builds must be made where they exist; without them the app falls back
  to the system font.
- `gh` is logged in as `mohdbk`; commits use the GitHub no-reply email.

## Releasing (summary; details in docs/releasing.md)
1. Bump `versionCode` (+1) and `versionName` (`0.1.0-beta.N` …) in `app/build.gradle.kts`.
2. Run all tests.
3. `./gradlew :app:releaseFiles -PreleaseNotes="…"` gives `app/build/release/MotorDash-<v>.apk` + `update.json`.
4. Commit, tag `v<versionName>`, push, then
   `gh release create v<v> <apk> <update.json> --title "MotorDash <v>"`.
   **Never mark it as a pre-release**: the updater reads `releases/latest/download/update.json`,
   which skips pre-releases.

## State as of 2026-10-30
- **v0.1.0-beta.1** (versionCode 2) released on GitHub. The updater URL resolves and the
  checksum matches. CI is green.
- `API_VERSION` is 11.
- Verified only in tests or screenshots, **not yet on the car**: thumbnails, "Device connected",
  changing or moving the save folder, Navigate (maps intent), the installer step of the
  updater, the hot reload status in the dev app after the latest reinstall.
- The Arabic text was written by the agent and needs a native-speaker review.
- An explainer video was made outside the repo (`~/Videos/motordash-explainer.mp4`) from test
  screenshots with Pillow + `imageio-ffmpeg`.

## Ideas not done yet
- Built-in media player (DiLink often lacks codecs/apps for mkv/flac).
- Remember the QR address choice by network type (hotspot vs Wi-Fi) rather than IP.
- Keep received text messages across restarts.
- Optional "Not available in the dev build" Updates card in the debug app.
