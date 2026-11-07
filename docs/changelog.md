# Changelog

What changed in each release, newest first. Versions follow semantic versioning: a fix bumps the
patch (1.0.1), a new feature the minor version (1.1.0), and a change that breaks sync with older
devices or drops data the major version (2.0.0).

When you finish a change, add one short line (what changed for the user, or for the build) under
"Unreleased", in Added, Changed, Fixed or Removed. On a release, rename "Unreleased" to the version
and date.

## Unreleased

## 1.0.0

The first public release.

### Added

- **Timer:** Pomodoro work, short break and long break phases. Tags are timer presets with their
  own lengths and colour; the tag is fixed when a work phase starts. A dim clock keeps the countdown
  on a black screen.
- **Detox path:** levels from 1 h up to a goal you set, half an hour longer each time. A level passes
  when its time runs out with blocking on; a shorter, already passed level can be picked again
  without losing progress.
- **Panic button:** a 60-second grounding exercise before a detox level can be ended.
- **App blocking (Android):** a built-in accessibility service that only sees which app is in
  front, with Deep work and Detox block lists edited in the app and an on/off switch for apps that
  refuse to run beside it.
- **Stats:** focus time per day or week by tag, detox progress and the history of finished phases
  and attempts.
- **Apps during focus:** with usage access (Android) or Hush's own switch (Hyprland desktop, off by
  default), the apps in front during running work phases are recorded; on the desktop, browser
  sites and terminal programs are named from window titles, never URLs. An activity ("Doing") can
  be picked for time spent away from the device.
- **By activity:** sort an app, site or program into an activity once; Stats adds up each
  activity's time from the phases where it was picked and the apps sorted into it.
- **Desktop app (Linux):** the same timer, tags, stats and dim clock, with notifications and a
  chime; an Arch package (`hush`) built from source and the app image on each GitHub release.
- **Sync over the local network:** pair the phone and the computer with a one-time code; they
  sync with pinned, mutually authenticated TLS when the phone app opens and hourly on unmetered
  Wi-Fi. Nothing goes to the internet.
- **Releases:** APKs per CPU type plus a universal one, the desktop app image and `SHA256SUMS` on
  GitHub, each with a build provenance attestation.

### Security and privacy

- No cloud backup or device-to-device transfer of app data.
- The desktop's data and settings folders are private to the user (0700).
- The desktop listens for sync and announces itself only while paired or showing a pairing code.
- Release signing on GitHub needs the owner's approval; every APK's certificate is checked.
