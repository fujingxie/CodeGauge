package com.codegauge.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SettingsJsonParserTest {
    @Test
    fun parsesSettingsDevicesAndDiagnostics() {
        val settings = SettingsJsonParser.parseSettings(
            """
            {
              "settings": {
                "notifications_enabled": false,
                "warning_threshold": 70,
                "critical_threshold": 92,
                "quota_reset_notifications": true,
                "task_done_notifications": false,
                "collect_interval_seconds": 45,
                "dashboard_primary_window": "weekly"
              }
            }
            """.trimIndent(),
        )

        assertFalse(settings.notificationsEnabled)
        assertEquals(70, settings.warningThreshold)
        assertEquals(92, settings.criticalThreshold)
        assertTrue(settings.quotaResetNotifications)
        assertFalse(settings.taskDoneNotifications)
        assertEquals(45, settings.collectIntervalSeconds)
        assertEquals("weekly", settings.dashboardPrimaryWindow)

        val devices = SettingsJsonParser.parseDevices(
            """
            {
              "devices": [
                {
                  "device_id": "phone-2",
                  "name": "Tablet",
                  "paired_at": "2026-06-12T02:00:00Z",
                  "last_seen_at": "2026-06-12T03:00:00Z"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, devices.size)
        assertEquals("phone-2", devices.first().deviceId)
        assertEquals("Tablet", devices.first().name)
        assertEquals(Instant.parse("2026-06-12T02:00:00Z"), devices.first().pairedAt)
        assertEquals(Instant.parse("2026-06-12T03:00:00Z"), devices.first().lastSeenAt)

        val diagnostics = SettingsJsonParser.parseDiagnostics(
            """
            {
              "ok": true,
              "server_name": "CodeGauge Companion",
              "version": "dev",
              "server_time": "2026-06-12T03:10:08Z",
              "provider_count": 2,
              "available_provider_count": 1,
              "running_session_count": 3,
              "waiting_session_count": 1,
              "paired_device_count": 2,
              "latest_event_at": null
            }
            """.trimIndent(),
        )

        assertTrue(diagnostics.ok)
        assertEquals("CodeGauge Companion", diagnostics.serverName)
        assertEquals("dev", diagnostics.version)
        assertEquals(2, diagnostics.providerCount)
        assertEquals(1, diagnostics.availableProviderCount)
        assertEquals(3, diagnostics.runningSessionCount)
        assertEquals(1, diagnostics.waitingSessionCount)
        assertEquals(2, diagnostics.pairedDeviceCount)
        assertNull(diagnostics.latestEventAt)
    }

    @Test
    fun buildsFullSettingsPatchBody() {
        val body = SettingsJsonParser.settingsPatchBody(
            SettingsUpdate(
                notificationsEnabled = false,
                warningThreshold = 72,
                criticalThreshold = 91,
                quotaResetNotifications = true,
                taskDoneNotifications = false,
                collectIntervalSeconds = 120,
                dashboardPrimaryWindow = "5h",
            ),
        )

        val settings = JSONObject(body).getJSONObject("settings")
        assertFalse(settings.getBoolean("notifications_enabled"))
        assertEquals(72, settings.getInt("warning_threshold"))
        assertEquals(91, settings.getInt("critical_threshold"))
        assertTrue(settings.getBoolean("quota_reset_notifications"))
        assertFalse(settings.getBoolean("task_done_notifications"))
        assertEquals(120, settings.getInt("collect_interval_seconds"))
        assertEquals("5h", settings.getString("dashboard_primary_window"))
    }

    @Test
    fun defaultsInvalidDashboardPrimaryWindowToFiveHours() {
        val settings = SettingsJsonParser.parseSettings(
            """
            {
              "settings": {
                "dashboard_primary_window": "daily"
              }
            }
            """.trimIndent(),
        )

        assertEquals("5h", settings.dashboardPrimaryWindow)
    }
}
