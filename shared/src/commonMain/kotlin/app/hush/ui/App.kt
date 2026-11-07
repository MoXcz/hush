// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import app.hush.blocker.ProfileId
import app.hush.blocklists.ui.BlockListRoute
import app.hush.detox.ui.PathRoute
import app.hush.panic.ui.GroundingRoute
import app.hush.resources.Res
import app.hush.resources.ic_path
import app.hush.resources.ic_settings
import app.hush.resources.ic_settings_filled
import app.hush.resources.ic_timer
import app.hush.resources.ic_timer_filled
import app.hush.resources.path_title
import app.hush.resources.settings_title
import app.hush.resources.timer_title
import app.hush.settings.ui.SettingsRoute
import app.hush.settings.ui.onboarding.OnboardingRoute
import app.hush.stats.ui.StatsRoute
import app.hush.sync.ui.SyncRoute
import app.hush.timer.ui.TimerRoute
import app.hush.timer.ui.dimclock.DimClockRoute
import app.hush.ui.navigation.Route
import app.hush.ui.theme.HushTheme
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private data class TopLevelDestination(
    val route: Route,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource,
    val label: StringResource,
)

private val topLevelDestinations =
    listOf(
        TopLevelDestination(Route.Timer, Res.drawable.ic_timer, Res.drawable.ic_timer_filled, Res.string.timer_title),
        TopLevelDestination(Route.Path, Res.drawable.ic_path, Res.drawable.ic_path, Res.string.path_title),
        TopLevelDestination(Route.Settings, Res.drawable.ic_settings, Res.drawable.ic_settings_filled, Res.string.settings_title),
    )

// Non-Android targets can't serialize NavKey reflectively, so every route is registered here.
private val navConfig =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.Timer::class, Route.Timer.serializer())
                    subclass(Route.DimClock::class, Route.DimClock.serializer())
                    subclass(Route.Path::class, Route.Path.serializer())
                    subclass(Route.Grounding::class, Route.Grounding.serializer())
                    subclass(Route.Stats::class, Route.Stats.serializer())
                    subclass(Route.Settings::class, Route.Settings.serializer())
                    subclass(Route.Onboarding::class, Route.Onboarding.serializer())
                    subclass(Route.BlockList::class, Route.BlockList.serializer())
                    subclass(Route.Sync::class, Route.Sync.serializer())
                }
            }
    }

/**
 * The whole app. [openPanic] asks for the panic flow (from the blocker's block screen); the app
 * opens it once and calls [onPanicOpened].
 */
@Composable
fun App(
    modifier: Modifier = Modifier,
    openPanic: Boolean = false,
    onPanicOpened: () -> Unit = {},
) {
    HushTheme {
        val backStack = rememberNavBackStack(navConfig, Route.Timer)
        LaunchedEffect(openPanic) {
            if (!openPanic) return@LaunchedEffect
            backStack.retainAll { it == Route.Timer }
            backStack.add(Route.Path)
            backStack.add(Route.Grounding)
            onPanicOpened()
        }
        // Sub-screens (e.g. onboarding) keep their tab highlighted.
        val currentTab = backStack.lastOrNull { key -> topLevelDestinations.any { it.route == key } }
        val dimClock = backStack.lastOrNull() == Route.DimClock
        val motionScheme = MaterialTheme.motionScheme
        Scaffold(
            modifier = modifier,
            bottomBar = {
                // Outside NavDisplay, so page transitions never move or resize it (as Tomato does).
                AnimatedVisibility(
                    visible = !dimClock,
                    enter = slideInVertically(motionScheme.slowSpatialSpec()) { it },
                    exit = slideOutVertically(motionScheme.slowSpatialSpec()) { it },
                ) {
                    BottomToolbar(currentTab = currentTab, onSelect = backStack::selectTopLevel)
                }
            },
        ) { padding ->
            // The navigation host fills the window and never resizes; each screen pads itself for the
            // bar, so hiding the bar for the dim clock can't shift the shared ring mid-transition.
            val screen = Modifier.padding(bottom = padding.calculateBottomPadding())
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    val fade = fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.defaultEffectsSpec())
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        transitionSpec = { fade },
                        popTransitionSpec = { fade },
                        predictivePopTransitionSpec = { fade },
                        entryDecorators =
                            listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                        entryProvider =
                            entryProvider {
                                entry<Route.Timer> {
                                    TimerRoute(
                                        modifier = screen,
                                        onOpenDimClock = { if (!dimClock) backStack.add(Route.DimClock) },
                                        onOpenSetup = { backStack.add(Route.Onboarding) },
                                    )
                                }
                                entry<Route.DimClock> {
                                    DimClockRoute(onExit = { if (backStack.lastOrNull() == Route.DimClock) backStack.removeLastOrNull() })
                                }
                                entry<Route.Path> {
                                    PathRoute(
                                        modifier = screen,
                                        onOpenPanic = { if (backStack.lastOrNull() == Route.Path) backStack.add(Route.Grounding) },
                                        onOpenStats = { if (backStack.lastOrNull() == Route.Path) backStack.add(Route.Stats) },
                                        onOpenSetup = { backStack.add(Route.Onboarding) },
                                    )
                                }
                                entry<Route.Grounding> {
                                    GroundingRoute(
                                        modifier = screen,
                                        onClose = { if (backStack.lastOrNull() == Route.Grounding) backStack.removeLastOrNull() },
                                    )
                                }
                                entry<Route.Stats> {
                                    StatsRoute(
                                        modifier = screen,
                                        onBack = { if (backStack.lastOrNull() == Route.Stats) backStack.removeLastOrNull() },
                                    )
                                }
                                entry<Route.Settings> {
                                    SettingsRoute(
                                        modifier = screen,
                                        onOpenList = { backStack.add(Route.BlockList(it.value)) },
                                        onOpenSync = { if (backStack.lastOrNull() == Route.Settings) backStack.add(Route.Sync) },
                                    )
                                }
                                entry<Route.Sync> {
                                    SyncRoute(
                                        modifier = screen,
                                        onBack = { if (backStack.lastOrNull() == Route.Sync) backStack.removeLastOrNull() },
                                    )
                                }
                                entry<Route.Onboarding> {
                                    OnboardingRoute(
                                        modifier = screen,
                                        onOpenList = {
                                            if (backStack.lastOrNull() ==
                                                Route.Onboarding
                                            ) {
                                                backStack.add(Route.BlockList(it.value))
                                            }
                                        },
                                        onDone = { if (backStack.lastOrNull() == Route.Onboarding) backStack.removeLastOrNull() },
                                    )
                                }
                                entry<Route.BlockList> { key ->
                                    BlockListRoute(
                                        id = ProfileId(key.id),
                                        modifier = screen,
                                        onBack = { if (backStack.lastOrNull() == key) backStack.removeLastOrNull() },
                                    )
                                }
                            },
                    )
                }
            }
        }
    }
}

// Timer is home: other tabs sit on top of it, so Back from any tab returns to the timer.
private fun NavBackStack<NavKey>.selectTopLevel(route: Route) {
    if (lastOrNull() == route) return
    retainAll { it == Route.Timer }
    if (route != Route.Timer) add(route)
}

@Composable
private fun BottomToolbar(
    currentTab: NavKey?,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mirrors Tomato's toolbar (AppScreen.kt): the selected button grows its label with the theme's
    // default spatial spring; nothing else animates.
    val colors = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors =
                FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                    toolbarContainerColor = colors.primaryContainer,
                    toolbarContentColor = colors.onPrimaryContainer,
                ),
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .padding(vertical = FloatingToolbarDefaults.ScreenOffset)
                    .zIndex(1f),
        ) {
            topLevelDestinations.forEach { destination ->
                val selected = currentTab == destination.route
                val label = stringResource(destination.label)
                ToggleButton(
                    checked = selected,
                    onCheckedChange = { if (!selected) onSelect(destination.route) },
                    colors =
                        ToggleButtonDefaults.toggleButtonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer,
                            checkedContainerColor = colors.primary,
                            checkedContentColor = colors.onPrimary,
                        ),
                    shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
                    modifier = Modifier.height(56.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(if (selected) destination.selectedIcon else destination.icon),
                            contentDescription = label,
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = expandHorizontally(motionScheme.defaultSpatialSpec()),
                            exit = shrinkHorizontally(motionScheme.defaultSpatialSpec()),
                        ) {
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                            )
                        }
                    }
                }
            }
        }
    }
}
