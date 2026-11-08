# Hush

> Vibe coded app for personal use. I really like [Tomato](https://github.com/nsh07/Tomato) but wanted some extra things. The release is also just for personal use.
> If you wish to contribute or support I would strongly suggest going to [Tomato](https://github.com/nsh07/Tomato) and [Curbox](https://github.com/curbox-app/curbox-android) repos
> instead. Support them instead!

A minimalist focus app for Android and Linux: a Pomodoro timer, a "detox path" of longer and longer
breaks from your phone, and app blocking. No accounts, and nothing is sent to the internet.

<p align="center">
  <img src="docs/images/hero.png" alt="The timer on the desktop and stats on a phone" width="640">
</p>

[Download for Android](https://github.com/MoXcz/hush/releases/latest) ·
[Download for Linux](#linux-x86_64) ·
[Build from source](docs/building.md)

Hush builds on [Tomato](https://github.com/nsh07/Tomato) and
[Curbox](https://github.com/curbox-app/curbox-android); see [Credits](#credits-and-licences).

## What it does

<table>
  <tr>
    <td><img src="docs/images/timer.png" alt="Timer" width="220"></td>
    <td><img src="docs/images/detox.png" alt="Detox path" width="220"></td>
    <td><img src="docs/images/stats.png" alt="Stats by activity" width="220"></td>
    <td><img src="docs/images/block.png" alt="Block screen" width="220"></td>
  </tr>
  <tr>
    <td><b>Timer.</b> Work and break phases, with tags as presets.</td>
    <td><b>Detox path.</b> Levels from 1 h up to your goal.</td>
    <td><b>Stats.</b> The apps used during focus, by activity.</td>
    <td><b>Blocking.</b> A calm screen over the apps you block.</td>
  </tr>
</table>

- **App blocking (Android).** While a work phase or detox level runs, the apps on your Deep work or
  Detox list are covered by a block screen. It only reads which app is in front. Turn it on in
  Settings → App blocking.
- **Apps during focus.** Stats can show which apps you used during work phases. On Android this
  needs usage access (Settings → Timer → App usage during focus); on the desktop it works on
  Hyprland and is off until you turn it on.
- **Sorting into activities.** Tap an app, site or program in Stats to sort it into an activity
  once ("Anki → Study"). "By activity" then adds up the time per activity.
- **Sync.** The phone and the computer sync directly over your home network. See
  [Sync](#sync-with-your-computer).

### If blocking doesn't turn on

- **"Restricted setting" when you turn on the accessibility service.** Android blocks this for apps
  installed from a downloaded APK. Open Settings → Apps → Hush, tap ⋮ → **Allow restricted
  settings**, then try again.
- **Some apps refuse to run** while an accessibility service from outside the Play Store is on. Use
  the switch in Settings → App blocking to turn blocking off; turning it back on takes you to
  Android's Accessibility settings.
- **Android 16 Advanced Protection** turns off accessibility services like Hush's, so blocking
  can't be used while it's on. The timer and stats still work.

## Sync with your computer

1. On the computer, open **Settings → Sync**. It shows a code like `K7QM-2F9X-HC4D` for two minutes.
2. On the phone, open **Settings → Sync**, type the code and tap **Pair**.
3. Both devices show the other's name and sync right away.

After that the phone syncs when you open Hush and about every hour on Wi-Fi; the computer only needs
Hush open. History, tags, activities and sorting rules are synced. The computer needs
`avahi-daemon` running, and if you use a firewall, see [docs/sync.md](docs/sync.md).

## Install

Files for each version are on the [releases page](https://github.com/MoXcz/hush/releases).

### Android (8.0 or newer)

Download `hush-<version>-arm64-v8a.apk` (almost every current phone) or the `universal` APK if
you're not sure, open it and allow your browser or file manager to install apps. Updates must come
from the same source: Android refuses an update signed with a different key, and uninstalling
deletes Hush's data.

### Linux (x86_64)

Download `hush-<version>-linux-x86_64.tar.gz`, unpack it anywhere and run `Hush/bin/Hush`. It
brings its own Java runtime and is built on Arch Linux. Optional: `libnotify` (notifications),
`avahi` (sync) and `hyprland` (apps during focus). Your settings and history live in
`~/.config/hush` and `~/.local/share/hush`, so deleting the folder keeps them.

On Arch you can also build a pacman package from source; see
[docs/building.md](docs/building.md).

## Credits and licences

Hush is licensed under GPL-3.0-or-later, see [LICENSE](LICENSE). It is a derivative of two GPL-3.0
projects and wouldn't exist without them; please support them:

- **[Tomato](https://github.com/nsh07/Tomato)** by Nishant Mishra: Hush started from Tomato's
  architecture and reuses its timer engine design, Material 3 Expressive look and motion, screen
  layouts, toolbar, Room and Koin setup, and several icons.
- **[Curbox](https://github.com/curbox-app/curbox-android)** by Nethical and Curbox contributors:
  Hush's built-in blocker is adapted from Curbox's accessibility service and always-allowed rules
  (`AppBlockerService.kt`, `AlwaysAllowed.kt` keep Curbox's copyright line).
- **Manrope** by The Manrope Project Authors, https://github.com/googlefonts/manrope, under the SIL
  Open Font License 1.1 (`shared/src/commonMain/composeResources/files/OFL-manrope.txt`).
