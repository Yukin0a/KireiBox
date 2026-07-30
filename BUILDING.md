# Building KireiBox

This document contains public, reproducible Debug build instructions without changing the project's original `README.md`.

## Required toolchain

- JDK 17
- Android SDK Platform 36
- Android NDK `27.0.12077973`
- CMake `3.22.1`
- Gradle 8.13 through the committed Gradle Wrapper

The Android SDK path belongs in an untracked `local.properties` file:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Debug build gates

Linux / macOS:

```bash
./gradlew test :app:lintDebug :app:assembleDebug
```

Windows:

```powershell
.\gradlew.bat test :app:lintDebug :app:assembleDebug
```

The generated Debug APK is a local build artifact under `app/build/outputs/apk/debug/`. It is ignored by Git and is not uploaded by the repository's CI.

## Optional metadata-source credentials

Ymgal and Hikarinagi use OAuth client credentials. Public builds do not contain client IDs, client secrets, or access tokens. If the variables below are absent, the application still builds; only the corresponding metadata source is unavailable.

| Environment variable | Purpose |
| --- | --- |
| `KIREIBOX_YMGAL_CLIENT_ID` | Ymgal OAuth Client ID |
| `KIREIBOX_YMGAL_CLIENT_SECRET` | Ymgal OAuth Client Secret |
| `KIREIBOX_HIKARINAGI_CLIENT_ID` | Hikarinagi OAuth Client ID |
| `KIREIBOX_HIKARINAGI_CLIENT_SECRET` | Hikarinagi OAuth Client Secret |

Set only the variables needed for the current build, then rerun Gradle. Never commit real values to source, Gradle files, logs, issues, or CI configuration.

## Continuous integration

`.github/workflows/android-debug.yml` runs JVM tests, `lintDebug`, and `assembleDebug` for `main` pushes and pull requests. It does not read repository secrets, run a Release build, upload an APK, create a GitHub Release, or create a tag.
