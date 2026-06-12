package com.codegauge.activity

import com.codegauge.dashboard.SessionStatus
import java.time.Instant

data class ActivityEvent(
    val id: Long,
    val type: String,
    val providerId: String?,
    val payload: String,
    val createdAt: Instant?,
)

data class StreamAlert(
    val providerId: String,
    val windowType: String,
    val severity: String,
    val threshold: Int,
    val usagePercent: Int,
    val quotaEventKey: String,
)

sealed interface ActivityStreamMessage {
    data class Event(val event: ActivityEvent) : ActivityStreamMessage

    data class Session(val session: SessionStatus) : ActivityStreamMessage

    data class Alert(val alert: StreamAlert) : ActivityStreamMessage

    data object Ignored : ActivityStreamMessage
}
