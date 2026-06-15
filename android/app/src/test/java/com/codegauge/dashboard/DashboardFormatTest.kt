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

    @Test
    fun primaryWindowDefaultsToFiveHours() {
        val provider = providerWithWindows()

        assertEquals(WindowTypes.FiveHours, provider.primaryWindow("5h")?.windowType)
        assertEquals(WindowTypes.FiveHours, provider.primaryWindow("unknown")?.windowType)
    }

    @Test
    fun primaryWindowCanPreferWeekly() {
        val provider = providerWithWindows()

        assertEquals(WindowTypes.Weekly, provider.primaryWindow("weekly")?.windowType)
    }

    @Test
    fun primaryWindowFallsBackWhenPreferredWindowIsMissing() {
        val provider = providerWithWindows().copy(
            windows = listOf(
                quotaWindow(WindowTypes.Weekly),
            ),
        )

        assertEquals(WindowTypes.Weekly, provider.primaryWindow("5h")?.windowType)
    }
}

private fun providerWithWindows(): ProviderStatus {
    return ProviderStatus(
        id = "codex",
        name = "Codex",
        planTier = "",
        available = true,
        windows = listOf(
            quotaWindow(WindowTypes.FiveHours),
            quotaWindow(WindowTypes.Weekly),
        ),
    )
}

private fun quotaWindow(windowType: String): QuotaWindowStatus {
    return QuotaWindowStatus(
        windowType = windowType,
        percentLeft = null,
        used = null,
        limit = null,
        resetsAt = null,
        source = "test",
        updatedAt = null,
    )
}
