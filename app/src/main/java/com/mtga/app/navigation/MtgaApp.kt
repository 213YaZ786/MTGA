package com.mtga.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mtga.app.feature.accounts.AccountsScreen
import com.mtga.app.feature.diagnostics.DiagnosticsScreen
import com.mtga.app.feature.search.SearchScreen
import com.mtga.app.feature.settings.SettingsScreen
import com.mtga.app.feature.timeline.TimelineScreen

@Composable
fun MtgaApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBar = TopDestination.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TopDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.TIMELINE.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable(TopDestination.TIMELINE.route) {
                TimelineScreen(onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) })
            }
            composable(TopDestination.ACCOUNTS.route) { AccountsScreen() }
            composable(TopDestination.SEARCH.route) { SearchScreen() }
            composable(TopDestination.SETTINGS.route) {
                SettingsScreen(onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) })
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
