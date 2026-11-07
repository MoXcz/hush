# Build and verify

Prerequisites (JDK 21 and a **user-owned, writable** Android SDK with platform `android-37.0`,
build-tools `36.0.0`, platform-tools and an NDK), the install scripts, release signing and the Arch
package are described in [building.md](building.md). Always use the wrapper
(`./gradlew`). There's no global Gradle.

| What                                    | Command                                                   |
| --------------------------------------- | --------------------------------------------------------- |
| Fast logic tests (commonTest + jvmTest) | `./gradlew :shared:jvmTest`                                |
| Shared tests on the Android host        | `./gradlew :shared:testAndroidHostTest`                    |
| Built-in blocker unit tests             | `./gradlew :blocker-native:testDebugUnitTest`              |
| Android app unit tests (sync helpers, Koin verify) | `./gradlew :androidApp:testDebugUnitTest` (also in `check`) |
| LAN sync transport tests (also in `check`) | `./gradlew :sync-lan:test`                              |
| Build the app                           | `./gradlew :androidApp:assembleDebug`                      |
| Android Lint                            | `./gradlew :androidApp:lintDebug`                          |
| Format check / auto-fix                 | `./gradlew ktlintCheck` / `./gradlew ktlintFormat`         |
| Everything (run before you finish)      | `./gradlew ktlintCheck check :androidApp:assembleDebug`    |
| Release build (R8; signed if `keystore.properties` exists) | `./gradlew :androidApp:assembleRelease`  |
| Publish a release (APKs per CPU type + desktop tarball) | push a `v<version>` tag; see `docs/releasing.md` |
| CI on GitHub (push to main, pull requests, before a release) | `.github/workflows/ci.yml` runs `./gradlew ktlintCheck check :androidApp:assembleDebug` |
| Install on a device                     | `./gradlew :androidApp:installDebug`                       |
| Run the background sync now (paired release app) | `adb shell cmd jobscheduler run -f app.hush 1100` (`app.hush.debug` for debug) |
| Install the signed release              | `ANDROID_SERIAL=<device> ./gradlew :androidApp:installRelease` |
| Build against FakeBlocker               | `./gradlew :androidApp:installDebug -PfakeBlocker=true`    |
| Desktop unit tests                      | `./gradlew :desktopApp:test` (also part of `check`)        |
| Run the desktop app                     | `./gradlew :desktopApp:run`                                |
| Desktop app image (Linux)               | `./gradlew :desktopApp:createReleaseDistributable` → `desktopApp/build/compose/binaries/main-release/app/Hush/bin/Hush` |
| Install the desktop app (Arch package)  | `scripts/install-desktop.sh` (`--uninstall` removes it)    |
| Build the Arch package only             | `cd packaging/arch && BUILDDIR=$PWD/../../build/arch PKGDEST=$PWD/../../build/arch makepkg -f` (after the app image) |
| Install the release on a phone (checks) | `scripts/install-android.sh [--debug] [--pair HOST:PORT CODE] [--connect HOST:PORT]` |

Don't build `:desktopApp` (`check`, `createReleaseDistributable`) while `:desktopApp:run` is running
from the same checkout: the build rewrites the jar the running app loads classes from, and its
recording fails silently. Check with `pgrep -af desktopApp.jar`.

If a task name doesn't exist (KMP test task names change between AGP versions), list them with
`./gradlew :shared:tasks --group verification` and update this table. (`testAndroidHostTest` is
the name with AGP 9.4.1.)

**Desktop app.** The database and the sync identity (`sync/`) live in `$XDG_DATA_HOME/hush`
(default `~/.local/share/hush`), the settings file (`settings.preferences_pb`, which also holds the
device id and the sync peer) in `$XDG_CONFIG_HOME/hush` (default `~/.config/hush`; 0.1.0 kept it in
the data directory, and the app moves it on start unless the new one exists). Only one instance
runs per data and config directory (a `.lock` in each). Both directories are made private (0700)
on every start, existing ones included (`makePrivateDirectory`), so other users can't read them. Trying it with scratch directories, the app
image, the Arch package (layout, `depends`, `!strip`) and the JDK runtime modules (`jdk.crypto.ec`
is listed by hand) are in [building.md](building.md). The sync host listens on TCP 47447
and runs `avahi-publish` only while a phone is paired or the Sync screen shows a code; check it with `avahi-browse -rt _hush._tcp`.

**Sync on a device.** The phone app finds the desktop with NSD and syncs when it opens (at most once
a minute) and from an hourly JobScheduler job (`SYNC_JOB_ID = 1100`, unmetered Wi-Fi). `adb shell cmd
jobscheduler run -f app.hush 1100` runs the job now (`-f` ignores the network constraint);
`adb shell dumpsys jobscheduler app.hush` shows whether it's scheduled. Pair the **release** app, the
one with the real data: debug and release are separate installs with separate peers.

**Signed release.** `androidApp/build.gradle.kts` signs `release` with the key named in a
git-ignored `keystore.properties` at the repo root (format and backup in
[docs/building.md](building.md#signing-the-release)); without it the release is built
unsigned. The debug app (`app.hush.debug`) is a separate install with its own data.

**Why the SDK must be user-owned:** AGP runs SDK binaries (`aidl`, the NDK's `llvm-strip`) as the
build user and downloads missing components (it fetched build-tools `36.0.0` on its own here). A
root-owned SDK such as `/opt/android-sdk` with non-executable binaries breaks both (`aidl` fails,
and `assembleRelease` fails at `stripReleaseDebugSymbols`). Don't work around it in the build; fix
the SDK instead (install it with `sdkmanager` as your user, e.g. in `~/Android/Sdk`, or `chown -R`
it).

**The Gradle wrapper** (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) is Gradle's
own Apache-2.0 code. `gradle/wrapper/gradle-wrapper.properties` pins the distribution URL and its
`distributionSha256Sum` (from https://gradle.org/release-checksums/); update both together.
