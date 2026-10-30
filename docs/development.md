# Development

Built with Jetpack Compose. The dashboard UI hot reloads over the network while developing, so
the car screen updates seconds after you save. No ADB needed (DiLink doesn't offer it).

## How it's built

```
app/            Installed from USB. File server (FileServer), device web page (assets/web/index.html),
                in-app updater (update/), app shell. Hot reload client in src/debug only.
dashboard/      All the car-screen UI. Hot reloads in debug builds.
dashboard-api/  The contract between the two: DashboardUi, Host and its data classes.
devserver/      serve.py: continuous Gradle build of the dashboard, served to the car.
```

`app` implements `Host` (`FileShareHost`): server state, files, transfers, messages, updates
and actions. `dashboard` only renders a `Host` and calls its methods. That split is what makes
hot reload possible: the dashboard can be swapped at runtime without touching the file server
or transfers in progress.

**`API_VERSION`** (in `dashboard-api`) must be bumped whenever anything in `dashboard-api`
changes. The dev server refuses to send a dashboard built for a different version
("APK outdated, reinstall"), because loading it would crash.

### Device ⇄ car HTTP API (`FileServer`)
| Request | |
|---|---|
| `GET /?k=PIN` | Device web page; sets the auth cookie. Without a valid cookie: the PIN page |
| `GET /api/list[?path=]` | Folder listing |
| `PUT /api/upload?path=&name=` | Upload (whole file in one request) |
| `PUT /api/upload?path=&name=&id=&offset=&total=` | Resumable upload: append from `offset` |
| `GET /api/upload/status?path=&id=` | Bytes of a resumable upload received so far |
| `GET /api/download?path=[&k=PIN]` | Download; with `k`, works straight from a QR code |
| `GET /fonts/Dubai-<Weight>.woff2` | Web font for the device pages (no auth) |
| `POST /api/mkdir?path=&name=`, `DELETE /api/delete?path=` | Folders, deleting |
| `POST /api/text` | Text for the car screen (`text/plain`, max 16 KB) |
| `GET /api/ping` | Heartbeat from an open device page (every 15 s), for "Device connected" |

**Security.** Everything except the PIN page needs the auth cookie or the PIN. The PIN is
random per app start. After 5 wrong PINs in a row, new logins are blocked for 30 seconds
(devices already signed in keep working). Paths can't escape the shared folder.

### Languages
- Car UI text is in `dashboard/.../Strings.kt` (one `Strings` instance per language) rather than
  `res/values`. Hot-loaded code can't add Android resources, so this keeps wording changes hot
  reloadable. Use `LocalStrings.current` in composables.
- Messages the app shell shows itself (toasts, update errors) are in `app/.../AppText.kt`. It
  reads the same language preference.
- The device web page has its own `STRINGS` table in `assets/web/index.html`.
- Arabic tips: wrap Latin text inside Arabic strings with `ltr(...)` (a Unicode isolate) so
  "36 GB" doesn't become "GB 36". Use `LtrText` for addresses and PINs, and `tracking()`
  instead of fixed letter spacing (letter spacing breaks Arabic joining). Dialogs use
  `AppDialog`, which keeps the right-to-left direction Android resets for dialog windows.

### Fonts
The Dubai font may be embedded in apps but not redistributed as files, so it isn't in the repo.
Download it from [dubaifont.com](https://www.dubaifont.com) and put these files in
`app/src/main/assets/fonts/`:
`Dubai-Light`, `Dubai-Regular`, `Dubai-Medium` and `Dubai-Bold`, each as `.ttf` (car) and
`.woff2` (device web page). Without them, everything still builds and falls back to the system
font. The car loads the `.ttf` files, devices get the `.woff2` files from `/fonts/`, and the
screenshot tests use the same files.

## Requirements
- JDK 17+ (`export JAVA_HOME=…` if `java` isn't on your PATH)
- Android SDK with `platforms;android-35`: `sdk.dir` in `local.properties`, or `ANDROID_HOME`
- Car on Android 7.0+ (minSdk 24). Developed against DiLink on Android 13.

## Two apps on the car
| Build | Package | Name | Folder | Hot reload | In-app updates |
|---|---|---|---|---|---|
| debug | `app.motordash.dev` | MotorDash Dev | `Download/MotorDash Dev` | yes | no |
| release | `app.motordash` | MotorDash | `Download/MotorDash` | no | yes |

They install side by side, so testing never replaces the real app. Each has its own folder
because Android 13 hides other apps' non-media files.
Hot reload exists **only** in the debug app: its code lives in `app/src/debug` and isn't
compiled into releases.

## One-time setup
1. Start the dev server once. It generates a token and saves it to `local.properties`:
   ```bash
   ./devserver/serve.py          # listens on 127.0.0.1:8090
   ```
2. Tunnel it. You need a **stable** URL, or you'll have to retype it on the car each session:
   ```bash
   ngrok http 8090 --url=<your-static-domain>.ngrok-free.dev
   # or: cloudflared tunnel / pinggy (paid plans give fixed subdomains)
   ```
3. Add the URL to `local.properties`:
   ```properties
   motordash.devServerUrl=https://<your-static-domain>.ngrok-free.dev
   ```
4. Build the dev app and install it on the car from a USB stick:
   ```bash
   ./gradlew :app:assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
   ```

## Daily loop
```bash
./devserver/serve.py   # + your tunnel
```
Open **MotorDash Dev** on the car and edit anything in `dashboard/src`. The car reloads a few
seconds after you save. Connection status and the server URL/token are under
Settings → Technical Details → Developer.

**Reloads keep:** everything in `app/`: the file server, transfers in progress, messages.
**Reloads reset:** `remember` state inside `dashboard/` (e.g. the open page).
**Needs a reinstall of the dev APK:** changes to `app/` (including the device web page),
`dashboard-api/` (bump `API_VERSION`), new library dependencies, and Android resources in
`dashboard/`. Hot-loaded code can't add resources, so keep the dashboard UI code-only.

## Tests
```bash
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :dashboard:testDebugUnitTest
```
GitHub Actions runs the same on every push (`.github/workflows/test.yml`) and keeps the screenshots.
- `AppStartTest` launches the real app under Robolectric. **Run it before installing any APK.**
  Without ADB, a crash on launch is otherwise invisible on the car.
- `FileServerTest`: the HTTP API, PIN and lockout, resumable uploads, path safety.
- `MoveContentsTest`: moving files when the save folder changes.
- `UpdateClientTest`: update manifest, download, checksum rejection.
- `ScreenshotTest` renders the dashboard at DiLink's 1920×1080, in English and Arabic, into
  `dashboard/build/screenshots/*.png`. Handy for design work on a headless server.

## Configuration reference
| Key | File | Used by |
|---|---|---|
| `motordash.applicationId` | `gradle.properties` | Package name (debug adds `.dev`) |
| `motordash.devServerUrl`, `motordash.devToken` | `local.properties` | Debug app's hot reload defaults |
| `motordash.release.storeFile`, `…storePassword`, `…keyAlias`, `…keyPassword` (optional) | `local.properties` | Release signing ([Releasing](releasing.md)) |
| `motordash.updateUrl` | `local.properties` | Release app's update check ([Releasing](releasing.md#updates)) |

`local.properties` is git-ignored. It holds secrets, so back it up privately.

## Troubleshooting
- **App closes right after opening.** Run `AppStartTest`; it reproduces launch crashes on the
  JVM. An `AbstractMethodError` mentioning `Host` means compiled code is out of date after a
  `dashboard-api` change: run `./gradlew clean` and rebuild.
- **Dev app says "APK outdated, reinstall".** `API_VERSION` changed since the APK was built.
  Rebuild and reinstall the debug APK.
- **Hot reload stops updating after many quick builds.** If a build ran at the same time as the
  dev server's, the payload build fails on purpose ("Dashboard classes missing") and keeps the
  last good payload. Save again, or run `./gradlew :dashboard:hotPayload`.
