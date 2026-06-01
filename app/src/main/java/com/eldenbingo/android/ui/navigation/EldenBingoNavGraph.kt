package com.eldenbingo.android.ui.navigation

sealed class Screen(val route: String) {
    object Connect : Screen("connect")
    object Lobby : Screen("lobby")
    object BingoCard : Screen("bingo_card")
    object Chat : Screen("chat")
    object MapPreview : Screen("map_preview")
    object Scoreboard : Screen("scoreboard")
    object Admin : Screen("admin")
}
