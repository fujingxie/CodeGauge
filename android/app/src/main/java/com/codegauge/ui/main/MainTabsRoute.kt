package com.codegauge.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.codegauge.activity.ActivityRepository
import com.codegauge.activity.ActivityStreamClient
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.pairing.PairingRecord
import com.codegauge.settings.SettingsRepository
import com.codegauge.ui.activity.ActivityRoute
import com.codegauge.ui.dashboard.DashboardRoute
import com.codegauge.ui.settings.SettingsRoute

@Composable
fun MainTabsRoute(
    pairing: PairingRecord,
    dashboardRepository: DashboardRepository,
    activityRepository: ActivityRepository,
    settingsRepository: SettingsRepository,
    streamClient: ActivityStreamClient,
    onClearPairing: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(MainTab.Dashboard) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.iconText) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (selectedTab) {
                MainTab.Dashboard -> DashboardRoute(
                    pairing = pairing,
                    repository = dashboardRepository,
                    onClearPairing = onClearPairing,
                )
                MainTab.Activity -> ActivityRoute(
                    pairing = pairing,
                    dashboardRepository = dashboardRepository,
                    activityRepository = activityRepository,
                    streamClient = streamClient,
                )
                MainTab.Settings -> SettingsRoute(
                    pairing = pairing,
                    repository = settingsRepository,
                    onClearPairing = onClearPairing,
                )
            }
        }
    }
}

private enum class MainTab(
    val label: String,
    val iconText: String,
) {
    Dashboard("Dashboard", "D"),
    Activity("Activity", "A"),
    Settings("Settings", "S"),
}
