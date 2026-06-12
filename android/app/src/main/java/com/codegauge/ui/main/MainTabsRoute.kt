package com.codegauge.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var selectedDashboardProviderId by remember { mutableStateOf<String?>(null) }
    val hideBottomBar = selectedTab == MainTab.Dashboard && selectedDashboardProviderId != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar(
                    containerColor = Color(0xFF111722),
                    tonalElevation = 0.dp,
                ) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                if (tab != MainTab.Dashboard) {
                                    selectedDashboardProviderId = null
                                }
                            },
                            icon = {
                                Text(
                                    text = tab.iconText,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
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
                    activityRepository = activityRepository,
                    selectedProviderId = selectedDashboardProviderId,
                    onSelectedProviderIdChange = { selectedDashboardProviderId = it },
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
    Dashboard("仪表盘", "◒"),
    Activity("活动", "⌁"),
    Settings("设置", "⚙"),
}
