package com.codegauge.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DashboardFormatTest {
    @Test
    fun doesNotInventPercentWhenQuotaIsUnknown() {
        assertEquals("Unknown", formatPercentLeft(null))
    }

    @Test
    fun formatsKnownPercentLeft() {
        assertEquals("72% left", formatPercentLeft(72))
    }

    @Test
    fun formatsTokenUsageWithoutLimit() {
        assertEquals("22.4M tokens used", formatUsage(22_445_145L, null))
    }

    @Test
    fun formatsResetCountdown() {
        val now = Instant.parse("2026-06-11T08:00:00Z")
        val resetsAt = Instant.parse("2026-06-11T10:13:00Z")

        assertEquals("Resets in 2h 13m", formatResetText(resetsAt, now))
    }

    @Test
    fun formatsUnavailableReset() {
        assertEquals("Reset time unknown", formatResetText(null, Instant.EPOCH))
    }
}

