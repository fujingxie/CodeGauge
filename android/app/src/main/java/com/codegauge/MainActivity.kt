package com.codegauge

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.codegauge.activity.ActivityApi
import com.codegauge.activity.ActivityEvent
import com.codegauge.activity.ActivityRepository
import com.codegauge.activity.ActivityStreamClient
import com.codegauge.activity.ActivityStreamConnection
import com.codegauge.activity.ActivityStreamMessage
import com.codegauge.activity.OkHttpActivityApi
import com.codegauge.activity.OkHttpActivityStreamClient
import com.codegauge.dashboard.DashboardApi
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.dashboard.DashboardSnapshot
import com.codegauge.dashboard.OkHttpDashboardApi
import com.codegauge.pairing.CompanionDiscovery
import com.codegauge.pairing.CompanionEndpoint
import com.codegauge.pairing.EncryptedPairingStore
import com.codegauge.pairing.InMemoryPairingStore
import com.codegauge.pairing.NoopCompanionDiscovery
import com.codegauge.pairing.NsdCompanionDiscovery
import com.codegauge.pairing.OkHttpPairingApi
import com.codegauge.pairing.PairRequest
import com.codegauge.pairing.PairResponse
import com.codegauge.pairing.PairingApi
import com.codegauge.pairing.PairingRecord
import com.codegauge.pairing.PairingRepository
import com.codegauge.ui.pairing.PairingRoute
import com.codegauge.ui.theme.CodeGaugeTheme
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val appContext = applicationContext
        setContent {
            val repository = remember {
                PairingRepository(
                    api = OkHttpPairingApi(),
                    store = EncryptedPairingStore(appContext),
                )
            }
            val dashboardRepository = remember {
                DashboardRepository(OkHttpDashboardApi())
            }
            val activityRepository = remember {
                ActivityRepository(OkHttpActivityApi())
            }
            val streamClient = remember {
                OkHttpActivityStreamClient()
            }
            val discovery = remember {
                NsdCompanionDiscovery(appContext)
            }
            CodeGaugeTheme {
                CodeGaugeApp(
                    repository = repository,
                    dashboardRepository = dashboardRepository,
                    activityRepository = activityRepository,
                    streamClient = streamClient,
                    discovery = discovery,
                    deviceName = defaultDeviceName(),
                )
            }
        }
    }

    private fun defaultDeviceName(): String {
        return listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android" }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NotificationPermissionRequestCode)
    }

    companion object {
        private const val NotificationPermissionRequestCode = 2001
    }
}

@Composable
fun CodeGaugeApp(
    repository: PairingRepository,
    dashboardRepository: DashboardRepository,
    activityRepository: ActivityRepository,
    streamClient: ActivityStreamClient,
    discovery: CompanionDiscovery,
    deviceName: String,
) {
    PairingRoute(
        repository = repository,
        dashboardRepository = dashboardRepository,
        activityRepository = activityRepository,
        streamClient = streamClient,
        discovery = discovery,
        deviceName = deviceName,
    )
}

@Preview(showBackground = true)
@Composable
private fun CodeGaugeAppPreview() {
    CodeGaugeTheme {
        CodeGaugeApp(
            repository = PairingRepository(PreviewPairingApi, InMemoryPairingStore()),
            dashboardRepository = DashboardRepository(PreviewDashboardApi),
            activityRepository = ActivityRepository(PreviewActivityApi),
            streamClient = PreviewStreamClient,
            discovery = NoopCompanionDiscovery,
            deviceName = "Preview Android",
        )
    }
}

private object PreviewPairingApi : PairingApi {
    override suspend fun pair(endpoint: CompanionEndpoint, request: PairRequest): PairResponse {
        return PairResponse(
            token = "preview-token",
            serverName = "CodeGauge Companion",
        )
    }
}

private object PreviewDashboardApi : DashboardApi {
    override suspend fun status(pairing: PairingRecord): DashboardSnapshot {
        return DashboardSnapshot(
            providers = emptyList(),
            sessions = emptyList(),
            serverTime = Instant.EPOCH,
        )
    }
}

private object PreviewActivityApi : ActivityApi {
    override suspend fun events(pairing: PairingRecord, limit: Int): List<ActivityEvent> {
        return emptyList()
    }
}

private object PreviewStreamClient : ActivityStreamClient {
    override fun connect(
        pairing: PairingRecord,
        onMessage: (ActivityStreamMessage) -> Unit,
        onFailure: (Throwable) -> Unit,
        onClosed: () -> Unit,
    ): ActivityStreamConnection {
        return ActivityStreamConnection {}
    }
}
