package com.codegauge.notification

import com.codegauge.activity.ActivityEvent
import com.codegauge.activity.ActivityStreamMessage
import com.codegauge.activity.StreamAlert
import com.codegauge.dashboard.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class NotificationMapperTest {
    @Test
    fun mapsWaitingSessionToNotification() {
        val spec = NotificationMapper.map(
            ActivityStreamMessage.Session(
                SessionStatus(
                    providerId = "claude",
                    projectPath = "/work/codegauge",
                    state = "waiting",
                    lastActivityAt = Instant.EPOCH,
                ),
            ),
        )

        assertEquals(NotificationKind.Waiting, spec?.kind)
        assertEquals("Claude 等待确认", spec?.title)
        assertEquals("codegauge 正在等待你的输入。", spec?.body)
    }

    @Test
    fun mapsDoneSessionToNotification() {
        val spec = NotificationMapper.map(
            ActivityStreamMessage.Session(
                SessionStatus(
                    providerId = "claude",
                    projectPath = "/work/codegauge",
                    state = "done",
                    lastActivityAt = Instant.EPOCH,
                ),
            ),
        )

        assertEquals(NotificationKind.Done, spec?.kind)
        assertEquals("Claude 任务已完成", spec?.title)
        assertEquals("codegauge 已完成。", spec?.body)
    }

    @Test
    fun mapsQuotaAlertToNotification() {
        val spec = NotificationMapper.map(
            ActivityStreamMessage.Alert(
                StreamAlert(
                    providerId = "claude",
                    windowType = "5h",
                    severity = "critical",
                    threshold = 95,
                    usagePercent = 97,
                    quotaEventKey = "claude:5h:critical",
                ),
            ),
        )

        assertEquals(NotificationKind.Alert, spec?.kind)
        assertEquals("Claude 额度严重预警", spec?.title)
        assertEquals("5 小时窗口 使用率 97%，阈值 95%。", spec?.body)
    }

    @Test
    fun mapsQuotaResetToNotification() {
        val spec = NotificationMapper.map(
            ActivityStreamMessage.Alert(
                StreamAlert(
                    providerId = "claude",
                    windowType = "5h",
                    severity = "reset",
                    threshold = 80,
                    usagePercent = 4,
                    quotaEventKey = "claude:5h:reset",
                ),
            ),
        )

        assertEquals(NotificationKind.QuotaReset, spec?.kind)
        assertEquals("Claude 额度已恢复", spec?.title)
        assertEquals("5 小时窗口 使用率已回落到 4%。", spec?.body)
    }

    @Test
    fun ignoresRunningSessionAndRawEventUpdates() {
        assertNull(
            NotificationMapper.map(
                ActivityStreamMessage.Session(
                    SessionStatus(
                        providerId = "codex",
                        projectPath = "",
                        state = "running",
                        lastActivityAt = Instant.EPOCH,
                    ),
                ),
            ),
        )
        assertNull(
            NotificationMapper.map(
                ActivityStreamMessage.Event(
                    ActivityEvent(
                        id = 1,
                        type = "session_done",
                        providerId = "claude",
                        payload = "{}",
                        createdAt = Instant.EPOCH,
                    ),
                ),
            ),
        )
    }
}
