package com.codegauge.activity

import com.codegauge.dashboard.SessionStatus
import com.codegauge.dashboard.formatProviderName
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

fun formatSessionTitle(session: SessionStatus): String {
    val project = session.projectPath.substringAfterLast('/').ifBlank { "" }
    if (project.isNotBlank()) {
        return project
    }
    val provider = formatProviderName(session.providerId).takeIf { it != "未知服务" }
    return listOfNotNull(provider, "后台进程")
        .joinToString(" ")
}

fun formatSessionDetail(session: SessionStatus): String {
    return session.projectPath.ifBlank { "进程检测 · 暂无项目路径" }
}

fun formatSessionState(state: String): String {
    return when (state) {
        "running" -> "运行中"
        "waiting" -> "等待确认"
        "done" -> "已完成"
        "error" -> "异常"
        else -> state.ifBlank { "未知" }
    }
}

fun formatEventTitle(event: ActivityEvent): String {
    return when (event.type) {
        "session_start" -> "会话开始"
        "session_waiting" -> "等待你确认"
        "session_done" -> "任务完成"
        "limit_warn" -> "额度预警"
        "limit_critical" -> "额度紧张"
        "quota_reset" -> "额度恢复"
        "error" -> "异常事件"
        else -> event.type.ifBlank { "事件" }
    }
}

fun formatEventDetail(event: ActivityEvent): String {
    val payload = event.payloadJson()
    val project = payload?.optString("cwd")
        ?.takeIf { it.isNotBlank() }
        ?.substringAfterLast('/')
    val provider = event.providerId?.let(::formatProviderName)

    return listOfNotNull(provider, project)
        .joinToString(" · ")
        .ifBlank { "暂无详情" }
}

fun formatEventAge(event: ActivityEvent, now: Instant): String {
    val createdAt = event.createdAt ?: return ""
    val minutes = Duration.between(createdAt, now).toMinutes()
    return when {
        minutes <= 0 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        else -> "${minutes / 60}小时${minutes % 60}分钟前"
    }
}

private fun ActivityEvent.payloadJson(): JSONObject? {
    if (payload.isBlank()) {
        return null
    }
    return runCatching { JSONObject(payload) }.getOrNull()
}
