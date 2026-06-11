package com.codegauge.dashboard

import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

fun formatPercentLeft(percentLeft: Int?): String {
    return percentLeft?.let { "${it.coerceIn(0, 100)}% left" } ?: "Unknown"
}

fun progressFromPercentLeft(percentLeft: Int?): Float? {
    return percentLeft?.coerceIn(0, 100)?.toFloat()?.div(100f)
}

fun formatUsage(used: Long?, limit: Long?): String {
    return when {
        used == null && limit == null -> "Usage unavailable"
        used != null && limit != null -> "${formatTokenCount(used)} / ${formatTokenCount(limit)}"
        used != null -> "${formatTokenCount(used)} tokens used"
        else -> "Limit ${formatTokenCount(limit!!)}"
    }
}

fun formatResetText(resetsAt: Instant?, now: Instant): String {
    if (resetsAt == null) {
        return "Reset time unknown"
    }

    val duration = Duration.between(now, resetsAt)
    if (!duration.isNegative && !duration.isZero) {
        return "Resets in ${formatDuration(duration)}"
    }

    return "Reset due"
}

fun formatSource(source: String): String {
    return when (source) {
        "endpoint" -> "Source: precise"
        "ccusage" -> "Source: ccusage"
        "cli" -> "Source: cli"
        "" -> "Source: unknown"
        else -> "Source: $source"
    }
}

fun formatSessionSummary(sessions: List<SessionStatus>): String {
    val active = sessions.firstOrNull { it.state == "running" || it.state == "waiting" }
        ?: return "All sessions idle"
    val provider = active.providerId.replaceFirstChar { it.uppercase() }
    val project = active.projectPath.substringAfterLast('/').ifBlank { "unknown project" }
    val state = when (active.state) {
        "running" -> "running"
        "waiting" -> "waiting"
        else -> active.state
    }
    return "$provider $state · $project"
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
        "${hours}h ${remainingMinutes}m"
    } else {
        "${remainingMinutes}m"
    }
}
