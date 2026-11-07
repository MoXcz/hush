# Building Hush

How to build, run and install Hush from source. [commands.md](commands.md) has the full command
table (tests, lint, checks) and [engineering.md](engineering.md) the engineering guide.

## Prerequisites

- JDK 21. Gradle runs on it (bytecode targets 17), and the desktop app image needs its `jpackage`.
- A **user-owned, writable** Android SDK, e.g. `~/Android/Sdk`, named in `local.properties`
  (`sdk.dir=/home/<you>/Android/Sdk`) or by `ANDROID_HOME`. It needs platform `android-37.0`
  (API 37; plain `compileSdk = 37` finds it), build-tools `36.0.0`, platform-tools, and an NDK
  (`28.2.13676358` here) for stripping native libraries. AGP runs the SDK's binaries as your user
  and downloads missing parts, so a root-owned SDK such as `/opt/android-sdk` breaks the build;
  install it with `sdkmanager` as your user, or `chown -R` it.
- For the Arch package: `makepkg` (Arch Linux).

Always use the wrapper (`./gradlew`); there's no global Gradle.

## Build and run

```sh
./gradlew :androidApp:assembleDebug                      # Android debug APK
./gradlew :androidApp:installDebug                       # install it on a connected device
./gradlew :androidApp:assembleRelease                    # release APK (R8; signed, see below)
./gradlew :desktopApp:run                                # run the desktop app
./gradlew :desktopApp:createReleaseDistributable         # desktop app image
./gradlew ktlintCheck check :androidApp:assembleDebug    # everything before a change is done
```

The debug app (`app.hush.debug`, "Hush (debug)") and the release app (`app.hush`) are separate
installs with separate data, sync peers and accessibility services. Pair and use the release app
for real data.

The desktop app image is
`desktopApp/build/compose/binaries/main-release/app/Hush/bin/Hush`. It bundles its own Java runtime
(about 150 MB), so the `Hush` folder can be copied anywhere and started from `bin/Hush`.

### Trying the desktop app without touching your data

The desktop app keeps its database and sync identity in `$XDG_DATA_HOME/hush` (default
`~/.local/share/hush`) and its settings file, which also holds the device id and the sync peer, in
`$XDG_CONFIG_HOME/hush` (default `~/.config/hush`). Only one instance runs per data and config
directory. Point both at temporary directories to run a second, throwaway instance:

```sh
XDG_DATA_HOME=$(mktemp -d) XDG_CONFIG_HOME=$(mktemp -d) ./gradlew :desktopApp:run
```

The sync host listens on TCP 47447 and runs `avahi-publish`; check it with
`avahi-browse -rt _hush._tcp`.

## Install scripts

```sh
scripts/install-desktop.sh                # Arch: build the `hush` package and install it with pacman
scripts/install-desktop.sh --uninstall    # remove the package (settings and data stay)
scripts/install-android.sh                # phone: signed release over adb, then start it
```

`scripts/install-android.sh` takes:

- `--debug`: install the debug app instead (no keystore needed).
- `--pair HOST:PORT CODE`: pair with a phone's wireless debugging first.
- `--connect HOST:PORT`: connect to that phone over wireless debugging.

Without `--pair`/`--connect` it uses `ANDROID_SERIAL`, else the one device adb sees, else a phone
that advertises wireless debugging. After installing it grants `WRITE_SECURE_SETTINGS` (so the
blocking switch can turn blocking back on), starts Hush and reports a crash on start.

To grant it by hand (for example after installing the APK without the script):

```sh
adb shell pm grant app.hush android.permission.WRITE_SECURE_SETTINGS
```

Use `app.hush.debug` for the debug app. The grant survives updates but not an uninstall.

## Signing the release

`androidApp/build.gradle.kts` signs `release` with the key named in a git-ignored
`keystore.properties` at the repo root:

```properties
storeFile=/path/to/hush-release.jks
storePassword=...
keyAlias=hush
keyPassword=...
```

The file belongs to this checkout, so no global Gradle config is needed. Without it the release
build is still produced, just unsigned (`androidApp-<abi>-release-unsigned.apk`), so other machines and
CI don't break; `scripts/install-android.sh` refuses to install an unsigned release.

Keep the keystore outside the repo (`*.jks` is ignored anyway) and outside Hush's own data and
config directories (deleting those to reset Hush must not delete the key), and **back it up**: an installed
release can only be updated with the same key, and losing it means uninstalling, which wipes the
app's data.

To install the signed release by hand, `./gradlew :androidApp:installRelease` finds the device
through `ANDROID_SERIAL` (`adb mdns services`, then `adb connect <ip:port>` for wireless
debugging):

```sh
ANDROID_SERIAL=<device> ./gradlew :androidApp:installRelease
```

## The Arch package

`scripts/install-desktop.sh` builds the app image, then runs `makepkg -fsi --cleanbuild` on
`packaging/arch/PKGBUILD`. To build the package without installing it (after
`:desktopApp:createReleaseDistributable`):

```sh
cd packaging/arch && BUILDDIR=$PWD/../../build/arch PKGDEST=$PWD/../../build/arch makepkg -f
```

The package lands in `build/arch/hush-<version>-1-x86_64.pkg.tar.zst`. It installs the image in
`/usr/lib/hush`, a `/usr/bin/hush` wrapper (not a symlink: the launcher finds its runtime from its
own path), `hush.desktop` with `StartupWMClass=app-hush-desktop-MainKt`, and hicolor icons.
makepkg works under `build/arch` and needs no network; `options=('!strip' '!debug')` keeps the
bundled runtime intact. The version comes from `app-versionName` in `gradle/libs.versions.toml`.
The PKGBUILD reads the image from the checkout, so it can't be built from a clean clone or
published to the AUR as is.

If the native libraries change, re-check `depends` with `readelf -d` on `bin/Hush` and every `.so`
in the image (the runtime is jlinked from Arch's JDK, so it links the system's libjpeg, giflib,
libpng, harfbuzz and lcms2).

If a new dependency needs another JDK module at runtime, run
`./gradlew :desktopApp:suggestRuntimeModules` and update `modules(...)` in
`desktopApp/build.gradle.kts`. It can't see security providers: `jdk.crypto.ec` (EC keys and TLS
for sync) is listed by hand, and only the packaged image shows a missing one (`:desktopApp:run`
uses the full JDK).
