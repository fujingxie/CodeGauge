package com.codegauge.dashboard

import java.time.Instant

data class DashboardSnapshot(
    val providers: List<ProviderStatus>,
    val sessions: List<SessionStatus>,
    val serverTime: Instant?,
)

data class ProviderStatus(
    val id: String,
    val name: String,
    val planTier: String,
    val available: Boolean,
    val windows: List<QuotaWindowStatus>,
)

data class QuotaWindowStatus(
    val windowType: String,
    val percentLeft: Int?,
    val used: Long?,
    val limit: Long?,
    val resetsAt: Instant?,
    val source: String,
    val updatedAt: Instant?,
)

data class SessionStatus(
    val providerId: String,
    val projectPath: String,
    val state: String,
    val lastActivityAt: Instant?,
)

fun ProviderStatus.window(windowType: String): QuotaWindowStatus? {
    return windows.firstOrNull { it.windowType == windowType }
}

object WindowTypes {
    const val FiveHours = "5h"
    const val Weekly = "weekly"
}

