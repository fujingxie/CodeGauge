package com.codegauge.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DashboardFormatTest {
    @Test
    fun doesNotInventPercentWhenQuotaIsUnknown() {
        assertEquals("未知", formatPercentLeft(null))
    }

    @Test
    fun formatsKnownPercentLeft() {
        assertEquals("剩余 72%", formatPercentLeft(72))
    }

    @Test
    fun formatsTokenUsageWithoutLimit() {
        assertEquals("已用 22.4M 令牌", formatUsage(22_445_145L, null))
    }

    @Test
    fun formatsResetCountdown() {
        val now = Instant.parse("2026-06-11T08:00:00Z")
        val resetsAt = Instant.parse("2026-06-11T10:13:00Z")

        assertEquals("约 2小时13分钟 后恢复", formatResetText(resetsAt, now))
    }

    @Test
    fun formatsUnavailableReset() {
        assertEquals("恢复时间未知", formatResetText(null, Instant.EPOCH))
    }
}
