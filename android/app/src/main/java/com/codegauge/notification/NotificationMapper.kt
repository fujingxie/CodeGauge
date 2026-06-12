package com.codegauge.notification

import com.codegauge.activity.ActivityStreamMessage
import com.codegauge.activity.StreamAlert
import com.codegauge.dashboard.SessionStatus
import com.codegauge.dashboard.formatProviderName

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
        val provider = formatProviderName(alert.providerId)
        val window = alert.windowType.displayWindowType()
        val severity = alert.severity.ifBlank { "warning" }
        if (severity == "reset") {
            return NotificationSpec(
                kind = NotificationKind.Alert,
                title = "$provider 额度已恢复",
                body = "$window 使用率已回落到 ${alert.usagePercent}%。",
                stableKey = "alert:${alert.quotaEventKey.ifBlank { "${alert.providerId}:${alert.windowType}:reset" }}",
            )
        }

        return NotificationSpec(
            kind = NotificationKind.Alert,
            title = "$provider 额度${severity.displaySeverity()}",
            body = "$window 使用率 ${alert.usagePercent}%，阈值 ${alert.threshold}%。",
            stableKey = "alert:${alert.quotaEventKey.ifBlank { "${alert.providerId}:${alert.windowType}:$severity" }}",
        )
    }

    private fun mapSession(session: SessionStatus): NotificationSpec? {
        val provider = formatProviderName(session.providerId)
        val project = session.projectPath.substringAfterLast('/').ifBlank { "未知项目" }

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

    private fun String.displaySeverity(): String {
        return when (this) {
            "warning" -> "预警"
            "critical" -> "严重预警"
            else -> this
        }
    }

    private fun String.displayWindowType(): String {
        return when (this) {
            "5h" -> "5 小时窗口"
            "weekly" -> "周额度"
            else -> ifBlank { "额度窗口" }
        }
    }
}
