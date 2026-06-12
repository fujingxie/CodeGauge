package com.codegauge.widget

import com.codegauge.dashboard.DashboardSnapshot
import com.codegauge.dashboard.ProviderStatus
import com.codegauge.dashboard.QuotaWindowStatus
import com.codegauge.dashboard.WindowTypes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WidgetFormatterTest {
    @Test
    fun formatsProvidersWithUnknownPercentAndKnownUsage() {
        val state = WidgetFormatter.fromSnapshot(
            snapshot = DashboardSnapshot(
                providers = listOf(
                    ProviderStatus(
                        id = "claude",
                        name = "Claude",
                        planTier = "",
                        available = true,
                        windows = listOf(
                            QuotaWindowStatus(
                                windowType = WindowTypes.FiveHours,
                                percentLeft = null,
                                used = 12_034_000L,
                                limit = null,
                                resetsAt = Instant.parse("2026-06-12T10:00:00Z"),
                                source = "ccusage",
                                updatedAt = Instant.parse("2026-06-12T08:30:00Z"),
                            ),
                            QuotaWindowStatus(
                                windowType = WindowTypes.Weekly,
                                percentLeft = null,
                                used = 142_900_000L,
                                limit = null,
                                resetsAt = null,
                                source = "ccusage",
                                updatedAt = Instant.parse("2026-06-12T08:30:00Z"),
                            ),
                        ),
                    ),
                ),
                sessions = emptyList(),
                serverTime = Instant.parse("2026-06-12T08:30:00Z"),
            ),
            now = Instant.parse("2026-06-12T08:30:00Z"),
        )

        assertEquals("已连接", state.statusText)
        assertEquals(1, state.providers.size)
        assertEquals("Claude", state.providers[0].name)
        assertEquals("5h 未知 · 已用 12.0M", state.providers[0].fiveHourText)
        assertEquals("1h 30m 后恢复", state.providers[0].resetText)
        assertEquals("周 未知 · 已用 142.9M", state.providers[0].weeklyText)
    }

    @Test
    fun formatsUnpairedState() {
        val state = WidgetFormatter.unpaired()

        assertEquals("未配对", state.statusText)
        assertEquals("打开 App 完成配对", state.message)
    }
}

