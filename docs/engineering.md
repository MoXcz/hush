# Engineering guide

How Hush is built and why. Each section gives the rule, then the reasons; where Hush deliberately
differs from Tomato (the project it started from), it says so.

## 1. Versions and the version catalog

- All versions live in `gradle/libs.versions.toml`; build files only use `libs.*` aliases. Plugins
  are declared once in the root `build.gradle.kts` with `apply false`. No `buildSrc` or convention
  plugins until a third Android module needs the same config.
- Use stable versions released at least two weeks earlier (Dependabot's cooldown enforces the same
  for GitHub Actions). Check a version with Maven's `maven-metadata.xml` and the `.pom`'s
  `Last-Modified`; Google artifacts are at `https://dl.google.com/dl/android/maven2/`.
- Add a library to the catalog only with the change that first uses it.

Main versions: Gradle 9.7.1, AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Compose Multiplatform 1.12.2,
Material3 1.12.0-alpha03, lifecycle 2.11.0, Navigation 3 1.1.2, coroutines 1.11.0,
kotlinx-serialization 1.11.0, Room 3 3.0.3, sqlite-bundled 2.7.1, Koin BOM 4.2.2, DataStore 1.3.0,
kotlinx-datetime 0.8.0, ktlint 1.8.0 (plugin 14.2.0). SDK levels: `compileSdk = 37`,
`targetSdk = 37`, `minSdk = 26`, `jvmTarget = 17`.

Why some versions are what they are:

- **Gradle 9.7.1, not 9.8:** AGP 9.4.1 prints `Configuration.setVisible()` deprecation warnings on
  Gradle 9.8.
- **Material3 alpha:** the Material 3 Expressive components (`MaterialExpressiveTheme`,
  `ButtonGroup`, shape-morphing buttons, `SegmentedListItem`, floating toolbar) aren't in a stable
  Material3. `1.12.0-alpha03` is the version CMP 1.12.2 maps; the expressive APIs are opted in once
  in `shared/build.gradle.kts`. Move to a stable 1.12+ when JetBrains ships one.
- **SDK 37:** Compose 1.12, lifecycle 2.11 and core 1.19 require `minCompileSdk=37`. Plain
  `compileSdk = 37` finds `platforms/android-37.0`; code that needs a platform file asks AGP for it
  (e.g. `androidComponents.sdkComponents.aidl`).
- **No detekt:** 1.23.x lags behind Kotlin 2.4 and 2.0 is alpha. Android Lint covers the
  Android-specific checks.

## 2. Modules and source sets

```
shared/          KMP library (com.android.kotlin.multiplatform.library): all logic and all UI
                 (Compose MP), the Blocker interface, FakeBlocker. Targets android and jvm.
androidApp/      the Android app: Application, MainActivity, TimerService, notifications, alarms,
                 Koin wiring, sync client, usage reader
blocker-native/  com.android.library: the accessibility-service blocker and its block screen
desktopApp/      kotlin("jvm") + Compose Desktop: main(), window, Koin wiring, alerts, XDG
                 directories, sync host, focused-window tracking (Hyprland)
sync-lan/        kotlin("jvm"), JDK APIs only: identity, pinned mutual TLS, framing, pairing,
                 SyncServer, SyncClient
```

Dependencies: `androidApp -> shared, blocker-native, sync-lan`; `blocker-native -> shared`;
`desktopApp -> shared, sync-lan`. `shared` and `sync-lan` depend on nothing of ours.

Why:

- AGP 9's KMP plugin can't be combined with the application or library plugins, so every app entry
  point is its own module (as in Tomato).
- `jvm()` in `shared` serves the desktop, gives fast host tests (`:shared:jvmTest`), and fails the
  build if Android types leak into commonMain.
- `:blocker-native` as its own plain library makes the Blocker boundary (§3) a Gradle rule, not a
  convention. Its package is `app.hush.blocker.builtin` (`native` is a Java keyword).
- `:sync-lan` is sockets, TLS and `java.security`, which commonMain can't use, and the same code runs
  on Android and desktop. It's blocking (callers use `dispatchers.io`), knows nothing of `shared`
  (payloads are bytes), and may only use Java APIs present on Android API 26 (no `HexFormat`,
  `readNBytes`, `List.of`). Lint doesn't run `NewApi` on a plain JVM module, so after changing it,
  check its class files (`javap -c`) against `platforms/android-37.0/data/api-versions.xml`: every
  `java/`/`javax/` method must have `since` ≤ 26.
- Platform services live in the app modules, not `shared/jvmMain` (**differs from Tomato**);
  `jvmMain`/`androidMain` only hold small actuals (§9). What a device can do is a `DeviceFeatures`
  value bound per platform and read by ViewModels, so screens hide what can't work there.
- Compose tooling only on `androidRuntimeClasspath` (Tomato ships it in commonMain). Compose
  resources live in `shared/src/commonMain/composeResources`; only `androidApp` has `res/`.

## 3. The Blocker boundary

Nothing outside `:blocker-native` may know *how* apps are blocked: no accessibility service,
`AccessibilityEvent`, block activity, `ENABLED_ACCESSIBILITY_SERVICES` or session-store keys anywhere
else, with **one** exception: `androidApp/.../di/BlockerModule.kt`, which binds `Blocker`,
`BlockerSetup` and the engine's session store through `NativeBlockerFactory`. Shared code speaks of
"the blocker". Block lists are app data: they live in `shared` (Room, `BlockListRepository`), the UI
edits them, the engine reads them.

Check it before finishing any change that touches blocking:

```sh
grep -rniIE 'accessibility|blocker\.builtin|BlockActivity|AppBlockerService|curbox' \
  shared/src androidApp/src desktopApp/src --include='*.kt' --include='*.kts' --include='*.xml' \
  | grep -v di/BlockerModule.kt | grep -v 'composeResources/values/strings.xml'
# must print nothing (user-facing strings may say "Accessibility settings": that's what the user taps)
```

The contract (`shared/.../blocker/`): `Blocker` has `availability()` (`NeedsApproval`,
`Unsupported`, `Ready`), `profiles()`, `start(profile, minutes)` and `stop()` (`Ok`, `Denied`,
`Refused`, `ConnectionLost`), and `activeSession()` (`Active`, `None`, `Unknown`). **`Unknown` is
not `None`:** only a positive "inactive" counts as inactive, so a failed read never fails a detox
level or starts a second session. `BlockerSetup` opens the system settings, lists blockable apps
and turns the service on or off. `FakeBlocker` (commonMain, also used for previews) and the
desktop's `NoBlocker` implement both. `ProfileRole` maps the timer to the `deep` list and the path
to `detox`; an empty list means no profile, so the timer runs unblocked.

Why: domain code depends only on an interface it owns, so the engine could be swapped (Curbox's API
was replaced by the built-in engine without touching timer, path or history) and the rules are
testable without a device.

## 4. Packages and naming

Base package and `applicationId` `app.hush` (permanent); debug builds add `.debug` and install side
by side, each with its own data and accessibility service. Packages are by feature first:
`core`, `blocker`, `blocklists`, `timer`, `detox`, `panic`, `history`, `tags`, `activities`,
`rules` (app → activity), `usage`, `stats`, `settings`, `sync`, `data/db`, `data/prefs`, `ui`, `di`,
each with a `ui/` subpackage for its screens.

| Thing | Convention | Example |
| --- | --- | --- |
| Screen | stateless `<Feature>Screen` + `<Feature>Route` that wires the VM | `TimerScreen`, `TimerRoute` |
| UI state / intents | `@Immutable data class <Feature>UiState`, `sealed interface <Feature>Action` | `TimerAction.Start` |
| ViewModel | `<Feature>ViewModel` with one public `onAction()` | `TimerViewModel` |
| Repository | interface `XRepository`, implementation named by how it works | `RoomSessionRepository` |
| Fakes | `Fake` prefix | `FakeBlocker` |
| Flows | nouns, no `get…Stream` | `val sessions: Flow<List<Session>>` |
| Platform actuals | `File.android.kt`, `File.jvm.kt` | |
| Room entities | `<Name>Entity`, mapped to domain models in the repository | `SessionEntity` |
| Tests | backtick sentences | `` `level passes when end time reached` `` |

## 5. Architecture

- **Data layer** (repositories, `Blocker`): one source of truth per kind of data; `Flow` for
  observed data, `suspend` for one-shot operations; entities mapped to domain models.
- **Domain logic** in plain Kotlin classes in commonMain (`PomodoroEngine`, `DetoxPath`,
  `TimerController`, `DetoxController`), no Compose, Android or Koin imports, no use-case classes.
- **UI layer:** one ViewModel per screen with one `uiState: StateFlow<XUiState>` and one
  `onAction(XAction)`. No `Context` in ViewModels. No one-off event channels: things the UI must
  react to are state it acknowledges (e.g. `showGrounding` → `GroundingShown`).

Why: the timer is driven by the UI, a foreground service and an alarm at once; one owner of its
state (`TimerController`, a Koin single) keeps them from drifting. **Differs from Tomato**, whose
`TimerManager` takes many callbacks and whose global `StateRepository` is written by everyone: here
collaborators are injected interfaces and only the controller writes its state.

## 6. Timer engine and time

- Store timestamps and derive the rest: `remaining = plannedEnd - now`. The once-a-second loop only
  republishes. Pause stores the remaining duration; resume sets a new end time.
- Inject `kotlin.time.Clock`; never call `Clock.System` in logic. Persist instants as epoch ms.
- The Pomodoro state is a `TimerSnapshot`, saved on every change through `TimerStateStore`
  (`timer_*` keys in the settings DataStore) and restored at start. A running detox attempt is
  restored from its open `Session` row (`endedAt == null`).
- History is written inside the owner's lock: a work phase when it expires (`Completed`) or is
  skipped or reset after starting (`Stopped`); a detox attempt exactly once.
- Phase and level end is one idempotent `expireIfDue()` under a `Mutex`, because the loop, the alarm
  and app resume can all call it.

Why: detox levels last hours and survive reboots and process death, and pass or fail is decided by
comparing now with the stored end time. **Differs from Tomato**, which uses a since-boot clock and
drops a session on reboot; the trade-off is that changing the system time can "pass" a level,
accepted under friction over walls.

## 7. Kotlin practices

- No `GlobalScope` or ad hoc scopes: long-lived work runs in the injected `ApplicationScope`
  (`SupervisorJob() + dispatchers.default`, the only `CoroutineScope` in the graph); UI work in
  `viewModelScope`; the service in its own scope cancelled in `onDestroy`.
- Inject `AppDispatchers(io, default)`. Classes that block switch to `io` themselves (main-safe).
- Never swallow `CancellationException`: no `runCatching` around suspend calls; catch specific
  exceptions.
- Expected failures are sealed result types handled with exhaustive `when`; no `kotlin.Result` in
  our APIs. Exceptions are for bugs.
- `val` and read-only collections in state; `kotlin.time.Duration` everywhere except storage and
  `Blocker.start(minutes)`; `@JvmInline value class` ids. `private` by default, no `explicitApi()`.
- Official code style, ktlint on every module (`build/` excluded), `.editorconfig` allows
  `@Composable` function names.

## 8. Compose practices

- **Hoist state:** `XRoute(viewModel = koinViewModel())` collects with
  `collectAsStateWithLifecycle()` and passes plain values to `XScreen(state, onAction, modifier)`.
  Every emitting composable takes `modifier: Modifier = Modifier`. Previews only use `XScreen`s.
- **Effects:** `LaunchedEffect` for keyed suspend work (the dim clock's per-minute drift),
  `DisposableEffect` for anything to undo (window flags, brightness), `LifecycleResumeEffect` to
  re-check the blocker or usage access on resume.
- **Navigation 3:** `@Serializable sealed interface Route : NavKey`, `rememberNavBackStack`,
  `NavDisplay` with the ViewModel-store entry decorator. The dim clock is a destination so the ring
  can move there with a shared-element transition. **Differs from Tomato**, which keeps the back
  stack inside a ViewModel.
- **Look ("Dusk"):** Material 3 Expressive components and motion, but Hush's own identity: one muted
  slate-indigo accent (seed `#5B6395`) on cool greys, all roles generated into `ui/theme/Color.kt`
  (regenerate, don't hand-edit; WCAG AA in both themes); the accent only on the primary action, the
  progress ring and the selected tab. Font Manrope (OFL, Latin subset with tabular figures). Light
  and dark follow the system; no dynamic colour. The dim clock is black with `#A2A2A2` text.
- **Motion lives in components:** page changes cross-fade with `defaultEffectsSpec()`; the floating
  toolbar sits outside `NavDisplay` and never moves on a page change; the navigation host is never
  padded (each route gets the bar padding), so hiding the bar for the dim clock doesn't resize pages.
  Use the theme's `motionScheme` specs; never hard-code durations.
- Grouped lists are segmented rows (`SegmentedListItem` with `segmentedShapes`); acknowledged
  messages use `AnimatedNotice`.

## 9. Expect/actual vs interfaces

Platform services (blocker, notifications, wakeups, sync ports, app usage) are interfaces in
commonMain, implemented per platform and bound in Koin. `expect`/`actual` only for small
`@Composable` effects (dim clock window effect), platform builder paths (Room, DataStore) and
constants; never `expect class`. Why: interfaces can have real, fake and alternative
implementations at once.

## 10. Android and desktop specifics

**Timer service.** A `specialUse` foreground service runs only while a Pomodoro phase is running and
shows one notification with a system chronometer. A detox attempt runs no service: it's timestamps
plus a wakeup. `POST_NOTIFICATIONS` is asked on the first Start; two channels (ongoing timer, phase
or level end).

**Wakeups.** One `AlarmManager` scheduler behind `WakeupScheduler`, one `PendingIntent` per purpose:
the phase end is exact (`setExactAndAllowWhileIdle`, falling back to inexact), the detox level check
inexact. The level check runs one minute before the end and again at the end, because at the end a
`None` from the blocker can't be told from an early stop. The alarm only wakes the app; the decision
is `expireIfDue()` and, for detox, `activeSession()`. The boot receiver is enabled only while an
attempt is open. Not WorkManager (15-minute minimum, not for one-shots, one more dependency).

**The built-in blocker.** An accessibility service listening only to window changes
(`typeWindowStateChanged`, `canRetrieveWindowContent="false"`, `isAccessibilityTool="false"` set
explicitly: claiming to be a tool breaks Play and Advanced Protection policy). The running block is a
list id, name and end time in the settings DataStore; list edits apply mid-session. The block screen
(`BlockActivity`) hosts shared's `BlockedScreen`; detox offers "I need a break" into the panic flow.
Never blocked: Hush, launchers, system UI, settings, dialer, the keyboard. With `WRITE_SECURE_SETTINGS`
(granted over adb) the on/off switch edits `ENABLED_ACCESSIBILITY_SERVICES`; without it, off is
`disableSelf()` and on opens the system settings. Turning blocking off fails a running detox level at
the next check. Downloaded APKs need "Allow restricted settings" (Android 13+) before the service can
be turned on.

**LAN sync client (phone).** The phone only connects, it never listens. Permissions: `INTERNET`
(local sockets only; `:sync-lan` refuses non-local addresses: private, link-local, ULA, or inside a
prefix of the phone's own interfaces), `ACCESS_NETWORK_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, and
`ACCESS_LOCAL_NETWORK` on Android 17, asked when the user taps Pair. Discovery uses NSD
`_hush._tcp` (a new listener per search, one resolve at a time, IPv4 first). A sync tries the
remembered address first, then discovery; `Rejected` never unpairs. Syncs run when the app opens (at
most once a minute) and from an hourly JobScheduler job on unmetered Wi-Fi (`SYNC_JOB_ID = 1100`);
cancelling closes the socket at once.

**Desktop sync host.** Listens on TCP 47447 (any free port as fallback) and advertises with
`avahi-publish` only while a phone is paired or the Sync screen shows a pairing code, so an unpaired
computer opens no port and announces nothing.

**Apps during focus.**

- *Android:* `PACKAGE_USAGE_STATS` is granted by the user under "Usage access", never requested at
  runtime. One `queryEvents` per finished work phase; `ForegroundTime.perApp` (pure) pairs resumed
  and paused events, one app in front at a time, clipped to the phase, visits under 5 s dropped;
  Hush, the default launcher and system UI left out. `WorkUsageRecorder` runs in the
  `ApplicationScope`, outside the timer's lock; a failed read loses that phase's apps, never crashes.
- *Desktop (Hyprland only):* off by default; Hush's own switch (`UsageSwitch`,
  `desktop_app_usage`). `FocusTracking` runs `HyprlandWatcher` only while the switch is on and a
  work phase runs. The watcher reads Hyprland's event socket (`activewindow>>CLASS,TITLE`) and keeps
  the last 24 h in memory as targets, never titles: a window class, `web:<site>` from
  `siteFromTitle` (a domain or a listed site name, never from a private window) or `term:<program>`
  from `programFromTitle` (terminal classes, a built-in program list). When in doubt the time stays
  with the browser or terminal. Hush's own window (`app-hush-desktop-MainKt`; `main()` starts AWT
  first so the class is stable) is excluded. Without a Hyprland session `NoAppUsage` is bound and the
  row is hidden. Idle and locked time count (Hyprland has no lock event).
- Rows are keyed by the stored app key and sync like sessions; tag rules don't exist: apps are
  sorted into **activities** (§11).

## 11. Persistence

- **Room 3** in commonMain with `BundledSQLiteDriver` on every target and
  `setQueryCoroutineContext(dispatchers.io)`; only the file path is platform code. Schemas are
  exported to `shared/schemas` and committed. Every version bump gets a migration and a jvmTest
  (`MigrationTestHelper`); never `fallbackToDestructiveMigration`. Migrations keep producing their
  own version's rows (frozen seed SQL).
- **Tables** (version 7): `session` (work phases and detox attempts, with `uid`, `tagId`,
  `activityId`), `tag` (timer presets), `block_list` + `block_list_app`, `activity`, `session_app`
  (apps per phase, keyed by the session's **uid** so synced rows attach on both devices) and
  `activity_rule` (app key → activity uid). Seeded tags have fixed uids on every install.
- **Synced records** (tags, activities, rules) carry `updatedAtMillis`/`updatedBy` and a `deleted`
  tombstone and merge last-writer-wins; sessions and app rows are a union and never overwritten.
  Tag edits use column-specific updates so the uid is never wiped.
- **Rules sort apps into activities, not tags:** tags are timer presets; activities say what the
  time was for. A phase with a picked activity counts fully for it; otherwise each app counts for its
  rule's activity (`FocusByActivity`).
- **DataStore Preferences** for settings, the timer snapshot, the running block, the device id and
  the sync peer. Defaults live in `Settings()`. **Differs from Tomato**, which keeps settings in Room.
- **No backups, no device transfer:** `allowBackup="false"` plus `dataExtractionRules` excluding
  everything, because the settings hold the device id and the paired peer; sync is how data moves.
- **Desktop files:** the database and `sync/` keys in `$XDG_DATA_HOME/hush`, the settings in
  `$XDG_CONFIG_HOME/hush`; both made 0700 on start and locked against a second instance.

## 12. Dependency injection: Koin

- Classic DSL with constructor references (`singleOf`, `viewModelOf`); one module per feature in
  `shared/.../di/`, plus `androidModule` and `blockerModule` in `androidApp` and
  `desktopModule(dataDirectory, settingsFile, environment)` in `desktopApp`.
- Constructor injection everywhere; `by inject()` only in Android framework classes Koin can't
  construct (service, receivers, job service). `koinViewModel()` only in `XRoute`.
- Each platform binds the ports: `AppUsageReader`, the sync ports (`SyncTransport`, `Pairing`,
  `SyncScheduler`; `NoSync` where there's no sync), the blocker (with `-PfakeBlocker` a
  `FakeBlocker` in debug builds) and `DeviceFeatures`.
- `Module.verify()` tests (shared, desktop with and without Hyprland, Android) fail the build on a
  missing binding. **Differs from Tomato**, which uses the Koin compiler plugin with compile safety
  off.

## 13. Testing

- Logic tests in `shared/src/commonTest` (run on the JVM and the Android host); Room and Koin tests
  in `jvmTest`; blocker rules in `blocker-native/src/test`; pure sync and usage helpers in
  `androidApp/src/test`; the transport on loopback in `:sync-lan`; desktop parsers, focus time and
  the sync host end to end in `desktopApp/src/test`.
- Fakes, not mocks (`FakeBlocker`, `FakeSessionRepository`, `FakeActivityRuleRepository`,
  `FakeAppUsageReader`, the sync fakes …), all in-memory.
- `runTest` with a `TestClock` tied to the test scheduler, so `advanceTimeBy` moves both delays and
  "now"; ViewModel tests set `Dispatchers.Main` to a test dispatcher.
- Every product rule gets a test named after it (level ladder, pass and fail detection, pause =
  stop + restart, no automatic retry after `ConnectionLost`, …). **Differs from Tomato**, whose
  timer tests use `runBlocking` around hard-coded dispatchers.

## 14. Licensing

Tomato and Curbox are GPL-3.0 ("version 3 or any later version"), and Hush adapts their code, so it
is a derivative work: **the project is GPL-3.0-or-later**, with the `LICENSE` file and an SPDX header
(`// SPDX-License-Identifier: GPL-3.0-or-later`) in each source file. `AppBlockerService.kt` and
`AlwaysAllowed.kt` keep Curbox's copyright line ("Nethical and Curbox contributors"); files adapted
from Tomato keep Nishant Mishra's. When you copy or adapt a file, keep the original copyright line
next to ours and name the source in the commit message. The Gradle wrapper files are Apache-2.0.
This is an engineering note, not legal advice.
