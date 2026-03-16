package com.meshchat.ui.navigation

/**
 * Маршруты навигации приложения.
 */
sealed class Screen(val route: String) {
    data object ChatList : Screen("chat_list")
    data object Peers : Screen("peers")
    data object Settings : Screen("settings")
    data object Chat : Screen("chat/{peerId}") {
        fun createRoute(peerId: String) = "chat/$peerId"
    }
}
