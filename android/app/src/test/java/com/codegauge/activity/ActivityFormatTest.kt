package com.codegauge.activity

import com.codegauge.dashboard.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ActivityFormatTest {
    @Test
    fun labelsBlankProjectSessionAsBackgroundProcess() {
        val session = SessionStatus(
            providerId = "claude",
            projectPath = "",
            state = "running",
            lastActivityAt = Instant.EPOCH,
        )

        assertEquals("Claude 后台进程", formatSessionTitle(session))
        assertEquals("进程检测 · 暂无项目路径", formatSessionDetail(session))
    }

    @Test
    fun keepsProjectNameWhenPathIsKnown() {
        val session = SessionStatus(
            providerId = "claude",
            projectPath = "/Users/me/CodeGauge",
            state = "running",
            lastActivityAt = Instant.EPOCH,
        )

        assertEquals("CodeGauge", formatSessionTitle(session))
        assertEquals("/Users/me/CodeGauge", formatSessionDetail(session))
    }
}
