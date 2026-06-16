package com.codegauge.ui.activity

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.codegauge.activity.ActivityEvent
import com.codegauge.activity.ActivityRepository
import com.codegauge.activity.ActivityStreamClient
import com.codegauge.activity.ActivityStreamMessage
import com.codegauge.activity.formatEventAge
import com.codegauge.activity.formatEventDetail
import com.codegauge.activity.formatEventTitle
import com.codegauge.activity.formatSessionDetail
import com.codegauge.activity.formatSessionTitle
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.dashboard.SessionStatus
import com.codegauge.pairing.PairingRecord
import com.codegauge.ui.design.DashboardBackground
import com.codegauge.ui.design.DashboardBorder
import com.codegauge.ui.design.DashboardMuted
import com.codegauge.ui.design.DashboardSurface
import com.codegauge.ui.design.DashboardSurfaceRaised
import com.codegauge.ui.design.DashboardText
import com.codegauge.ui.design.DesignDot
import com.codegauge.ui.design.DesignPanel
import com.codegauge.ui.design.DesignPill
import com.codegauge.ui.design.GoodGreen
import com.codegauge.ui.design.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ActivityRoute(
    pairing: PairingRecord,
    dashboardRepository: DashboardRepository,
    activityRepository: ActivityRepository,
    streamClient: ActivityStreamClient,
    modifier: Modifier = Modifier,
) {
    var sessions by remember(pairing.serverUrl, pairing.token) {
        mutableStateOf(emptyList<SessionStatus>())
    }
    var events by remember(pairing.serverUrl, pairing.token) {
        mutableStateOf(emptyList<ActivityEvent>())
    }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    val scope = rememberCoroutineScope()

    suspend fun loadActivity(fullScreenLoading: Boolean) {
        if (fullScreenLoading) {
            isLoading = true
        } else {
            isRefreshing = true
        }

        try {
            sessions = dashboardRepository.loadStatus(pairing).sessions
            events = activityRepository.loadEvents(pairing)
            errorMessage = null
        } catch (exception: Exception) {
            Log.e(Tag, "Load activity failed", exception)
            errorMessage = exception.message ?: "活动记录加载失败"
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(pairing.serverUrl, pairing.token) {
        loadActivity(fullScreenLoading = true)
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(30_000)
        }
    }

    DisposableEffect(pairing.serverUrl, pairing.token) {
        val disposed = AtomicBoolean(false)
        val connection = streamClient.connect(
            pairing = pairing,
            onMessage = { message ->
                scope.launch {
                    when (message) {
                        is ActivityStreamMessage.Event -> {
                            events = mergeEvent(events, message.event)
                            errorMessage = null
                        }
                        is ActivityStreamMessage.Session -> {
                            sessions = mergeSession(sessions, message.session)
                            errorMessage = null
                        }
                        ActivityStreamMessage.Quota,
                        is ActivityStreamMessage.Alert -> Unit
                        ActivityStreamMessage.Ignored -> Unit
                    }
                }
            },
            onFailure = { error ->
                if (!disposed.get()) {
                    Log.e(Tag, "Activity stream failed", error)
                    scope.launch {
                        errorMessage = error.message ?: "实时连接已断开"
                    }
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
                loadActivity(fullScreenLoading = false)
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ActivityStatusStrip(
                pairing = pairing,
                online = errorMessage == null,
                now = now,
            )
            ActivityHeader()

            errorMessage?.let {
                ErrorMessage(it)
            }

            if (isLoading) {
                LoadingPanel()
            } else {
                SessionsSection(
                    sessions = sessions,
                    now = now,
                )
                EventsSection(
                    events = events,
                    now = now,
                )
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = DashboardSurface,
            contentColor = GoodGreen,
        )
    }
}

@Composable
private fun ActivityStatusStrip(
    pairing: PairingRecord,
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
            text = "${ClockFormatter.format(now)}  ${if (online) "已同步" else "待同步"}",
            style = MaterialTheme.typography.bodySmall,
            color = DashboardMuted,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ActivityHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "活动",
            style = MaterialTheme.typography.headlineLarge,
            color = DashboardText,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "当前会话与实时事件流",
            style = MaterialTheme.typography.bodyMedium,
            color = DashboardMuted,
        )
    }
}

@Composable
private fun SessionsSection(
    sessions: List<SessionStatus>,
    now: Instant,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("当前会话")
        if (sessions.isEmpty()) {
            EmptyPanel("当前没有运行中的会话")
        } else {
            sessions.take(4).forEach { session ->
                SessionCard(
                    session = session,
                    now = now,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionStatus,
    now: Instant,
) {
    val waiting = session.state == "waiting"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (waiting) WarningAmber.copy(alpha = 0.76f) else DashboardBorder,
                shape = RoundedCornerShape(12.dp),
            ),
        shape = RoundedCornerShape(12.dp),
        color = DashboardSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile("▢")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatSessionTitle(session),
                    style = MaterialTheme.typography.titleLarge,
                    color = DashboardText,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatSessionDetail(session),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashboardMuted,
                    maxLines = 1,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                SessionStatePill(session.state)
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = formatSessionAge(session, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardMuted,
                )
            }
        }
    }
}

@Composable
private fun EventsSection(
    events: List<ActivityEvent>,
    now: Instant,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("事件流")
        if (events.isEmpty()) {
            EmptyPanel("暂无事件")
        } else {
            DesignPanel(
                contentPadding = 0.dp,
                contentSpacing = 0.dp,
            ) {
                events.take(30).forEachIndexed { index, event ->
                    EventRow(
                        event = event,
                        now = now,
                    )
                    if (index != events.take(30).lastIndex) {
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: ActivityEvent,
    now: Instant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EventIcon(event.type)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatEventTitle(event),
                style = MaterialTheme.typography.bodyLarge,
                color = DashboardText,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatEventDetail(event),
                style = MaterialTheme.typography.bodySmall,
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
private fun EventIcon(type: String) {
    val color = eventColor(type)
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = color.copy(alpha = 0.18f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = eventSymbol(type),
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
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
private fun IconTile(symbol: String) {
    Surface(
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(10.dp),
        color = DashboardSurfaceRaised,
        border = BorderStroke(1.dp, DashboardBorder),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                color = DashboardMuted,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = DashboardText,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun EmptyPanel(text: String) {
    DesignPanel(
        contentPadding = 14.dp,
        contentSpacing = 8.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = DashboardMuted,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DashboardBorder),
    )
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = GoodGreen,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF321820),
        border = BorderStroke(1.dp, Color(0xFFE34D5A).copy(alpha = 0.32f)),
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
private fun eventColor(type: String) = when (type) {
    "quota_reset" -> GoodGreen
    "session_waiting", "limit_warn" -> WarningAmber
    "session_done" -> GoodGreen
    "error", "limit_critical" -> Color(0xFFE34D5A)
    "session_start" -> Color(0xFF60709A)
    else -> DashboardMuted
}

private fun eventSymbol(type: String): String {
    return when (type) {
        "quota_reset" -> "↯"
        "session_waiting" -> "!"
        "session_done" -> "✓"
        "limit_warn" -> "△"
        "limit_critical" -> "!"
        "error" -> "×"
        "session_start" -> ">_"
        else -> "•"
    }
}

private fun formatSessionAge(
    session: SessionStatus,
    now: Instant,
): String {
    val lastActivityAt = session.lastActivityAt ?: return ""
    val minutes = java.time.Duration.between(lastActivityAt, now).toMinutes()
    return when {
        minutes <= 0 -> "刚刚"
        minutes < 60 -> "${minutes}m 前"
        else -> "${minutes / 60}h 前"
    }
}

private fun mergeEvent(
    events: List<ActivityEvent>,
    event: ActivityEvent,
): List<ActivityEvent> {
    return listOf(event)
        .plus(events.filterNot { it.id == event.id })
        .sortedByDescending { it.createdAt ?: Instant.EPOCH }
        .take(50)
}

private fun mergeSession(
    sessions: List<SessionStatus>,
    update: SessionStatus,
): List<SessionStatus> {
    val key = sessionKey(update)
    val next = sessions.filterNot { sessionKey(it) == key }
    return listOf(update).plus(next)
}

private fun sessionKey(session: SessionStatus): String {
    return "${session.providerId}:${session.projectPath}"
}

private val ClockFormatter = DateTimeFormatter
    .ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

private const val Tag = "CodeGaugeActivity"
