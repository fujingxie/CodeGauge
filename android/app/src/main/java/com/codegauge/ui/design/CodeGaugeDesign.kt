package com.codegauge.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val DashboardBackground = Color(0xFF090D13)
internal val DashboardSurface = Color(0xFF121821)
internal val DashboardSurfaceRaised = Color(0xFF151C27)
internal val DashboardBorder = Color(0xFF273141)
internal val DashboardMuted = Color(0xFF8A93A3)
internal val DashboardText = Color(0xFFE9EEF7)
internal val ClaudeAccent = Color(0xFFE97857)
internal val CodexAccent = Color(0xFF17C398)
internal val GaugeWarning = Color(0xFFE8C24A)
internal val WeeklyRingAccent = Color(0xFF79A7FF)
internal val GoodGreen = Color(0xFF38D06D)
internal val WarningAmber = Color(0xFFE8C24A)

@Composable
internal fun DesignPanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    contentPadding: Dp = 9.dp,
    contentSpacing: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (highlighted) {
                    WarningAmber.copy(alpha = 0.74f)
                } else {
                    DashboardBorder
                },
                shape = RoundedCornerShape(12.dp),
            ),
        shape = RoundedCornerShape(12.dp),
        color = DashboardSurface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content,
        )
    }
}

@Composable
internal fun DesignPill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = DashboardMuted,
    filled: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(99.dp),
        color = if (filled) accent.copy(alpha = 0.18f) else DashboardSurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = accent.copy(alpha = if (filled) 0.34f else 0.18f),
        ),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) accent else DashboardMuted,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun DesignDot(
    color: Color,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
) {
    Box(
        modifier = modifier.size(if (glow) 12.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (glow) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.20f)),
            )
        }
        Box(
            modifier = Modifier
                .size(if (glow) 8.dp else 9.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
internal fun QuotaRingGauge(
    centerPercentLeft: Int?,
    windowLabel: String,
    outerPercentLeft: Int? = centerPercentLeft,
    innerPercentLeft: Int? = null,
    accent: Color,
    modifier: Modifier = Modifier,
    gaugeSize: Dp = 126.dp,
    valueFontSize: TextUnit = 30.sp,
    percentFontSize: TextUnit = 11.sp,
    labelFontSize: TextUnit = 11.sp,
) {
    Box(
        modifier = modifier.size(gaugeSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.062f
            val outerRadius = size.minDimension * 0.39f
            val innerRadius = size.minDimension * 0.285f
            drawRingTrack(outerRadius, stroke)
            drawRingTrack(innerRadius, stroke)

            if (outerPercentLeft == null && innerPercentLeft == null) {
                drawUnavailableTicks(outerRadius, stroke)
                drawUnavailableTicks(innerRadius, stroke)
            } else {
                val outerValue = outerPercentLeft?.coerceIn(0, 100)
                val innerValue = innerPercentLeft?.coerceIn(0, 100)

                if (outerValue == null) {
                    drawUnavailableTicks(outerRadius, stroke)
                } else {
                    val outerSweep = 360f * (outerValue / 100f)
                    val outerAccent = if (outerValue <= 25) GaugeWarning else accent
                    drawRingGlow(outerRadius, stroke, outerAccent, outerSweep)
                    drawRingArc(outerRadius, stroke, outerAccent, -90f, outerSweep)
                }

                if (innerValue == null) {
                    drawUnavailableTicks(innerRadius, stroke)
                } else {
                    val innerSweep = 360f * (innerValue / 100f)
                    val innerAccent = if (innerValue <= 25) GaugeWarning else WeeklyRingAccent
                    drawRingGlow(innerRadius, stroke, innerAccent, innerSweep)
                    drawRingArc(innerRadius, stroke, innerAccent, -90f, innerSweep)
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = centerPercentLeft?.coerceIn(0, 100)?.toString() ?: "-",
                    color = centerPercentLeft?.let { if (it <= 25) GaugeWarning else DashboardText } ?: DashboardMuted,
                    fontSize = valueFontSize,
                    lineHeight = valueFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    modifier = Modifier.padding(bottom = 5.dp, start = 2.dp),
                    text = if (centerPercentLeft == null) "" else "%",
                    color = DashboardText.copy(alpha = 0.90f),
                    fontSize = percentFontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (centerPercentLeft == null) "数据不可用" else "$windowLabel · 剩余",
                color = DashboardMuted,
                fontSize = labelFontSize,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRingTrack(
    radius: Float,
    stroke: Float,
) {
    drawCircle(
        color = Color(0xFF263140),
        radius = radius,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRingGlow(
    radius: Float,
    stroke: Float,
    color: Color,
    sweep: Float,
) {
    drawRingArc(
        radius = radius,
        stroke = stroke * 1.82f,
        color = color.copy(alpha = 0.15f),
        startAngle = -90f,
        sweepAngle = sweep,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRingArc(
    radius: Float,
    stroke: Float,
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
) {
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle.coerceIn(0f, 360f),
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUnavailableTicks(
    radius: Float,
    stroke: Float,
) {
    val tickCount = 56
    val tickLength = stroke * 0.74f
    repeat(tickCount) { index ->
        val angle = (index * 360f / tickCount - 90f) * PI.toFloat() / 180f
        val startRadius = radius - tickLength
        val endRadius = radius + tickLength
        drawLine(
            color = Color(0xFF344051),
            start = Offset(
                center.x + cos(angle) * startRadius,
                center.y + sin(angle) * startRadius,
            ),
            end = Offset(
                center.x + cos(angle) * endRadius,
                center.y + sin(angle) * endRadius,
            ),
            strokeWidth = stroke * 0.28f,
            cap = StrokeCap.Round,
        )
    }
}
