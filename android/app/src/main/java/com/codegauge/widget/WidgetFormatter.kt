package com.codegauge.widget

import com.codegauge.dashboard.DashboardSnapshot
import com.codegauge.dashboard.ProviderStatus
import com.codegauge.dashboard.QuotaWindowStatus
import com.codegauge.dashboard.WindowTypes
import com.codegauge.dashboard.window
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

object WidgetFormatter {
    fun unpaired(): CodeGaugeWidgetState {
        return CodeGaugeWidgetState(
            statusText = "未配对",
            message = "打开 App 完成配对",
            providers = emptyList(),
            updatedAt = null,
        )
    }

    fun error(message: String): CodeGaugeWidgetState {
        return CodeGaugeWidgetState(
            statusText = "连接异常",
            message = message,
            providers = emptyList(),
            updatedAt = Instant.now(),
        )
    }

    fun fromSnapshot(
        snapshot: DashboardSnapshot,
        now: Instant,
    ): CodeGaugeWidgetState {
        val providers = snapshot.providers
            .sortedWith(
                compareBy<ProviderStatus> {
                    when (it.id) {
                        "claude" -> 0
                        "codex" -> 1
                        else -> 2
                    }
                }.thenBy { it.name },
            )
            .map { provider ->
                WidgetProviderLine(
                    id = provider.id,
                    name = provider.name.ifBlank { provider.id },
                    fiveHourText = formatWindow("5h", provider.window(WindowTypes.FiveHours)),
                    weeklyText = formatWindow("周", provider.window(WindowTypes.Weekly)),
                    resetText = formatReset(provider.window(WindowTypes.FiveHours)?.resetsAt, now),
                )
            }

        return CodeGaugeWidgetState(
            statusText = "已连接",
            message = if (providers.isEmpty()) "暂无额度数据" else null,
            providers = providers,
            updatedAt = now,
        )
    }

    private fun formatWindow(label: String, window: QuotaWindowStatus?): String {
        val percentText = window?.percentLeft?.let { "${it.coerceIn(0, 100)}%" } ?: "未知"
        val usageText = window?.used?.let { "已用 ${formatTokenCount(it)}" } ?: "用量未知"
        return "$label $percentText · $usageText"
    }

    private fun formatReset(resetsAt: Instant?, now: Instant): String {
        if (resetsAt == null) {
            return "恢复时间未知"
        }

        val duration = Duration.between(now, resetsAt)
        if (duration.isNegative || duration.isZero) {
            return "正在恢复"
        }

        val minutes = duration.toMinutes().coerceAtLeast(0)
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (hours > 0) {
            "${hours}h ${remainingMinutes}m 后恢复"
        } else {
            "${remainingMinutes}m 后恢复"
        }
    }

    private fun formatTokenCount(value: Long): String {
        val absolute = abs(value)
        return when {
            absolute >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
            absolute >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
            absolute >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
            else -> value.toString()
        }
    }
}

