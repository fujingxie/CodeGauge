package com.codegauge.ui.dashboard

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.dashboard.DashboardSnapshot
import com.codegauge.dashboard.ProviderStatus
import com.codegauge.dashboard.QuotaWindowStatus
import com.codegauge.dashboard.SessionStatus
import com.codegauge.dashboard.WindowTypes
import com.codegauge.dashboard.formatProviderName
import com.codegauge.dashboard.formatSessionSummary
import com.codegauge.dashboard.window
import com.codegauge.pairing.PairingRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardRoute(
    pairing: PairingRecord,
    repository: DashboardRepository,
    modifier: Modifier = Modifier,
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
            errorMessage = exception.message ?: "仪表盘加载失败"
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
            .background(DashboardBackground)
            .pullRefresh(pullRefreshState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DashboardStatusStrip(
                pairing = pairing,
                snapshot = snapshot,
                online = snapshot != null && errorMessage == null,
                now = now,
            )

            errorMessage?.let {
                ErrorMessage(it)
            }

            if (isLoading && snapshot == null) {
                DashboardLoading()
            } else {
                val currentSnapshot = snapshot
                if (currentSnapshot == null) {
                    EmptyDashboardPanel()
                } else {
                    ProviderGaugeCards(
                        providers = currentSnapshot.providers,
                        now = now,
                    )
                    CurrentSessionPanel(currentSnapshot.sessions)
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = DashboardSurface,
            contentColor = ClaudeAccent,
        )
    }
}

@Composable
private fun DashboardStatusStrip(
    pairing: PairingRecord,
    snapshot: DashboardSnapshot?,
    online: Boolean,
    now: Instant,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesignDot(
                color = if (online) GoodGreen else DashboardMuted,
                glow = online,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${pairing.serverName} · ${if (online) "已连接" else "未连接"}",
                style = MaterialTheme.typography.titleSmall,
                color = DashboardText,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "${formatClock(snapshot?.serverTime ?: now)}  ${if (online) "已同步" else "待同步"}",
            style = MaterialTheme.typography.bodySmall,
            color = DashboardMuted,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ProviderGaugeCards(
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        orderedProviders.forEach { provider ->
            ProviderGaugeCard(
                provider = provider,
                now = now,
            )
        }
    }
}

@Composable
private fun ProviderGaugeCard(
    provider: ProviderStatus,
    now: Instant,
) {
    val visual = provider.visualSpec()
    val fiveHour = provider.window(WindowTypes.FiveHours)
    val weekly = provider.window(WindowTypes.Weekly)
    val mainWindow = provider.mainWindow(fiveHour, weekly)
    val highlighted = mainWindow?.percentLeft?.let { it <= 25 } == true

    DesignPanel(highlighted = highlighted) {
        ProviderHeader(
            provider = provider,
            visual = visual,
            source = mainWindow?.source.orEmpty(),
        )

        QuotaRingGauge(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            percentLeft = mainWindow?.percentLeft,
            windowLabel = mainWindow?.windowType.windowShortLabel(),
            accent = visual.accent,
        )

        WindowTimerPill(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            window = mainWindow,
            now = now,
        )

        DesignDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WindowMetric(
                label = "5H",
                window = fiveHour,
            )
            WindowMetric(
                label = "周",
                window = weekly,
                alignEnd = true,
            )
        }
    }
}

@Composable
private fun ProviderHeader(
    provider: ProviderStatus,
    visual: ProviderVisual,
    source: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesignDot(color = visual.accent, glow = true)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = provider.name.ifBlank { formatProviderName(provider.id) },
                style = MaterialTheme.typography.headlineSmall,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            visual.planLabel(provider.planTier)?.let {
                Spacer(modifier = Modifier.width(8.dp))
                DesignPill(
                    text = it,
                    accent = DashboardMuted,
                )
            }
        }
        SourceBadge(source)
    }
}

@Composable
private fun SourceBadge(source: String) {
    val precise = source == "endpoint"
    Row(verticalAlignment = Alignment.CenterVertically) {
        DesignDot(
            color = if (precise) GoodGreen else WarningAmber,
            glow = precise,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = when {
                precise -> "精确"
                source.isBlank() -> "未知"
                else -> "估算"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = DashboardMuted,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WindowTimerPill(
    window: QuotaWindowStatus?,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    DesignPill(
        modifier = modifier,
        text = formatWindowTimer(window, now),
        accent = DashboardText,
    )
}

@Composable
private fun WindowMetric(
    label: String,
    window: QuotaWindowStatus?,
    alignEnd: Boolean = false,
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = DashboardMuted,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = formatWindowUsage(window),
            style = MaterialTheme.typography.bodyMedium,
            color = DashboardText,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CurrentSessionPanel(sessions: List<SessionStatus>) {
    val active = sessions.firstOrNull { it.state == "waiting" || it.state == "running" }
        ?: sessions.firstOrNull()

    DesignPanel(highlighted = active?.state == "waiting") {
        if (active == null) {
            Text(
                text = formatSessionSummary(emptyList()),
                style = MaterialTheme.typography.bodyLarge,
                color = DashboardMuted,
                fontWeight = FontWeight.SemiBold,
            )
            return@DesignPanel
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ">_",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (active.state == "waiting") WarningAmber else ClaudeAccent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = sessionSummary(active),
                    style = MaterialTheme.typography.titleMedium,
                    color = DashboardText,
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            SessionStatePill(active.state)
            Text(
                modifier = Modifier.padding(start = 12.dp),
                text = "›",
                color = DashboardMuted,
                fontSize = 28.sp,
            )
        }
    }
}

@Composable
private fun SessionStatePill(state: String) {
    val (label, accent) = when (state) {
        "waiting" -> "待确认" to WarningAmber
        "running" -> "在跑" to GoodGreen
        "done" -> "已完成" to DashboardMuted
        "error" -> "异常" to Color(0xFFE34D5A)
        else -> "未知" to DashboardMuted
    }
    DesignPill(
        text = label,
        accent = accent,
        filled = true,
    )
}

@Composable
private fun DashboardLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LoadingGaugeCard(
            name = "Claude",
            plan = "Max",
            accent = ClaudeAccent,
            highlighted = true,
        )
        LoadingGaugeCard(
            name = "Codex",
            plan = "Plus",
            accent = CodexAccent,
        )
    }
}

@Composable
private fun LoadingGaugeCard(
    name: String,
    plan: String,
    accent: Color,
    highlighted: Boolean = false,
) {
    DesignPanel(highlighted = highlighted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DesignDot(color = accent, glow = true)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            DesignPill(text = plan)
        }

        QuotaRingGauge(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            percentLeft = null,
            windowLabel = "5H",
            accent = accent,
        )

        CircularProgressIndicator(
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.CenterHorizontally),
            color = accent,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun EmptyDashboardPanel() {
    DesignPanel {
        Text(
            text = "暂无仪表盘数据",
            style = MaterialTheme.typography.titleMedium,
            color = DashboardText,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "启动 Companion 后下拉刷新。",
            style = MaterialTheme.typography.bodyMedium,
            color = DashboardMuted,
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF321820),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFE34D5A).copy(alpha = 0.32f),
        ),
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = message,
            color = Color(0xFFFF8791),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DesignDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DashboardBorder),
    )
}

private fun ProviderStatus.mainWindow(
    fiveHour: QuotaWindowStatus?,
    weekly: QuotaWindowStatus?,
): QuotaWindowStatus? {
    return when (id.lowercase(Locale.US)) {
        "codex" -> weekly?.takeIf { it.percentLeft != null } ?: fiveHour ?: weekly
        else -> fiveHour?.takeIf { it.percentLeft != null } ?: weekly ?: fiveHour
    }
}

private fun ProviderStatus.visualSpec(): ProviderVisual {
    return when (id.lowercase(Locale.US)) {
        "codex" -> ProviderVisual(
            accent = CodexAccent,
            fallbackPlan = "Plus",
        )
        else -> ProviderVisual(
            accent = ClaudeAccent,
            fallbackPlan = "Max",
        )
    }
}

private data class ProviderVisual(
    val accent: Color,
    val fallbackPlan: String,
) {
    fun planLabel(planTier: String): String? {
        return planTier.ifBlank { fallbackPlan }.takeIf { it.isNotBlank() }
    }
}

private fun String?.windowShortLabel(): String {
    return when (this) {
        WindowTypes.Weekly -> "周"
        WindowTypes.FiveHours -> "5H"
        else -> "窗口"
    }
}

private fun formatWindowUsage(window: QuotaWindowStatus?): String {
    if (window == null) {
        return "—"
    }
    val used = window.used?.let(::formatCompactCount)
    val limit = window.limit?.let(::formatCompactCount)
    return when {
        used != null && limit != null -> "$used/$limit"
        used != null -> used
        limit != null -> "—/$limit"
        else -> "—"
    }
}

private fun formatCompactCount(value: Long): String {
    val absolute = abs(value)
    return when {
        absolute >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        absolute >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        absolute >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun formatWindowTimer(
    window: QuotaWindowStatus?,
    now: Instant,
): String {
    val resetsAt = window?.resetsAt ?: return "—"
    val duration = Duration.between(now, resetsAt)
    if (duration.isNegative || duration.isZero) {
        return "正在恢复"
    }

    val remaining = formatDurationCompact(duration)
    return if (window.windowType == WindowTypes.Weekly) {
        "周窗口 $remaining 重置"
    } else {
        "约 $remaining 后满血"
    }
}

private fun formatDurationCompact(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    val days = minutes / (24 * 60)
    val hours = (minutes % (24 * 60)) / 60
    val remainingMinutes = minutes % 60

    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${remainingMinutes}m"
        else -> "${remainingMinutes}m"
    }
}

private fun sessionSummary(session: SessionStatus): String {
    val provider = formatProviderName(session.providerId)
    val project = session.projectPath.substringAfterLast('/').ifBlank { "未知项目" }
    val state = when (session.state) {
        "running" -> "正在跑"
        "waiting" -> "等待确认"
        "done" -> "已完成"
        "error" -> "异常"
        else -> session.state.ifBlank { "未知" }
    }
    return "$provider $state · $project"
}

private fun formatClock(value: Instant): String {
    return ClockFormatter.format(value)
}

private val ClockFormatter = DateTimeFormatter
    .ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

private const val Tag = "CodeGaugeDashboard"
