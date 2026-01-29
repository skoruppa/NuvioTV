package com.nuvio.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.nuvio.tv.ui.components.SidebarItem
import com.nuvio.tv.ui.components.SidebarNavigation
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NuvioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val sidebarFocusRequester = remember { FocusRequester() }
                    var isSidebarExpanded by remember { mutableStateOf(false) }
                    var isSidebarFocused by remember { mutableStateOf(false) }

                    val rootRoutes = setOf(
                        Screen.Home.route,
                        Screen.Search.route,
                        Screen.Settings.route,
                        Screen.AddonManager.route
                    )

                    LaunchedEffect(currentRoute) {
                        if (currentRoute in rootRoutes) {
                            if (!isSidebarFocused) {
                                isSidebarExpanded = false
                            }
                        } else {
                            isSidebarExpanded = false
                        }
                    }

                    BackHandler(enabled = currentRoute in rootRoutes && !isSidebarFocused) {
                        isSidebarExpanded = true
                        sidebarFocusRequester.requestFocus()
                    }

                    val sidebarItems = listOf(
                        SidebarItem(route = Screen.Home.route, label = "Home", icon = Icons.Filled.Home),
                        SidebarItem(route = Screen.Search.route, label = "Search", icon = Icons.Filled.Search),
                        SidebarItem(route = Screen.AddonManager.route, label = "Addons", icon = Icons.Filled.Extension),
                        SidebarItem(route = Screen.Settings.route, label = "Settings", icon = Icons.Filled.Settings)
                    )

                    val showSidebar = currentRoute in rootRoutes
                    val sidebarWidth by animateDpAsState(
                        targetValue = if (showSidebar && isSidebarExpanded) 260.dp else 0.dp,
                        label = "sidebarPadding"
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showSidebar) {
                            SidebarNavigation(
                                items = sidebarItems,
                                selectedRoute = currentRoute,
                                isExpanded = isSidebarExpanded,
                                onExpandedChange = { expanded -> isSidebarExpanded = expanded },
                                focusRequester = sidebarFocusRequester,
                                onFocusChange = { focused -> isSidebarFocused = focused },
                                onNavigate = { route ->
                                    if (currentRoute != route) {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = sidebarWidth)
                        ) {
                            NuvioNavHost(navController = navController)
                        }
                    }
                }
            }
        }
    }
}
