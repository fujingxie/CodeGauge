package com.codegauge.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.codegauge.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CodeGaugeWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(170.dp, 170.dp),
            DpSize(320.dp, 150.dp),
            DpSize(360.dp, 180.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = CodeGaugeWidgetStore(context).load()
        provideContent {
            WidgetContent(state)
        }
    }
}

class CodeGaugeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CodeGaugeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CodeGaugeWidgetScheduler.schedule(context)
        CoroutineScope(Dispatchers.IO).launch {
            CodeGaugeWidgetUpdater.refresh(context)
        }
    }

    override fun onDisabled(context: Context) {
        CodeGaugeWidgetScheduler.cancel(context)
        super.onDisabled(context)
    }
}

@Composable
private fun WidgetContent(state: CodeGaugeWidgetState) {
    val size = LocalSize.current
    val wide = size.width >= 260.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetGlow)
            .cornerRadius(if (wide) 24.dp else 22.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(if (wide) 8.dp else 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetSurface)
                .cornerRadius(if (wide) 18.dp else 20.dp)
                .padding(if (wide) 14.dp else 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            if (state.providers.isEmpty()) {
                EmptyWidgetCard(state, wide)
            } else if (wide) {
                WideWidgetContent(state)
            } else {
                CompactWidgetContent(state)
            }
        }
    }
}

@Composable
private fun WideWidgetContent(state: CodeGaugeWidgetState) {
    HeaderRow(state)
    Spacer(modifier = GlanceModifier.height(8.dp))
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val providers = state.providers.take(2)
        providers.forEachIndexed { index, provider ->
            ProviderGauge(
                provider = provider,
                compact = false,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (index != providers.lastIndex) {
                Spacer(modifier = GlanceModifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun CompactWidgetContent(state: CodeGaugeWidgetState) {
    val provider = state.providers.firstOrNull { it.percentLeft != null }
        ?: state.providers.first()
    ProviderGauge(
        provider = provider,
        compact = true,
        modifier = GlanceModifier.fillMaxSize(),
        footer = footerText(state),
    )
}

@Composable
private fun HeaderRow(state: CodeGaugeWidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderDot(WidgetAccent)
            Spacer(modifier = GlanceModifier.size(6.dp))
            Text(
                text = "CodeGauge",
                style = TextStyle(
                    color = WidgetText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Text(
            text = footerText(state),
            style = TextStyle(
                color = WidgetMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun ProviderGauge(
    provider: WidgetProviderLine,
    compact: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    footer: String? = null,
) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            ProviderDot(provider.accentColor())
            Spacer(modifier = GlanceModifier.size(7.dp))
            Text(
                text = provider.name,
                style = TextStyle(
                    color = WidgetText,
                    fontSize = if (compact) 18.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(if (compact) 9.dp else 7.dp))

        GaugeShell(
            provider = provider,
            compact = compact,
        )

        Spacer(modifier = GlanceModifier.height(if (compact) 6.dp else 4.dp))

        Text(
            text = provider.resetLine(),
            style = TextStyle(
                color = provider.detailColor(),
                fontSize = if (compact) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        footer?.let {
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = it,
                style = TextStyle(
                    color = WidgetMuted,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

@Composable
private fun GaugeShell(
    provider: WidgetProviderLine,
    compact: Boolean,
) {
    val progress = provider.percentLeft?.coerceIn(0, 100)?.div(100f) ?: 0f
    val shellSize = if (compact) 96.dp else 82.dp

    Column(
        modifier = GlanceModifier
            .size(shellSize)
            .background(WidgetGaugeShell)
            .cornerRadius(99.dp)
            .padding(if (compact) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = provider.percentText,
            style = TextStyle(
                color = provider.percentColor(),
                fontSize = if (compact) 32.sp else 27.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = if (provider.percentLeft == null) provider.emptyCaption() else "%",
            style = TextStyle(
                color = WidgetMuted,
                fontSize = if (provider.percentLeft == null) 11.sp else 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.height(7.dp))

        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(7.dp)
                .cornerRadius(99.dp),
            color = provider.accentColor(),
            backgroundColor = WidgetTrack,
        )
        Spacer(modifier = GlanceModifier.height(5.dp))
        LinearProgressIndicator(
            progress = (progress * 0.72f).coerceIn(0f, 1f),
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(5.dp)
                .cornerRadius(99.dp),
            color = provider.secondaryColor(),
            backgroundColor = WidgetTrack,
        )
    }
}

@Composable
private fun EmptyWidgetCard(
    state: CodeGaugeWidgetState,
    wide: Boolean,
) {
    if (wide) {
        HeaderRow(state)
        Spacer(modifier = GlanceModifier.height(12.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyGauge("Claude", GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.size(20.dp))
            EmptyGauge("Codex", GlanceModifier.defaultWeight())
        }
    } else {
        EmptyGauge(
            label = state.message ?: "未连接",
            modifier = GlanceModifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EmptyGauge(
    label: String,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            ProviderDot(WidgetMuted)
            Spacer(modifier = GlanceModifier.size(7.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = WidgetText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(modifier = GlanceModifier.height(11.dp))
        Column(
            modifier = GlanceModifier
                .size(82.dp)
                .background(WidgetGaugeShell)
                .cornerRadius(99.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "-",
                style = TextStyle(
                    color = WidgetMuted,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "未连接",
                style = TextStyle(
                    color = WidgetMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun ProviderDot(color: ColorProvider) {
    Spacer(
        modifier = GlanceModifier
            .size(9.dp)
            .background(color)
            .cornerRadius(99.dp),
    )
}

private fun WidgetProviderLine.resetLine(): String {
    return when {
        percentLeft == null -> usageText
        windowLabel.isNotBlank() -> "$windowLabel  $resetText"
        else -> resetText
    }
}

private fun WidgetProviderLine.emptyCaption(): String {
    return if (usageText == "数据不可用") "不可用" else "%"
}

private fun WidgetProviderLine.accentColor(): ColorProvider {
    return when {
        percentLeft == null -> WidgetMuted
        percentLeft <= 8 -> WidgetDanger
        id.equals("codex", ignoreCase = true) -> WidgetCodex
        else -> WidgetAccent
    }
}

private fun WidgetProviderLine.secondaryColor(): ColorProvider {
    return when {
        percentLeft == null -> WidgetTrack
        percentLeft <= 25 -> WidgetWarning
        id.equals("codex", ignoreCase = true) -> WidgetCodexDim
        else -> WidgetAccentDim
    }
}

private fun WidgetProviderLine.percentColor(): ColorProvider {
    return when {
        percentLeft == null -> WidgetMuted
        percentLeft <= 8 -> WidgetDanger
        percentLeft <= 25 -> WidgetWarning
        id.equals("codex", ignoreCase = true) -> WidgetCodex
        else -> WidgetText
    }
}

private fun WidgetProviderLine.detailColor(): ColorProvider {
    return when {
        percentLeft == null -> WidgetMuted
        percentLeft <= 25 -> WidgetWarning
        else -> WidgetMuted
    }
}

private fun footerText(state: CodeGaugeWidgetState): String {
    if (state.providers.isEmpty()) {
        return state.statusText
    }
    val updatedAt = state.updatedAt ?: return "点击打开"
    val localTime = updatedAt.atZone(ZoneId.systemDefault()).format(FooterFormatter)
    return "上次更新 $localTime"
}

private val FooterFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val WidgetGlow = ColorProvider(Color(0xFF070A0F))
private val WidgetSurface = ColorProvider(Color(0xFF121821))
private val WidgetGaugeShell = ColorProvider(Color(0xFF0C1118))
private val WidgetTrack = ColorProvider(Color(0xFF283343))
private val WidgetText = ColorProvider(Color(0xFFE9EEF7))
private val WidgetMuted = ColorProvider(Color(0xFF8A93A3))
private val WidgetAccent = ColorProvider(Color(0xFFE97857))
private val WidgetAccentDim = ColorProvider(Color(0xFFC75E45))
private val WidgetCodex = ColorProvider(Color(0xFF17C398))
private val WidgetCodexDim = ColorProvider(Color(0xFF109E7C))
private val WidgetWarning = ColorProvider(Color(0xFFE8C24A))
private val WidgetDanger = ColorProvider(Color(0xFFFF5B66))
