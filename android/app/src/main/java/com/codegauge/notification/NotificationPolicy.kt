package com.codegauge.notification

import com.codegauge.settings.AppSettings

object NotificationPolicy {
    fun shouldShow(settings: AppSettings, spec: NotificationSpec): Boolean {
        if (!settings.notificationsEnabled) {
            return false
        }
        return when (spec.kind) {
            NotificationKind.Alert,
            NotificationKind.Waiting,
            -> true
            NotificationKind.Done -> settings.taskDoneNotifications
            NotificationKind.QuotaReset -> settings.quotaResetNotifications
        }
    }
}
