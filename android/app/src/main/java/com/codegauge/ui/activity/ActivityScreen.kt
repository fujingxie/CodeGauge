package com.codegauge.ui.activity

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codegauge.activity.ActivityEvent
import com.codegauge.activity.ActivityRepository
import com.codegauge.activity.ActivityStreamClient
import com.codegauge.activity.ActivityStreamMessage
import com.codegauge.activity.formatEventAge
import com.codegauge.activity.formatEventDetail
import com.codegauge.activity.formatEventTitle
import com.codegauge.activity.formatSessionState
import com.codegauge.activity.formatSessionTitle
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.dashboard.SessionStatus
import com.codegauge.pairing.PairingRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

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
            errorMessage = exception.message ?: "Could not load activity."
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
                        is ActivityStreamMessage.Alert -> Unit
                        ActivityStreamMessage.Ignored -> Unit
                    }
                }
            },
            onFailure = { error ->
                Log.e(Tag, "Activity stream failed", error)
                scope.launch {
                    errorMessage = error.message ?: "Activity stream disconnected."
                }
            },
        )

        onDispose {
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
            .pullRefresh(pullRefreshState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ActivityHeader()

            errorMessage?.let {
                ErrorMessage(it)
            }

            if (isLoading) {
                LoadingPanel()
            } else {
                SessionsPanel(sessions)
                EventsPanel(
                    events = events,
                    now = now,
                )
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
private fun ActivityHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Activity",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Sessions and hook events as they arrive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionsPanel(sessions: List<SessionStatus>) {
    Panel {
        Text(
            text = "Current sessions",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )

        if (sessions.isEmpty()) {
            Text(
                text = "All sessions idle",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sessions.forEach { session ->
                    SessionCard(session)
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionStatus) {
    val waiting = session.state == "waiting"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (waiting) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
                },
                shape = RoundedCornerShape(8.dp),
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (waiting) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.background.copy(alpha = 0.42f)
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatSessionTitle(session),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = session.providerId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatePill(
                text = formatSessionState(session.state),
                highlighted = waiting,
            )
        }
    }
}

@Composable
private fun EventsPanel(
    events: List<ActivityEvent>,
    now: Instant,
) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Event stream",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${events.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (events.isEmpty()) {
            Text(
                text = "No events yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                events.forEach { event ->
                    EventRow(
                        event = event,
                        now = now,
                    )
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(eventColor(event.type)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = formatEventTitle(event),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatEventAge(event, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatEventDetail(event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatePill(
    text: String,
    highlighted: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
        },
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onSecondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun eventColor(type: String) = when (type) {
    "session_waiting" -> MaterialTheme.colorScheme.secondary
    "session_done" -> MaterialTheme.colorScheme.primary
    "error", "limit_critical" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
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

private const val Tag = "CodeGaugeActivity"
