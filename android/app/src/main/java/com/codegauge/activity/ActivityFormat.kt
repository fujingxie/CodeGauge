package com.codegauge.activity

import com.codegauge.dashboard.SessionStatus
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

fun formatSessionTitle(session: SessionStatus): String {
    return session.projectPath.substringAfterLast('/').ifBlank { "Unknown project" }
}

fun formatSessionState(state: String): String {
    return when (state) {
        "running" -> "running"
        "waiting" -> "waiting"
        "done" -> "done"
        "error" -> "error"
        else -> state.ifBlank { "unknown" }
    }
}

fun formatEventTitle(event: ActivityEvent): String {
    return when (event.type) {
        "session_start" -> "Session started"
        "session_waiting" -> "Waiting for confirmation"
        "session_done" -> "Task finished"
        "limit_warn" -> "Quota warning"
        "limit_critical" -> "Quota critical"
        "quota_reset" -> "Quota reset"
        "error" -> "Error"
        else -> event.type.ifBlank { "Event" }
    }
}

fun formatEventDetail(event: ActivityEvent): String {
    val payload = event.payloadJson()
    val project = payload?.optString("cwd")
        ?.takeIf { it.isNotBlank() }
        ?.substringAfterLast('/')
    val provider = event.providerId?.replaceFirstChar { it.uppercase() }

    return listOfNotNull(provider, project)
        .joinToString(" · ")
        .ifBlank { "No detail" }
}

fun formatEventAge(event: ActivityEvent, now: Instant): String {
    val createdAt = event.createdAt ?: return ""
    val minutes = Duration.between(createdAt, now).toMinutes()
    return when {
        minutes <= 0 -> "now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${minutes / 60}h ${minutes % 60}m ago"
    }
}

private fun ActivityEvent.payloadJson(): JSONObject? {
    if (payload.isBlank()) {
        return null
    }
    return runCatching { JSONObject(payload) }.getOrNull()
}

