# Releasing

## One-time: release key
Releases are signed with a key that lives **outside the repo**. Android only accepts updates
signed with the same key, so if it's lost, every later update needs an uninstall on the car.
Back up the keystore file *and* `local.properties` (it holds the password).
```properties
# local.properties
motordash.release.storeFile=/home/you/.motordash/motordash-release.jks
motordash.release.storePassword=...
motordash.release.keyAlias=motordash
```
Without these, release builds fail instead of producing an unsigned or debug-signed APK.

## Package name
The app is `app.motordash` (`motordash.applicationId` in `gradle.properties`; the dev app is
`app.motordash.dev`). Kotlin packages and folders use the same name. Reverse-domain names are
only a convention, so no domain is needed: sideloaded apps just need a name no other app on the
car uses.
**Never change it after a release.** Android treats a new name as a different app: it installs
next to the old one, gets no updates from it, and starts with empty app data.

## Versions and betas
- `versionCode` must go up by one for **every** APK you hand out. Android and the updater compare
  only this number.
- `versionName` is what people see. Betas lead up to a release: `0.1.0-beta.1`, `-beta.2`, …,
  then `0.1.0`. A name containing "beta" shows a **Beta** label in Settings.
- On GitHub, **don't** tick "Set as a pre-release" for betas: `…/releases/latest/download/update.json`
  skips pre-releases, so beta users would never get the update. Put "beta" in the title instead.

## Each release
1. Bump `versionCode` (+1, must always increase) and `versionName` in `app/build.gradle.kts`.
2. Run the tests (see [Development](development.md#tests)).
3. Build the release files:
   ```bash
   ./gradlew :app:releaseFiles -PreleaseNotes="What changed, shown in the app"
   # -> app/build/release/MotorDash-<version>.apk and update.json
   ```
4. Commit and tag: `git tag v<versionName>`.
5. Publish both files (see [Hosting](#hosting)). For a first install, copy the APK to a USB stick.

## Updates
Release builds check `motordash.updateUrl` 30 s after start and then every 6 hours, or when you
tap **Check now** in Settings → Updates. When a newer `versionCode` is out, the Settings icon
gets a green dot. **Update** downloads the APK, checks it, and opens Android's installer. The user
confirms once, because sideloaded apps can't install silently. The first time, Android asks to
allow MotorDash to "install unknown apps".

Downloads are verified twice: the SHA-256 from `update.json`, and the APK must be signed with
the same key as the installed app. If an update fails with "isn't signed with the MotorDash
release key", it was built with another keystore.

### Hosting
`update.json` and the APK must sit side by side (`apk` in `update.json` is relative) at a
stable URL that always points at the latest release. With GitHub Releases on a **public** repo:
1. Create a release for the tag and attach both files from `app/build/release/`.
2. One-time, in `local.properties`, then rebuild the release:
   ```properties
   motordash.updateUrl=https://github.com/mohdbk/motordash/releases/latest/download/update.json
   ```
Any static web host works the same way. If `motordash.updateUrl` is empty, the Updates section
is hidden. The car needs internet to check (its own connection or a device's hotspot).
