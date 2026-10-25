package com.example.perfectoutfit.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.perfectoutfit.feature.catalog.CatalogScreen
import com.example.perfectoutfit.feature.explorer.ExplorerScreen
import com.example.perfectoutfit.feature.history.HistoryScreen
import com.example.perfectoutfit.feature.home.HomeLaunch
import com.example.perfectoutfit.feature.home.HomeLaunchState
import com.example.perfectoutfit.feature.home.HomeScreen
import com.example.perfectoutfit.feature.rate.RateOutfitScreen
import com.example.perfectoutfit.feature.settings.ModeComparisonScreen
import com.example.perfectoutfit.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.first

// Guards against the classic Compose Navigation bug where a screen that's
// tapped repeatedly (e.g. mashing a back arrow during its exit fade) fires a
// second navigate()/popBackStack() before the first transition has settled.
// The first tap moves the back stack off the tapped screen's entry, so acting
// only while that entry is still on top makes every extra tap a no-op instead
// of racing the back stack — which otherwise can leave a destination composed
// but never actually drawn (a blank/black screen with the nav bar still up).
// Not gated on the entry being RESUMED: an interrupted fade can leave it stuck
// in STARTED, and the screen would then swallow every tap until it is left.
private inline fun NavHostController.ifOnTop(from: NavBackStackEntry, action: NavHostController.() -> Unit) {
    if (currentBackStackEntry?.id == from.id) {
        action()
    }
}

private fun NavHostController.navigateOnce(from: NavBackStackEntry, route: String) =
    ifOnTop(from) { navigate(route) }

private fun NavHostController.popBackStackOnce(from: NavBackStackEntry) =
    ifOnTop(from) { popBackStack() }

// Shows the tab's root screen: pops back to it when it's in the back stack (the
// current tab, or Home, which is always at the bottom), otherwise opens it on top
// of Home. Either way every sub-screen is dropped. popUpTo + launchSingleTop make
// repeated taps idempotent, so this is not gated on ifOnTop.
private fun NavHostController.navigateToTab(route: String) {
    if (!popBackStack(route, inclusive = false)) {
        navigate(route) {
            popUpTo(graph.findStartDestination().id)
            launchSingleTop = true
        }
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Default.Home),
    BottomNavItem(Screen.History, "History", Icons.Default.History),
    BottomNavItem(Screen.Settings, "Settings", Icons.Default.Settings)
)

// MD3 canonical breakpoints (https://m3.material.io/foundations/layout/applying-layout).
private const val MEDIUM_WIDTH_BREAKPOINT_DP = 600
private const val EXPANDED_WIDTH_BREAKPOINT_DP = 840
private val MAX_CONTENT_WIDTH = 840.dp

// MD3 emphasized easing/duration for top-level destination switches — a "fade
// through" transition, the pattern Material recommends for UI elements with no
// direct navigational relationship (e.g. bottom nav / rail destinations).
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val DestinationEnter = fadeIn(animationSpec = tween(durationMillis = 400, easing = EmphasizedDecelerate))
private val DestinationExit = fadeOut(animationSpec = tween(durationMillis = 200, easing = EmphasizedAccelerate))

@Composable
fun PerfectOutfitNavHost(launchRequest: LaunchRequest? = null) {
    val navController = rememberNavController()
    // A tab tap or launch held back while an edit screen confirms (or asks first).
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val homeLaunch = remember { HomeLaunchState() }

    // Like a tab tap, a launch onto the screen already showing doesn't navigate: navigateToTab
    // would re-navigate singleTop and leave that screen's composition on a lifecycle that never
    // resumes, so it stops collecting its state.
    fun showTab(route: String) {
        if (navController.currentDestination?.route != route) navController.navigateToTab(route)
    }

    fun open(target: LaunchTarget) {
        when (target) {
            is LaunchTarget.RateEntry ->
                navController.navigate(Screen.RateOutfit.createRoute(target.outfitEntryId, highlight = true))
            LaunchTarget.Settings -> showTab(Screen.Settings.route)
            is LaunchTarget.Home -> {
                showTab(Screen.Home.route)
                homeLaunch.pending = HomeLaunch(target.activityType)
            }
        }
    }

    // NavHost sits in Scaffold's subcomposed content, so its graph may not be set yet when this
    // effect starts; the first back stack entry means it is. A launch arriving on an edit screen
    // waits for the same confirmation as a tab tap.
    LaunchedEffect(launchRequest) {
        val request = launchRequest ?: return@LaunchedEffect
        navController.currentBackStackEntryFlow.first()
        val route = navController.currentDestination?.route
        if (route == Screen.NewOutfit.route || route == Screen.RateOutfit.route) {
            pendingNavigation = { open(request.target) }
        } else {
            open(request.target)
        }
    }

    var homeReselections by remember { mutableIntStateOf(0) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    // Sub-screens sit directly on their tab's root, so the entry below one is its tab
    // (e.g. a live outfit log belongs to Home, a past one to History).
    val tabRoutes = bottomNavItems.map { it.screen.route }
    val currentTabRoute = if (currentRoute in tabRoutes) {
        currentRoute
    } else {
        navController.previousBackStackEntry?.destination?.route
    }
    // These screens hold edits; they confirm (or ask first) before a tab switch.
    val isOnEditScreen = currentRoute == Screen.NewOutfit.route || currentRoute == Screen.RateOutfit.route

    fun isSelected(item: BottomNavItem) = currentTabRoute == item.screen.route

    fun onNavItemClick(item: BottomNavItem) {
        val route = item.screen.route
        when {
            isOnEditScreen -> pendingNavigation = { navController.navigateToTab(route) }
            currentRoute == route -> if (item.screen == Screen.Home) homeReselections++
            else -> navController.navigateToTab(route)
        }
    }

    // Adaptive navigation per MD3 canonical layouts: a bottom bar on compact
    // widths (phones), a rail from medium widths upward (tablets, foldables,
    // large screens) — https://m3.material.io/foundations/layout/canonical-layouts.
    val windowWidthDp = LocalConfiguration.current.screenWidthDp
    val useNavigationRail = windowWidthDp >= MEDIUM_WIDTH_BREAKPOINT_DP
    val maxContentWidth = if (windowWidthDp >= EXPANDED_WIDTH_BREAKPOINT_DP) MAX_CONTENT_WIDTH else Dp.Unspecified

    if (useNavigationRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                bottomNavItems.forEach { item ->
                    NavigationRailItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = isSelected(item),
                        onClick = { onNavItemClick(item) }
                    )
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime)
            ) { innerPadding ->
                NavGraphContent(
                    navController = navController,
                    innerPadding = innerPadding,
                    maxContentWidth = maxContentWidth,
                    pendingNavigation = pendingNavigation,
                    onPendingNavigationChange = { pendingNavigation = it },
                    homeReselections = homeReselections,
                    homeLaunch = homeLaunch,
                )
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
            bottomBar = {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = isSelected(item),
                            onClick = { onNavItemClick(item) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavGraphContent(
                navController = navController,
                innerPadding = innerPadding,
                maxContentWidth = maxContentWidth,
                pendingNavigation = pendingNavigation,
                onPendingNavigationChange = { pendingNavigation = it },
                homeReselections = homeReselections,
                homeLaunch = homeLaunch,
            )
        }
    }
}

@Composable
private fun NavGraphContent(
    navController: NavHostController,
    innerPadding: PaddingValues,
    maxContentWidth: Dp,
    pendingNavigation: (() -> Unit)?,
    onPendingNavigationChange: ((() -> Unit)?) -> Unit,
    homeReselections: Int,
    homeLaunch: HomeLaunchState
) {
    // Constrain reading/content width on expanded (840dp+) windows so lines of
    // text and layouts don't stretch edge-to-edge on large screens.
    val contentModifier = if (maxContentWidth != Dp.Unspecified) {
        Modifier.widthIn(max = maxContentWidth)
    } else {
        Modifier
    }

    // An edit screen accepted (or confirmed) the tab tap or launch stored in pendingNavigation.
    val onTabSwitchConfirmed: () -> Unit = {
        pendingNavigation?.let { navigate ->
            onPendingNavigationChange(null)
            navigate()
        }
    }
    val onTabSwitchDismissed: () -> Unit = { onPendingNavigationChange(null) }

    Box(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = contentModifier,
            enterTransition = { DestinationEnter },
            exitTransition = { DestinationExit },
            popEnterTransition = { DestinationEnter },
            popExitTransition = { DestinationExit }
        ) {
            composable(Screen.Home.route) { entry ->
                HomeScreen(
                    onNavigateToNewOutfit = { navController.navigateOnce(entry, Screen.NewOutfit.createRoute(isLive = true)) },
                    onNavigateToExplorer = { forecastTemp ->
                        navController.navigateOnce(entry, Screen.Explorer.createRoute(forecastTemp))
                    },
                    tabReselections = homeReselections,
                    launchState = homeLaunch
                )
            }
            composable(
                route = Screen.Explorer.route,
                arguments = listOf(
                    navArgument("forecastTemp") {
                        type = NavType.IntType
                        defaultValue = Screen.Explorer.NO_FORECAST_TEMP
                    }
                )
            ) { entry ->
                ExplorerScreen(onNavigateBack = { navController.popBackStackOnce(entry) })
            }
            composable(Screen.Catalog.route) { entry ->
                CatalogScreen(onNavigateBack = { navController.popBackStackOnce(entry) })
            }
            composable(Screen.ModeComparison.route) { entry ->
                ModeComparisonScreen(onNavigateBack = { navController.popBackStackOnce(entry) })
            }
            composable(
                route = Screen.RateOutfit.route,
                arguments = listOf(
                    navArgument("outfitEntryId") { type = NavType.LongType },
                    navArgument("highlight") { type = NavType.BoolType; defaultValue = false }
                )
            ) { entry ->
                val highlight = entry.arguments?.getBoolean("highlight") ?: false
                RateOutfitScreen(
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    highlightRating = highlight,
                    externalCancelRequested = pendingNavigation != null,
                    onExternalCancelConfirmed = onTabSwitchConfirmed,
                    onExternalCancelDismissed = onTabSwitchDismissed
                )
            }
            composable(
                route = Screen.NewOutfit.route,
                arguments = listOf(navArgument("isLive") { type = NavType.BoolType; defaultValue = false })
            ) { entry ->
                RateOutfitScreen(
                    onNavigateBack = { navController.popBackStackOnce(entry) },
                    externalCancelRequested = pendingNavigation != null,
                    onExternalCancelConfirmed = onTabSwitchConfirmed,
                    onExternalCancelDismissed = onTabSwitchDismissed
                )
            }
            composable(Screen.History.route) { entry ->
                HistoryScreen(
                    onNavigateToRateOutfit = { entryId ->
                        navController.navigateOnce(entry, Screen.RateOutfit.createRoute(entryId, highlight = false))
                    },
                    onNavigateToNewOutfit = { navController.navigateOnce(entry, Screen.NewOutfit.createRoute(isLive = false)) }
                )
            }
            composable(Screen.Settings.route) { entry ->
                SettingsScreen(
                    onNavigateToCatalog = { navController.navigateOnce(entry, Screen.Catalog.route) },
                    onNavigateToModeComparison = { navController.navigateOnce(entry, Screen.ModeComparison.route) }
                )
            }
        }
    }
}
