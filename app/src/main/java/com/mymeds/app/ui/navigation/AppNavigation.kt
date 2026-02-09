package com.mymeds.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.mymeds.app.ui.screens.DashboardScreen
import com.mymeds.app.ui.screens.HistoryScreen
import com.mymeds.app.ui.screens.SettingsScreen
import com.mymeds.app.ui.viewmodel.MedsViewModel

enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Today", Icons.Default.Home),
    History("History", Icons.Default.CalendarMonth),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(viewModel: MedsViewModel) {
    var currentTab by remember { mutableStateOf(Tab.Dashboard) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = currentTab == tab,
                        onClick = { currentTab = tab }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (currentTab) {
                Tab.Dashboard -> DashboardScreen(viewModel)
                Tab.History -> HistoryScreen(viewModel)
                Tab.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToDashboard = { currentTab = Tab.Dashboard }
                )
            }
        }
    }
}
