package com.codegauge.ui.dashboard

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
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
import com.codegauge.activity.ActivityStreamClient
import com.codegauge.activity.ActivityStreamMessage
import com.codegauge.activity.ActivityEvent
import com.codegauge.activity.ActivityRepository
import com.codegauge.activity.formatEventAge
import com.codegauge.activity.formatEventDetail
import com.codegauge.activity.formatEventTitle
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.dashboard.DashboardSnapshot
import com.codegauge.dashboard.ProviderStatus
import com.codegauge.dashboard.QuotaWindowStatus
import com.codegauge.dashboard.SessionStatus
import com.codegauge.dashboard.WindowTypes
import com.codegauge.dashboard.dashboardPrimaryWindowOrDefault
import com.codegauge.dashboard.formatProviderName
import com.codegauge.dashboard.formatSessionSummary
import com.codegauge.dashboard.primaryWindow
import com.codegauge.dashboard.window
import com.codegauge.pairing.PairingRecord
import com.codegauge.settings.SettingsRepository
import com.codegauge.ui.design.ClaudeAccent
import com.codegauge.ui.design.CodexAccent
import com.codegauge.ui.design.DashboardBackground
import com.codegauge.ui.design.DashboardBorder
import com.codegauge.ui.design.DashboardMuted
import com.codegauge.ui.design.DashboardSurface
import com.codegauge.ui.design.DashboardText
import com.codegauge.ui.design.DesignDot
import com.codegauge.ui.design.DesignPanel
import com.codegauge.ui.design.DesignPill
import com.codegauge.ui.design.GoodGreen
import com.codegauge.ui.design.QuotaRingGauge
import com.codegauge.ui.design.WarningAmber
import com.codegauge.ui.design.WeeklyRingAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardRoute(
    pairing: PairingRecord,
    repository: DashboardRepository,
    activityRepository: ActivityRepository,
    settingsRepository: SettingsRepository,
    streamClient: ActivityStreamClient,
    selectedProviderId: String?,
    onSelectedProviderIdChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var snapshot by remember(pairing.serverUrl, pairing.token) {
        mutableStateOf<DashboardSnapshot?>(null)
    }
    var detailEvents by remember(pairing.serverUrl, pairing.token, selectedProviderId) {
        mutableStateOf(emptyList<ActivityEvent>())
    }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var detailEventError by remember { mutableStateOf<String?>(null) }
    var primaryWindowType by remember(pairing.serverUrl, pairing.token) {
        mutableStateOf(WindowTypes.FiveHours)
    }
    var now by remember { mutableStateOf(Instant.now()) }
    var streamRefreshTick by remember(pairing.serverUrl, pairing.token) {
        mutableStateOf(0)
    }
    val scope = rememberCoroutineScope()

    suspend fun loadSnapshot(
        fullScreenLoading: Boolean,
        showRefreshIndicator: Boolean = !fullScreenLoading,
    ) {
        if (fullScreenLoading) {
            isLoading = true
        } else if (showRefreshIndicator) {
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
            if (showRefreshIndicator) {
                isRefreshing = false
            }
        }
    }

    suspend fun loadDetailEvents(providerId: String) {
        try {
            detailEvents = activityRepository.loadEvents(pairing)
                .filter { event -> event.providerId.equals(providerId, ignoreCase = true) }
                .take(5)
            detailEventError = null
        } catch (exception: Exception) {
            Log.e(Tag, "Load provider events failed", exception)
            detailEventError = exception.message ?: "最近事件加载失败"
        }
    }

    suspend fun loadDashboardPreference() {
        try {
            primaryWindowType = dashboardPrimaryWindowOrDefault(
                settingsRepository.loadSettings(pairing).dashboardPrimaryWindow,
            )
        } catch (exception: Exception) {
            Log.w(Tag, "Load dashboard preference failed, fallback to 5h", exception)
            primaryWindowType = WindowTypes.FiveHours
        }
    }

    LaunchedEffect(pairing.serverUrl, pairing.token) {
        loadDashboardPreference()
        loadSnapshot(fullScreenLoading = true)
    }

    LaunchedEffect(pairing.serverUrl, pairing.token, selectedProviderId) {
        while (true) {
            delay(DashboardAutoRefreshMs)
            loadSnapshot(fullScreenLoading = false, showRefreshIndicator = false)
            selectedProviderId?.let { loadDetailEvents(it) }
        }
    }

    LaunchedEffect(pairing.serverUrl, pairing.token, selectedProviderId) {
        selectedProviderId?.let { loadDetailEvents(it) }
    }

    LaunchedEffect(streamRefreshTick, selectedProviderId) {
        if (streamRefreshTick == 0) {
            return@LaunchedEffect
        }
        delay(StreamRefreshDebounceMs)
        loadSnapshot(fullScreenLoading = false, showRefreshIndicator = false)
        selectedProviderId?.let { loadDetailEvents(it) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }

    DisposableEffect(pairing.serverUrl, pairing.token, streamClient) {
        val disposed = AtomicBoolean(false)
        val connection = streamClient.connect(
            pairing = pairing,
            onMessage = { message ->
                if (message.shouldRefreshDashboard()) {
                    scope.launch {
                        streamRefreshTick += 1
                    }
                }
            },
            onFailure = { error ->
                if (!disposed.get()) {
                    Log.w(Tag, "Dashboard stream disconnected; polling fallback remains active", error)
                }
            },
        )

        onDispose {
            disposed.set(true)
            connection.close()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                loadDashboardPreference()
                loadSnapshot(fullScreenLoading = false)
                selectedProviderId?.let { loadDetailEvents(it) }
            }
        },
    )

    BackHandler(enabled = selectedProviderId != null) {
        onSelectedProviderIdChange(null)
    }

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
            val currentSnapshot = snapshot
            val selectedProvider = selectedProviderId?.let { providerId ->
                currentSnapshot?.providers?.firstOrNull { it.id.equals(providerId, ignoreCase = true) }
            }

            if (selectedProvider != null) {
                ProviderDetailScreen(
                    provider = selectedProvider,
                    events = detailEvents,
                    eventError = detailEventError,
                    primaryWindowType = primaryWindowType,
                    now = now,
                    onBack = { onSelectedProviderIdChange(null) },
                )
            } else {
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
                } else if (currentSnapshot == null) {
                    EmptyDashboardPanel()
                } else {
                    ProviderGaugeCards(
                        providers = currentSnapshot.providers,
                        primaryWindowType = primaryWindowType,
                        now = now,
                        onProviderClick = { onSelectedProviderIdChange(it.id) },
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
    primaryWindowType: String,
    now: Instant,
    onProviderClick: (ProviderStatus) -> Unit,
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
                primaryWindowType = primaryWindowType,
                now = now,
                onClick = { onProviderClick(provider) },
            )
        }
    }
}

@Composable
private fun ProviderGaugeCard(
    provider: ProviderStatus,
    primaryWindowType: String,
    now: Instant,
    onClick: () -> Unit,
) {
    val visual = provider.visualSpec()
    val fiveHour = provider.window(WindowTypes.FiveHours)
    val weekly = provider.window(WindowTypes.Weekly)
    val mainWindow = provider.primaryWindow(primaryWindowType)
    val highlighted = listOfNotNull(fiveHour?.percentLeft, weekly?.percentLeft).any { it <= 25 }

    DesignPanel(
        modifier = Modifier.clickable(onClick = onClick),
        highlighted = highlighted,
    ) {
        ProviderHeader(
            provider = provider,
            visual = visual,
            source = mainWindow?.source.orEmpty(),
        )

        QuotaRingGauge(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            centerPercentLeft = mainWindow?.percentLeft,
            windowLabel = mainWindow?.windowType.windowShortLabel(),
            outerPercentLeft = fiveHour?.percentLeft,
            innerPercentLeft = weekly?.percentLeft,
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
                indicatorColor = visual.accent,
            )
            WindowMetric(
                label = "周",
                window = weekly,
                alignEnd = true,
                indicatorColor = WeeklyRingAccent,
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
    indicatorColor: Color? = null,
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (indicatorColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(indicatorColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = DashboardMuted,
                fontWeight = FontWeight.Bold,
            )
        }
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
private fun ProviderDetailScreen(
    provider: ProviderStatus,
    events: List<ActivityEvent>,
    eventError: String?,
    primaryWindowType: String,
    now: Instant,
    onBack: () -> Unit,
) {
    val visual = provider.visualSpec()
    val fiveHour = provider.window(WindowTypes.FiveHours)
    val weekly = provider.window(WindowTypes.Weekly)
    val mainWindow = provider.primaryWindow(primaryWindowType)
    val hasWindowData = provider.windows.any { window ->
        window.percentLeft != null || window.used != null || window.limit != null || window.resetsAt != null
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProviderDetailHeader(
            provider = provider,
            visual = visual,
            onBack = onBack,
        )

        QuotaRingGauge(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            centerPercentLeft = mainWindow?.percentLeft,
            windowLabel = mainWindow?.windowType.windowShortLabel(),
            outerPercentLeft = fiveHour?.percentLeft,
            innerPercentLeft = weekly?.percentLeft,
            accent = visual.accent,
            gaugeSize = 224.dp,
            valueFontSize = 48.sp,
            percentFontSize = 13.sp,
            labelFontSize = 13.sp,
        )

        WindowTimerPill(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            window = mainWindow,
            now = now,
        )

        DetailWindowPanel(
            title = "5h 窗口",
            window = fiveHour,
            resetLabel = "精确恢复",
        )
        DetailWindowPanel(
            title = "周窗口",
            window = weekly,
            resetLabel = "重置",
        )

        if (!hasWindowData) {
            Text(
                text = "可能原因：Companion 未上报本服务商，或服务商接口超时。下拉重试。",
                style = MaterialTheme.typography.bodyMedium,
                color = DashboardMuted,
                lineHeight = 22.sp,
            )
        }

        ProviderRecentEvents(
            provider = provider,
            events = events,
            eventError = eventError,
            now = now,
        )
    }
}

@Composable
private fun ProviderDetailHeader(
    provider: ProviderStatus,
    visual: ProviderVisual,
    onBack: () -> Unit,
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
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onBack),
                shape = CircleShape,
                color = DashboardSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DashboardBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "‹",
                        color = DashboardText,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
            Spacer(modifier = Modifier.width(18.dp))
            DesignDot(color = visual.accent, glow = true)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = provider.name.ifBlank { formatProviderName(provider.id) },
                style = MaterialTheme.typography.headlineMedium,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            visual.planLabel(provider.planTier)?.let {
                Spacer(modifier = Modifier.width(8.dp))
                DesignPill(text = it)
            }
        }
    }
}

@Composable
private fun DetailWindowPanel(
    title: String,
    window: QuotaWindowStatus?,
    resetLabel: String,
) {
    DesignPanel(
        contentPadding = 14.dp,
        contentSpacing = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detailSourceLabel(window?.source),
                style = MaterialTheme.typography.titleSmall,
                color = DashboardMuted,
                fontWeight = FontWeight.Bold,
            )
        }

        DetailMetricRow("used", detailCount(window?.used))
        DetailMetricRow("limit", detailCount(window?.limit))
        DetailMetricRow(
            label = "剩余",
            value = window?.percentLeft?.let { "${it.coerceIn(0, 100)}%" } ?: "—",
            emphasized = window?.percentLeft?.let { it <= 25 } == true,
        )
        DetailMetricRow(resetLabel, detailReset(window))
        DetailMetricRow(
            label = "来源",
            value = detailSourceLabel(window?.source),
            last = true,
            strong = true,
        )
    }
}

@Composable
private fun DetailMetricRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
    strong: Boolean = false,
    last: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = DashboardMuted,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    emphasized -> WarningAmber
                    strong -> DashboardText
                    else -> DashboardText.copy(alpha = 0.92f)
                },
                fontWeight = if (strong || emphasized) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (!last) {
            DesignDivider()
        }
    }
}

@Composable
private fun ProviderRecentEvents(
    provider: ProviderStatus,
    events: List<ActivityEvent>,
    eventError: String?,
    now: Instant,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "最近事件",
            style = MaterialTheme.typography.headlineSmall,
            color = DashboardText,
            fontWeight = FontWeight.Bold,
        )

        eventError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFF8791),
            )
        }

        if (events.isEmpty()) {
            DesignPanel {
                DetailEventPlaceholder(provider)
            }
        } else {
            DesignPanel(contentPadding = 0.dp, contentSpacing = 0.dp) {
                events.forEachIndexed { index, event ->
                    DetailEventRow(
                        event = event,
                        now = now,
                    )
                    if (index != events.lastIndex) {
                        DesignDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailEventPlaceholder(provider: ProviderStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EventBadge(
            symbol = ">_",
            accent = DashboardMuted,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "暂无相关事件",
                style = MaterialTheme.typography.titleMedium,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = provider.name.ifBlank { formatProviderName(provider.id) },
                style = MaterialTheme.typography.bodyMedium,
                color = DashboardMuted,
            )
        }
    }
}

@Composable
private fun DetailEventRow(
    event: ActivityEvent,
    now: Instant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EventBadge(
            symbol = eventSymbol(event.type),
            accent = eventAccent(event.type),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatEventTitle(event),
                style = MaterialTheme.typography.titleMedium,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = formatEventDetail(event),
                style = MaterialTheme.typography.bodyMedium,
                color = DashboardMuted,
                maxLines = 1,
            )
        }
        Text(
            text = formatEventAge(event, now),
            style = MaterialTheme.typography.bodySmall,
            color = DashboardMuted,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun EventBadge(
    symbol: String,
    accent: Color,
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = accent.copy(alpha = 0.16f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
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
            centerPercentLeft = null,
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

private fun detailCount(value: Long?): String {
    return value?.let(::formatCompactCount) ?: "—"
}

private fun detailReset(window: QuotaWindowStatus?): String {
    val resetsAt = window?.resetsAt ?: return "—"
    return if (window.windowType == WindowTypes.Weekly) {
        DetailDateFormatter.format(resetsAt)
    } else {
        ClockFormatter.format(resetsAt)
    }
}

private fun detailSourceLabel(source: String?): String {
    return when (source) {
        "endpoint" -> "精确"
        "ccusage" -> "估算"
        "cli" -> "CLI"
        null, "" -> "—"
        else -> source
    }
}

private fun eventSymbol(type: String): String {
    return when (type) {
        "quota_reset" -> "↯"
        "limit_warn" -> "△"
        "limit_critical" -> "!"
        "session_done" -> "✓"
        "session_waiting" -> "?"
        "session_start" -> ">_"
        "error" -> "×"
        else -> "•"
    }
}

private fun eventAccent(type: String): Color {
    return when (type) {
        "quota_reset" -> GoodGreen
        "limit_warn" -> WarningAmber
        "limit_critical" -> ClaudeAccent
        "session_done" -> GoodGreen
        "session_waiting" -> WarningAmber
        "error" -> Color(0xFFE34D5A)
        else -> Color(0xFF7A8AB3)
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

private val DetailDateFormatter = DateTimeFormatter
    .ofPattern("M月d日 HH:mm")
    .withZone(ZoneId.systemDefault())

private const val Tag = "CodeGaugeDashboard"
private const val DashboardAutoRefreshMs = 30_000L
private const val StreamRefreshDebounceMs = 500L

private fun ActivityStreamMessage.shouldRefreshDashboard(): Boolean {
    return when (this) {
        ActivityStreamMessage.Quota,
        is ActivityStreamMessage.Event,
        is ActivityStreamMessage.Session,
        is ActivityStreamMessage.Alert,
        -> true
        ActivityStreamMessage.Ignored -> false
    }
}
