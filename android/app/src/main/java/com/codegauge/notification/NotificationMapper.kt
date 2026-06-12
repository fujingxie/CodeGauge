package com.codegauge.notification

import com.codegauge.activity.ActivityStreamMessage
import com.codegauge.activity.StreamAlert
import com.codegauge.dashboard.SessionStatus

enum class NotificationKind {
    Alert,
    Waiting,
    Done,
}

data class NotificationSpec(
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val stableKey: String,
)

object NotificationMapper {
    fun map(message: ActivityStreamMessage): NotificationSpec? {
        return when (message) {
            is ActivityStreamMessage.Alert -> mapAlert(message.alert)
            is ActivityStreamMessage.Session -> mapSession(message.session)
            is ActivityStreamMessage.Event,
            ActivityStreamMessage.Ignored,
            -> null
        }
    }

    private fun mapAlert(alert: StreamAlert): NotificationSpec {
        val provider = alert.providerId.displayName()
        val severity = alert.severity.ifBlank { "warning" }
        if (severity == "reset") {
            return NotificationSpec(
                kind = NotificationKind.Alert,
                title = "$provider 额度已恢复",
                body = "${alert.windowType} 使用率已回落到 ${alert.usagePercent}%。",
                stableKey = "alert:${alert.quotaEventKey.ifBlank { "${alert.providerId}:${alert.windowType}:reset" }}",
            )
        }

        return NotificationSpec(
            kind = NotificationKind.Alert,
            title = "$provider 额度${severity.displaySeverity()}",
            body = "${alert.windowType} 使用率 ${alert.usagePercent}%，阈值 ${alert.threshold}%。",
            stableKey = "alert:${alert.quotaEventKey.ifBlank { "${alert.providerId}:${alert.windowType}:$severity" }}",
        )
    }

    private fun mapSession(session: SessionStatus): NotificationSpec? {
        val provider = session.providerId.displayName()
        val project = session.projectPath.substringAfterLast('/').ifBlank { "Unknown project" }

        return when (session.state) {
            "waiting" -> NotificationSpec(
                kind = NotificationKind.Waiting,
                title = "$provider 等待确认",
                body = "$project 正在等待你的输入。",
                stableKey = "session:${session.providerId}:${session.projectPath}:waiting",
            )
            "done" -> NotificationSpec(
                kind = NotificationKind.Done,
                title = "$provider 任务已完成",
                body = "$project 已完成。",
                stableKey = "session:${session.providerId}:${session.projectPath}:done",
            )
            else -> null
        }
    }

    private fun String.displayName(): String {
        return replaceFirstChar { it.uppercase() }.ifBlank { "服务商" }
    }

    private fun String.displaySeverity(): String {
        return when (this) {
            "warning" -> "预警"
            "critical" -> "严重预警"
            else -> this
        }
    }
}
