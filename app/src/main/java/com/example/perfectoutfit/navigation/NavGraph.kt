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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
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
import com.example.perfectoutfit.feature.home.HomeScreen
import com.example.perfectoutfit.feature.rate.RateOutfitScreen
import com.example.perfectoutfit.feature.settings.SettingsScreen

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
fun PerfectOutfitNavHost(deepLinkOutfitEntryId: Long? = null) {
    val navController = rememberNavController()
    var pendingTabRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deepLinkOutfitEntryId) {
        if (deepLinkOutfitEntryId != null) {
            navController.navigate(Screen.RateOutfit.createRoute(deepLinkOutfitEntryId, highlight = true))
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isOnNewOutfit = currentDestination?.route?.startsWith("new_outfit") == true
    val isOnRateOutfit = currentDestination?.route?.startsWith("rate_outfit") == true
    val isOnCatalog = currentDestination?.route == Screen.Catalog.route

    fun isSelected(item: BottomNavItem) = when {
        isOnNewOutfit -> item.screen == Screen.History
        isOnCatalog -> item.screen == Screen.Settings
        else -> currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    fun onNavItemClick(item: BottomNavItem) {
        if (isOnNewOutfit) {
            pendingTabRoute = item.screen.route
        } else {
            val alreadyAtRoot = currentDestination?.route == item.screen.route
            if (!alreadyAtRoot) {
                // If RateOutfit is open, pop it first so its state is not
                // saved and later restored on top of the destination tab.
                if (isOnRateOutfit) navController.popBackStack()
                navController.navigate(item.screen.route) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            }
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
                    pendingTabRoute = pendingTabRoute,
                    onPendingTabRouteChange = { pendingTabRoute = it }
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
                pendingTabRoute = pendingTabRoute,
                onPendingTabRouteChange = { pendingTabRoute = it }
            )
        }
    }
}

@Composable
private fun NavGraphContent(
    navController: NavHostController,
    innerPadding: PaddingValues,
    maxContentWidth: Dp,
    pendingTabRoute: String?,
    onPendingTabRouteChange: (String?) -> Unit
) {
    // Constrain reading/content width on expanded (840dp+) windows so lines of
    // text and layouts don't stretch edge-to-edge on large screens.
    val contentModifier = if (maxContentWidth != Dp.Unspecified) {
        Modifier.widthIn(max = maxContentWidth)
    } else {
        Modifier
    }

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
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToNewOutfit = { navController.navigate(Screen.NewOutfit.createRoute(isLive = true)) },
                    onNavigateToExplorer = { forecastTemp ->
                        navController.navigate(Screen.Explorer.createRoute(forecastTemp))
                    }
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
            ) {
                ExplorerScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Catalog.route) {
                CatalogScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.RateOutfit.route,
                arguments = listOf(
                    navArgument("outfitEntryId") { type = NavType.LongType },
                    navArgument("highlight") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val highlight = backStackEntry.arguments?.getBoolean("highlight") ?: false
                RateOutfitScreen(
                    onNavigateBack = { navController.popBackStack() },
                    highlightRating = highlight
                )
            }
            composable(
                route = Screen.NewOutfit.route,
                arguments = listOf(navArgument("isLive") { type = NavType.BoolType; defaultValue = false })
            ) {
                RateOutfitScreen(
                    onNavigateBack = { navController.popBackStack() },
                    externalCancelRequested = pendingTabRoute != null,
                    onExternalCancelConfirmed = {
                        val route = pendingTabRoute
                        if (route != null) {
                            onPendingTabRouteChange(null)
                            val startId = navController.graph.findStartDestination().id
                            if (route == Screen.Home.route) {
                                // Home is the start destination and already in the back stack;
                                // popBackStack is more reliable than navigate + launchSingleTop here.
                                navController.popBackStack(startId, inclusive = false)
                            } else {
                                navController.navigate(route) {
                                    popUpTo(startId)
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    onExternalCancelDismissed = { onPendingTabRouteChange(null) }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateToRateOutfit = { entryId ->
                        navController.navigate(Screen.RateOutfit.createRoute(entryId, highlight = false))
                    },
                    onNavigateToNewOutfit = { navController.navigate(Screen.NewOutfit.createRoute(isLive = false)) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToCatalog = { navController.navigate(Screen.Catalog.route) }
                )
            }
        }
    }
}
