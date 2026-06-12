package com.codegauge.settings

import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

object SettingsJsonParser {
    fun parseSettings(body: String): AppSettings {
        try {
            val settings = JSONObject(body).optJSONObject("settings")
                ?: throw SettingsException("Settings response did not include settings")
            return parseSettingsObject(settings)
        } catch (exception: JSONException) {
            throw SettingsException("Settings response was not valid JSON", exception)
        }
    }

    fun parseDevices(body: String): List<PairedDevice> {
        try {
            val root = JSONObject(body)
            val devicesJson = root.optJSONArray("devices") ?: return emptyList()
            return buildList {
                for (index in 0 until devicesJson.length()) {
                    val item = devicesJson.getJSONObject(index)
                    add(
                        PairedDevice(
                            deviceId = item.optString("device_id"),
                            name = item.optString("name"),
                            pairedAt = item.optInstant("paired_at"),
                            lastSeenAt = item.optInstant("last_seen_at"),
                        ),
                    )
                }
            }
        } catch (exception: JSONException) {
            throw SettingsException("Devices response was not valid JSON", exception)
        }
    }

    fun parseDiagnostics(body: String): CompanionDiagnostics {
        try {
            val root = JSONObject(body)
            return CompanionDiagnostics(
                ok = root.optBoolean("ok", false),
                serverName = root.optString("server_name"),
                version = root.optString("version"),
                serverTime = root.optInstant("server_time"),
                providerCount = root.optInt("provider_count"),
                availableProviderCount = root.optInt("available_provider_count"),
                runningSessionCount = root.optInt("running_session_count"),
                waitingSessionCount = root.optInt("waiting_session_count"),
                pairedDeviceCount = root.optInt("paired_device_count"),
                latestEventAt = root.optInstant("latest_event_at"),
            )
        } catch (exception: JSONException) {
            throw SettingsException("Diagnostics response was not valid JSON", exception)
        }
    }

    fun settingsPatchBody(update: SettingsUpdate): String {
        val settings = JSONObject()
        update.notificationsEnabled?.let {
            settings.put("notifications_enabled", it)
        }
        update.warningThreshold?.let {
            settings.put("warning_threshold", it)
        }
        update.criticalThreshold?.let {
            settings.put("critical_threshold", it)
        }
        update.quotaResetNotifications?.let {
            settings.put("quota_reset_notifications", it)
        }
        update.taskDoneNotifications?.let {
            settings.put("task_done_notifications", it)
        }
        update.collectIntervalSeconds?.let {
            settings.put("collect_interval_seconds", it)
        }
        return JSONObject()
            .put("settings", settings)
            .toString()
    }

    private fun parseSettingsObject(json: JSONObject): AppSettings {
        return AppSettings(
            notificationsEnabled = json.optBoolean("notifications_enabled", true),
            warningThreshold = json.optInt("warning_threshold", 80),
            criticalThreshold = json.optInt("critical_threshold", 95),
            quotaResetNotifications = json.optBoolean("quota_reset_notifications", true),
            taskDoneNotifications = json.optBoolean("task_done_notifications", true),
            collectIntervalSeconds = json.optInt("collect_interval_seconds", 60),
        )
    }
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
