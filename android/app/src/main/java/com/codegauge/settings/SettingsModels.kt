package com.codegauge.settings

import java.time.Instant

data class AppSettings(
    val notificationsEnabled: Boolean,
    val warningThreshold: Int,
    val criticalThreshold: Int,
    val quotaResetNotifications: Boolean,
    val taskDoneNotifications: Boolean,
    val collectIntervalSeconds: Int,
    val dashboardPrimaryWindow: String,
)

data class SettingsUpdate(
    val notificationsEnabled: Boolean? = null,
    val warningThreshold: Int? = null,
    val criticalThreshold: Int? = null,
    val quotaResetNotifications: Boolean? = null,
    val taskDoneNotifications: Boolean? = null,
    val collectIntervalSeconds: Int? = null,
    val dashboardPrimaryWindow: String? = null,
) {
    companion object {
        fun from(settings: AppSettings): SettingsUpdate {
            return SettingsUpdate(
                notificationsEnabled = settings.notificationsEnabled,
                warningThreshold = settings.warningThreshold,
                criticalThreshold = settings.criticalThreshold,
                quotaResetNotifications = settings.quotaResetNotifications,
                taskDoneNotifications = settings.taskDoneNotifications,
                collectIntervalSeconds = settings.collectIntervalSeconds,
                dashboardPrimaryWindow = settings.dashboardPrimaryWindow,
            )
        }
    }
}

data class PairedDevice(
    val deviceId: String,
    val name: String,
    val pairedAt: Instant?,
    val lastSeenAt: Instant?,
)

data class CompanionDiagnostics(
    val ok: Boolean,
    val serverName: String,
    val version: String,
    val serverTime: Instant?,
    val providerCount: Int,
    val availableProviderCount: Int,
    val runningSessionCount: Int,
    val waitingSessionCount: Int,
    val pairedDeviceCount: Int,
    val latestEventAt: Instant?,
)

data class SettingsSnapshot(
    val settings: AppSettings,
    val devices: List<PairedDevice>,
    val diagnostics: CompanionDiagnostics,
)
