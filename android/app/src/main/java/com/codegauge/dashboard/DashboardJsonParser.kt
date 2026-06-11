package com.codegauge.dashboard

import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

object DashboardJsonParser {
    fun parseStatus(body: String): DashboardSnapshot {
        try {
            val root = JSONObject(body)
            val providersJson = root.optJSONArray("providers")
            val sessionsJson = root.optJSONArray("sessions")

            val providers = buildList {
                if (providersJson != null) {
                    for (index in 0 until providersJson.length()) {
                        add(parseProvider(providersJson.getJSONObject(index)))
                    }
                }
            }
            val sessions = buildList {
                if (sessionsJson != null) {
                    for (index in 0 until sessionsJson.length()) {
                        add(parseSession(sessionsJson.getJSONObject(index)))
                    }
                }
            }

            return DashboardSnapshot(
                providers = providers,
                sessions = sessions,
                serverTime = root.optInstant("server_time"),
            )
        } catch (exception: JSONException) {
            throw DashboardException("Status response was not valid JSON", exception)
        }
    }

    private fun parseProvider(json: JSONObject): ProviderStatus {
        val windowsJson = json.optJSONArray("windows")
        val windows = buildList {
            if (windowsJson != null) {
                for (index in 0 until windowsJson.length()) {
                    add(parseWindow(windowsJson.getJSONObject(index)))
                }
            }
        }

        return ProviderStatus(
            id = json.optString("id"),
            name = json.optString("name").ifBlank { json.optString("id") },
            planTier = json.optString("plan_tier"),
            available = json.optBoolean("available", false),
            windows = windows,
        )
    }

    private fun parseWindow(json: JSONObject): QuotaWindowStatus {
        return QuotaWindowStatus(
            windowType = json.optString("window_type"),
            percentLeft = json.optNullableInt("percent_left"),
            used = json.optNullableLong("used"),
            limit = json.optNullableLong("limit"),
            resetsAt = json.optInstant("resets_at"),
            source = json.optString("source"),
            updatedAt = json.optInstant("updated_at"),
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
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (isNull(name)) null else optInt(name)
}

private fun JSONObject.optNullableLong(name: String): Long? {
    return if (isNull(name)) null else optLong(name)
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

