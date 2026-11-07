# Hush

A minimalist focus app for Android and Linux with a Pomodoro timer and a "detox path".
No accounts and nothing is sent to the internet.

## Based on Tomato and Curbox

Hush is a derivative of two GPL-3.0 projects, and wouldn't exist without them:

- **[Tomato](https://github.com/nsh07/Tomato)** by Nishant Mishra: Hush started from Tomato's
  architecture and reuses its timer engine design, Material 3 Expressive look and motion, screen
  layouts, toolbar, Room and Koin setup, and several icons.
- **[Curbox](https://github.com/curbox-app/curbox-android)** by Nethical and Curbox contributors:
  Hush first blocked apps through Curbox's API, and its built-in blocker is adapted from Curbox's
  accessibility service and always-allowed rules (`AppBlockerService.kt`, `AlwaysAllowed.kt` keep
  Curbox's copyright line).

Because of this, Hush is released under GPL-3.0-or-later as well. Please support the original
projects.

## What it does

**App blocking (Android).** An accessibility service covers the apps on your block lists with a
calm block screen while a work phase or detox level runs. Turn it on once in Settings → App
blocking, then pick the apps for the Deep work and Detox lists. It only reads which app is in
front; no screen content, and nothing leaves the phone. The desktop app doesn't block apps.

Some apps refuse to run while any accessibility service installed outside the Play Store is on,
so Settings → App blocking has an on/off switch. Turning it back on from requires opening
Android's Accessibility settings so you can turn it on there.

**Apps during focus.** Stats can show which apps you used during work phases, and you can mark a
work phase with an activity with a tag such as "E-reader" for time spent away from the phone.

On Android this needs usage access, which you turn on yourself in Settings → Timer → App usage during
focus; without it nothing is recorded. The desktop app does the same on Hyprland by recording the
focused window's class.

**Sorting into activities.** Tap an app, site or program in Stats to sort it into an activity once
("Anki → Study"). Stats then adds up the time per activity under "By activity": work phases where
you picked that activity, plus the time in the apps sorted into it.

**Desktop data.** The desktop app keeps its settings in `~/.config/hush` and its history and sync
keys in `~/.local/share/hush` (or `$XDG_CONFIG_HOME/hush` and `$XDG_DATA_HOME/hush`). Uninstalling
leaves them in place.

## Sync on your network

The desktop app can sync history, tags, activities and sorting rules with a phone, directly over a local
network. The desktop listens on TCP port 47447 (another free port if that one
is taken) and announces itself over mDNS, so `avahi-daemon` must be running. If a firewall is on,
allow both:

```sh
sudo ufw allow 47447/tcp && sudo ufw allow 5353/udp                     # ufw
sudo firewall-cmd --permanent --add-port=47447/tcp --add-service=mdns \
  && sudo firewall-cmd --reload                                          # firewalld (mdns = UDP 5353)
```

### Pairing

1. On the computer, open Hush, then **Settings → Sync** ("Pair with your phone"). It shows a code
   like `K7QM-2F9X-HC4D`, valid for two minutes.
2. On the phone, open Hush, then **Settings → Sync** ("Pair with your computer"). Type the code and
   tap **Pair**. On Android 17 Hush first asks for permission to find devices on your network.
3. Both devices then show the other one's name, and the first sync runs right away.

After that the phone syncs whenever you open Hush (at most once a minute) and about every hour in
the background while it's on unmetered Wi-Fi; the computer only needs Hush open. **Unpair** forgets
the other device on this device only; pairing again replaces the old pairing. Guest networks that
isolate devices from each other block discovery.

## Install

No release has been published yet. Once one is, its files are on the
[releases page](https://github.com/MoXcz/hush/releases).

**Android** (8.0 or newer). Download the APK and open it on the phone (allow your browser or file
manager to install apps when Android asks), or install it from a computer with
`adb install <file>.apk`. Updates must be signed with the same key as the installed app; if Android
refuses one, the only way out is to uninstall, which deletes Hush's data.

**Linux desktop (Arch, x86_64).** Install the package with pacman:

```sh
sudo pacman -U hush-<version>-1-x86_64.pkg.tar.zst
```

Start it from your launcher or with `hush`. Remove it with `sudo pacman -R hush` (your settings and
history stay). Optional dependencies:

- `libnotify`: notifications when a phase ends
- `avahi`: pairing and syncing with the phone (`avahi-daemon` must be running)
- `hyprland`: apps during focus (only Hyprland sessions are supported)

To build Hush from source, see [docs/building.md](docs/building.md).

## Credits and licences

Hush is licensed under GPL-3.0-or-later, see [LICENSE](LICENSE). It builds on Tomato and Curbox,
see [Based on Tomato and Curbox](#based-on-tomato-and-curbox).

- Manrope by The Manrope Project Authors, https://github.com/googlefonts/manrope, under the SIL
  Open Font License 1.1 (`shared/src/commonMain/composeResources/files/OFL-manrope.txt`).
