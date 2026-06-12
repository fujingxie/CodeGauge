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
        assertEquals(null, state.providers[0].percentLeft)
        assertEquals("-", state.providers[0].percentText)
        assertEquals("5h", state.providers[0].windowLabel)
        assertEquals("已用 12.0M", state.providers[0].usageText)
        assertEquals("5h 未知 · 已用 12.0M", state.providers[0].fiveHourText)
        assertEquals("1h 30m 后满血", state.providers[0].resetText)
        assertEquals("周 未知 · 已用 142.9M", state.providers[0].weeklyText)
    }

    @Test
    fun prefersCodexWeeklyPrecisePercent() {
        val state = WidgetFormatter.fromSnapshot(
            snapshot = DashboardSnapshot(
                providers = listOf(
                    ProviderStatus(
                        id = "codex",
                        name = "Codex",
                        planTier = "",
                        available = true,
                        windows = listOf(
                            QuotaWindowStatus(
                                windowType = WindowTypes.FiveHours,
                                percentLeft = 86,
                                used = null,
                                limit = null,
                                resetsAt = Instant.parse("2026-06-12T21:01:00Z"),
                                source = "endpoint",
                                updatedAt = Instant.parse("2026-06-12T08:30:00Z"),
                            ),
                            QuotaWindowStatus(
                                windowType = WindowTypes.Weekly,
                                percentLeft = 47,
                                used = 291_500_000L,
                                limit = null,
                                resetsAt = Instant.parse("2026-06-18T02:00:00Z"),
                                source = "endpoint",
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

        val codex = state.providers.single()
        assertEquals(47, codex.percentLeft)
        assertEquals("47", codex.percentText)
        assertEquals("周", codex.windowLabel)
        assertEquals("已用 291.5M", codex.usageText)
        assertEquals("5d 17h 重置", codex.resetText)
    }

    @Test
    fun formatsUnpairedState() {
        val state = WidgetFormatter.unpaired()

        assertEquals("未配对", state.statusText)
        assertEquals("打开 App 完成配对", state.message)
    }
}
