package com.codegauge.ui.dashboard

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.dashboard.DashboardSnapshot
import com.codegauge.dashboard.ProviderStatus
import com.codegauge.dashboard.QuotaWindowStatus
import com.codegauge.dashboard.SessionStatus
import com.codegauge.dashboard.WindowTypes
import com.codegauge.dashboard.formatPercentLeft
import com.codegauge.dashboard.formatResetText
import com.codegauge.dashboard.formatSessionSummary
import com.codegauge.dashboard.formatSource
import com.codegauge.dashboard.formatUsage
import com.codegauge.dashboard.progressFromPercentLeft
import com.codegauge.dashboard.window
import com.codegauge.pairing.PairingRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardRoute(
    pairing: PairingRecord,
    repository: DashboardRepository,
    modifier: Modifier = Modifier,
    onClearPairing: () -> Unit,
) {
    var snapshot by remember(pairing.serverUrl, pairing.token) {
        mutableStateOf<DashboardSnapshot?>(null)
    }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    val scope = rememberCoroutineScope()

    suspend fun loadSnapshot(fullScreenLoading: Boolean) {
        if (fullScreenLoading) {
            isLoading = true
        } else {
            isRefreshing = true
        }

        try {
            snapshot = repository.loadStatus(pairing)
            errorMessage = null
        } catch (exception: Exception) {
            Log.e(Tag, "Load dashboard failed", exception)
            errorMessage = exception.message ?: "Could not load dashboard."
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(pairing.serverUrl, pairing.token) {
        loadSnapshot(fullScreenLoading = true)
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                loadSnapshot(fullScreenLoading = false)
            }
        },
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DashboardHeader()
            ConnectionPanel(
                pairing = pairing,
                online = snapshot != null && errorMessage == null,
                errorMessage = errorMessage,
                onRefresh = {
                    scope.launch {
                        loadSnapshot(fullScreenLoading = false)
                    }
                },
                onClearPairing = onClearPairing,
            )

            if (isLoading && snapshot == null) {
                LoadingPanel()
            } else {
                val currentSnapshot = snapshot
                if (currentSnapshot == null) {
                    EmptyDashboardPanel()
                } else {
                    ProviderCards(
                        providers = currentSnapshot.providers,
                        now = now,
                    )
                    SessionSummaryPanel(currentSnapshot.sessions)
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DashboardHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "CodeGauge",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Claude and Codex usage at a glance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionPanel(
    pairing: PairingRecord,
    online: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onClearPairing: () -> Unit,
) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(online)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (online) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${pairing.serverName} · ${pairing.serverUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        errorMessage?.let {
            ErrorMessage(it)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onRefresh,
            ) {
                Text("Refresh")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onClearPairing,
            ) {
                Text("Pair again")
            }
        }
    }
}

@Composable
private fun ProviderCards(
    providers: List<ProviderStatus>,
    now: Instant,
) {
    val orderedProviders = providers.sortedWith(
        compareBy<ProviderStatus> {
            when (it.id) {
                "claude" -> 0
                "codex" -> 1
                else -> 2
            }
        }.thenBy { it.name },
    )

    if (orderedProviders.isEmpty()) {
        EmptyDashboardPanel()
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        orderedProviders.forEach { provider ->
            ProviderCard(
                provider = provider,
                now = now,
            )
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderStatus,
    now: Instant,
) {
    val fiveHour = provider.window(WindowTypes.FiveHours)
    val weekly = provider.window(WindowTypes.Weekly)

    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name.ifBlank { provider.id },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                if (provider.planTier.isNotBlank()) {
                    Text(
                        text = provider.planTier,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SourcePill(
                source = listOfNotNull(fiveHour, weekly).firstOrNull()?.source.orEmpty(),
            )
        }

        if (!provider.available) {
            Text(
                text = "Data unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Panel
        }

        QuotaWindowBlock(
            label = "5h window",
            window = fiveHour,
            now = now,
            accentColor = MaterialTheme.colorScheme.primary,
        )
        QuotaWindowBlock(
            label = "Weekly",
            window = weekly,
            now = now,
            accentColor = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun QuotaWindowBlock(
    label: String,
    window: QuotaWindowStatus?,
    now: Instant,
    accentColor: Color,
) {
    val progress = progressFromPercentLeft(window?.percentLeft)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatPercentLeft(window?.percentLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
        }

        if (progress == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
            )
        } else {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
            )
        }

        Text(
            text = formatUsage(window?.used, window?.limit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatResetText(window?.resetsAt, now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionSummaryPanel(sessions: List<SessionStatus>) {
    Panel {
        Text(
            text = "Current sessions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = formatSessionSummary(sessions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        sessions.take(3).forEach { session ->
            SessionRow(session)
        }
    }
}

@Composable
private fun SessionRow(session: SessionStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.projectPath.substringAfterLast('/').ifBlank { "Unknown project" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = session.providerId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = session.state,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyDashboardPanel() {
    Panel {
        Text(
            text = "No dashboard data",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Pull down or tap Refresh after Companion is running.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourcePill(source: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            text = formatSource(source),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(
                if (online) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
    )
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private const val Tag = "CodeGaugeDashboard"
