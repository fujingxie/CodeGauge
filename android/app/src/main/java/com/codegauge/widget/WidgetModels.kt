package com.codegauge.widget

import java.time.Instant

data class CodeGaugeWidgetState(
    val statusText: String,
    val message: String?,
    val providers: List<WidgetProviderLine>,
    val updatedAt: Instant?,
)

data class WidgetProviderLine(
    val id: String,
    val name: String,
    val fiveHourText: String,
    val weeklyText: String,
    val resetText: String,
)

