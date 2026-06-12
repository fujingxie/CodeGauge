package com.codegauge.dashboard

import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

fun formatPercentLeft(percentLeft: Int?): String {
    return percentLeft?.let { "剩余 ${it.coerceIn(0, 100)}%" } ?: "未知"
}

fun progressFromPercentLeft(percentLeft: Int?): Float? {
    return percentLeft?.coerceIn(0, 100)?.toFloat()?.div(100f)
}

fun formatUsage(used: Long?, limit: Long?): String {
    return when {
        used == null && limit == null -> "用量暂不可用"
        used != null && limit != null -> "${formatTokenCount(used)} / ${formatTokenCount(limit)} 令牌"
        used != null -> "已用 ${formatTokenCount(used)} 令牌"
        else -> "上限 ${formatTokenCount(limit!!)} 令牌"
    }
}

fun formatResetText(resetsAt: Instant?, now: Instant): String {
    if (resetsAt == null) {
        return "恢复时间未知"
    }

    val duration = Duration.between(now, resetsAt)
    if (!duration.isNegative && !duration.isZero) {
        return "约 ${formatDuration(duration)} 后恢复"
    }

    return "正在恢复"
}

fun formatSource(source: String): String {
    return when (source) {
        "endpoint" -> "精确数据"
        "ccusage" -> "ccusage 估算"
        "cli" -> "CLI 数据"
        "" -> "来源未知"
        else -> "来源：$source"
    }
}

fun formatSessionSummary(sessions: List<SessionStatus>): String {
    val active = sessions.firstOrNull { it.state == "running" || it.state == "waiting" }
        ?: return "当前没有运行中的会话"
    val provider = formatProviderName(active.providerId)
    val project = active.projectPath.substringAfterLast('/').ifBlank { "未知项目" }
    val state = when (active.state) {
        "running" -> "正在运行"
        "waiting" -> "等待确认"
        else -> active.state
    }
    return "$provider $state · $project"
}

fun formatProviderName(providerId: String?): String {
    return when (providerId?.lowercase(Locale.US)) {
        "claude" -> "Claude"
        "codex" -> "Codex"
        null, "" -> "未知服务"
        else -> providerId.replaceFirstChar { it.uppercase(Locale.US) }
    }
}

private fun formatTokenCount(value: Long): String {
    val absolute = abs(value)
    return when {
        absolute >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        absolute >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        absolute >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return if (hours > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${remainingMinutes}分钟"
    }
}
