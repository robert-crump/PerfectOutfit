package com.example.perfectoutfit.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Catalog : Screen("catalog")
    data object RateOutfit : Screen("rate_outfit/{outfitEntryId}?highlight={highlight}") {
        fun createRoute(outfitEntryId: Long, highlight: Boolean = false) =
            "rate_outfit/$outfitEntryId?highlight=$highlight"
    }
    data object NewOutfit : Screen("new_outfit?isLive={isLive}") {
        fun createRoute(isLive: Boolean = false) = "new_outfit?isLive=$isLive"
    }
    data object History : Screen("history")
    data object Settings : Screen("settings")
}
