# AGENTS.md

Read this file before you touch code. It holds the rules; the details live in [`docs/`](docs/README.md).

## Project

**Hush** (package `app.hush`) is a minimalist focus app for Android and the Linux desktop: a
Pomodoro timer with tags, a "detox path" of levels that get longer, a panic button with a short
grounding exercise, history and stats, built-in app blocking (an accessibility service in
`:blocker-native`, behind our own `Blocker` interface), the apps used during focus, and sync between
the phone and the desktop over the local network.

Principles: one screen does one thing, few settings, no shame, friction over walls. When scope is
unclear, ask the user.

Reference code (GPL-3, read-only, cloned locally by whoever set up the session):

- **Tomato** (KMP + Compose MP + Room 3 + Koin): `tomato/`. Upstream: https://github.com/nsh07/Tomato
- **Curbox** (Android app blocker): `curbox/`. `:blocker-native` ports parts of its
  `services/AppBlockerService.kt`, `BaseBlockingService.kt` and `blockers/FocusModeBlocker.kt`.

## Rules for agents

1. **Stay minimal.** Don't add features, screens, settings, permissions or dependencies the user
   didn't ask for. If you think something is missing, tell the user. Don't build
   it.
2. **Work on the current task only.** Don't scaffold future features.
3. **Follow the [engineering guide](docs/engineering.md).** If a rule doesn't fit, don't quietly
   break it: change the rule there (with the reason) in the same change and say so.
4. **Test commonMain logic.** Every rule in `timer/`, `detox/`, `history/` and the ViewModels gets
   unit tests in `commonTest`. Use fakes, not mocks. A change to logic without a test is not done.
5. **Respect the Blocker boundary** ([guide §3](docs/engineering.md#3-the-blocker-boundary)). It's
   the one architectural rule we never bend; run its grep before finishing a change that touches
   blocking.
6. **Comments:** keep them short, and only for a non-obvious *why*. Don't narrate code. KDoc goes on
   public interfaces (`Blocker`, repositories, engine entry points) and nowhere else by default.
7. **Verify before you finish:** run the [commands](docs/commands.md) that cover what you touched.
   They must pass. Say what you ran when you report back.
8. **Don't commit** unless the user asks. Never add `local.properties`, signing keys or secrets.
9. **Licence:** the project is GPL-3.0-or-later ([guide §14](docs/engineering.md#14-licensing)). If
   you copy code from Tomato or Curbox, keep the original copyright line in the file header and name
   the source in the commit message.
10. **Add a line to the [changelog](docs/changelog.md)** under "Unreleased" for every change a user
    or packager would notice: one or two lines on what changed, not how. It isn't a work log.
    Releases follow semantic versioning.
11. **Docs:** `README.md` is for users only: what Hush does, installing the packages, and credits.
    Developer documentation lives in [`docs/`](docs/README.md). Keep this file to rules and
    pointers; put details in `docs/`. Never name or link local, uncommitted notes in committed files.

## Where things are

| Topic | File |
| --- | --- |
| Prerequisites, building, running, install scripts, signing, the Arch package | [docs/building.md](docs/building.md) |
| Every Gradle and adb command, desktop and sync notes | [docs/commands.md](docs/commands.md) |
| Engineering guide: versions, modules, Blocker boundary, architecture, Compose, Android, persistence, Koin, tests, licence | [docs/engineering.md](docs/engineering.md) |
| GitHub releases, signing secrets, CI | [docs/releasing.md](docs/releasing.md) |
| How apps, sites and terminal programs are recorded | [docs/apps-during-focus.md](docs/apps-during-focus.md) |
| Sync setup for users (firewall, pairing, desktop data) | [docs/sync.md](docs/sync.md) |
| What changed in each release | [docs/changelog.md](docs/changelog.md) |

Before you finish: `./gradlew ktlintCheck check :androidApp:assembleDebug` (plus the commands for
what you touched) must pass.
