package com.codegauge.notification

import com.codegauge.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    @Test
    fun totalNotificationSwitchSuppressesBusinessNotifications() {
        val settings = defaultSettings().copy(notificationsEnabled = false)

        assertFalse(NotificationPolicy.shouldShow(settings, alertSpec()))
        assertFalse(NotificationPolicy.shouldShow(settings, doneSpec()))
        assertFalse(NotificationPolicy.shouldShow(settings, resetSpec()))
        assertFalse(NotificationPolicy.shouldShow(settings, waitingSpec()))
    }

    @Test
    fun taskDoneSwitchOnlySuppressesDoneNotifications() {
        val settings = defaultSettings().copy(taskDoneNotifications = false)

        assertFalse(NotificationPolicy.shouldShow(settings, doneSpec()))
        assertTrue(NotificationPolicy.shouldShow(settings, waitingSpec()))
        assertTrue(NotificationPolicy.shouldShow(settings, alertSpec()))
    }

    @Test
    fun quotaResetSwitchOnlySuppressesResetNotifications() {
        val settings = defaultSettings().copy(quotaResetNotifications = false)

        assertFalse(NotificationPolicy.shouldShow(settings, resetSpec()))
        assertTrue(NotificationPolicy.shouldShow(settings, alertSpec()))
        assertTrue(NotificationPolicy.shouldShow(settings, doneSpec()))
    }
}

private fun defaultSettings(): AppSettings {
    return AppSettings(
        notificationsEnabled = true,
        warningThreshold = 80,
        criticalThreshold = 95,
        quotaResetNotifications = true,
        taskDoneNotifications = true,
        collectIntervalSeconds = 60,
    )
}

private fun alertSpec(): NotificationSpec {
    return NotificationSpec(
        kind = NotificationKind.Alert,
        title = "Claude 额度预警",
        body = "5 小时窗口 使用率 84%，阈值 80%。",
        stableKey = "alert:claude:5h:warning",
    )
}

private fun resetSpec(): NotificationSpec {
    return NotificationSpec(
        kind = NotificationKind.QuotaReset,
        title = "Claude 额度已恢复",
        body = "5 小时窗口 使用率已回落到 4%。",
        stableKey = "alert:claude:5h:reset",
    )
}

private fun doneSpec(): NotificationSpec {
    return NotificationSpec(
        kind = NotificationKind.Done,
        title = "Claude 任务已完成",
        body = "codegauge 已完成。",
        stableKey = "session:claude:/work/codegauge:done",
    )
}

private fun waitingSpec(): NotificationSpec {
    return NotificationSpec(
        kind = NotificationKind.Waiting,
        title = "Claude 等待确认",
        body = "codegauge 正在等待你的输入。",
        stableKey = "session:claude:/work/codegauge:waiting",
    )
}
