package com.nuvio.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nuvio.tv.ui.screens.detail.MetaDetailsScreen
import com.nuvio.tv.ui.screens.home.HomeScreen
import com.nuvio.tv.ui.screens.addon.AddonManagerScreen
import com.nuvio.tv.ui.screens.search.SearchScreen
import com.nuvio.tv.ui.screens.settings.SettingsScreen

@Composable
fun NuvioNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetail = { itemId, itemType ->
                    navController.navigate(Screen.Detail.createRoute(itemId, itemType))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("itemType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            MetaDetailsScreen(
                onBackPress = { navController.popBackStack() },
                onPlayClick = { videoId ->
                    // Navigate to player or stream selection
                    // TODO: Implement stream selection screen
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(Screen.AddonManager.route) {
            AddonManagerScreen()
        }
    }
}
