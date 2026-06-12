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
    val percentLeft: Int?,
    val percentText: String,
    val windowLabel: String,
    val usageText: String,
    val resetText: String,
    val fiveHourText: String,
    val weeklyText: String,
)
