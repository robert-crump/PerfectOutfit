package com.example.perfectoutfit.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.perfectoutfit.feature.catalog.CatalogScreen
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

@Composable
fun PerfectOutfitNavHost(deepLinkOutfitEntryId: Long? = null) {
    val navController = rememberNavController()
    var pendingTabRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deepLinkOutfitEntryId) {
        if (deepLinkOutfitEntryId != null) {
            navController.navigate(Screen.RateOutfit.createRoute(deepLinkOutfitEntryId, highlight = true))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val isOnNewOutfit  = currentDestination?.route?.startsWith("new_outfit") == true
            val isOnRateOutfit = currentDestination?.route?.startsWith("rate_outfit") == true
            val isOnCatalog    = currentDestination?.route == Screen.Catalog.route

            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = when {
                            isOnNewOutfit            -> item.screen == Screen.History
                            isOnCatalog              -> item.screen == Screen.Settings
                            else -> currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        },
                        onClick = {
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
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToNewOutfit = { navController.navigate(Screen.NewOutfit.createRoute(isLive = true)) }
                )
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
                            pendingTabRoute = null
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
                    onExternalCancelDismissed = { pendingTabRoute = null }
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
