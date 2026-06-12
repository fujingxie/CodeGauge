package com.codegauge.activity

import com.codegauge.dashboard.SessionStatus
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

object ActivityJsonParser {
    fun parseEvents(body: String): List<ActivityEvent> {
        try {
            val root = JSONObject(body)
            val eventsJson = root.optJSONArray("events") ?: return emptyList()
            return buildList {
                for (index in 0 until eventsJson.length()) {
                    add(parseEvent(eventsJson.getJSONObject(index)))
                }
            }
        } catch (exception: JSONException) {
            throw ActivityException("事件响应不是有效 JSON", exception)
        }
    }

    fun parseStreamMessage(body: String): ActivityStreamMessage {
        try {
            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: return ActivityStreamMessage.Ignored
            return when (root.optString("event_type")) {
                "event_update" -> ActivityStreamMessage.Event(parseEvent(data))
                "session_update" -> ActivityStreamMessage.Session(parseSession(data))
                "alert" -> ActivityStreamMessage.Alert(parseAlert(data))
                else -> ActivityStreamMessage.Ignored
            }
        } catch (exception: JSONException) {
            throw ActivityException("实时消息不是有效 JSON", exception)
        }
    }

    private fun parseEvent(json: JSONObject): ActivityEvent {
        return ActivityEvent(
            id = json.optLong("id"),
            type = json.optString("type"),
            providerId = json.optNullableString("provider_id"),
            payload = json.optString("payload"),
            createdAt = json.optInstant("created_at"),
        )
    }

    private fun parseSession(json: JSONObject): SessionStatus {
        return SessionStatus(
            providerId = json.optString("provider_id"),
            projectPath = json.optString("project_path"),
            state = json.optString("state"),
            lastActivityAt = json.optInstant("last_activity_at"),
        )
    }

    private fun parseAlert(json: JSONObject): StreamAlert {
        return StreamAlert(
            providerId = json.optString("provider_id"),
            windowType = json.optString("window_type"),
            severity = json.optString("severity"),
            threshold = json.optInt("threshold"),
            usagePercent = json.optInt("usage_percent"),
            quotaEventKey = json.optString("quota_event_key"),
        )
    }
}

private fun JSONObject.optNullableString(name: String): String? {
    if (isNull(name)) {
        return null
    }
    return optString(name).ifBlank { null }
}

private fun JSONObject.optInstant(name: String): Instant? {
    if (isNull(name)) {
        return null
    }
    val value = optString(name)
    if (value.isBlank()) {
        return null
    }
    return runCatching { Instant.parse(value) }.getOrNull()
}
