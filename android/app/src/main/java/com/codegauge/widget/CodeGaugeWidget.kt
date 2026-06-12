package com.codegauge.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetSurface)
            .cornerRadius(18.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "CodeGauge · ${state.statusText}",
            style = TextStyle(
                color = WidgetText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.height(10.dp))

        if (state.providers.isEmpty()) {
            Text(
                text = state.message ?: "暂无额度数据",
                style = TextStyle(
                    color = WidgetMuted,
                    fontSize = 13.sp,
                ),
            )
        } else {
            state.providers.take(2).forEach { provider ->
                ProviderLine(provider)
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
        }

        Text(
            text = footerText(state),
            style = TextStyle(
                color = WidgetMuted,
                fontSize = 11.sp,
            ),
        )
    }
}

@Composable
private fun ProviderLine(provider: WidgetProviderLine) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = "${provider.name} · ${provider.resetText}",
            style = TextStyle(
                color = WidgetText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = provider.fiveHourText,
            style = TextStyle(
                color = WidgetAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = provider.weeklyText,
            style = TextStyle(
                color = WidgetSecondAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private fun footerText(state: CodeGaugeWidgetState): String {
    val updatedAt = state.updatedAt ?: return "点击打开 App"
    val localTime = updatedAt.atZone(ZoneId.systemDefault()).format(FooterFormatter)
    return "更新 $localTime"
}

private val FooterFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val WidgetSurface = ColorProvider(Color(0xFF151A24))
private val WidgetText = ColorProvider(Color(0xFFE8ECF2))
private val WidgetMuted = ColorProvider(Color(0xFF9EA8B8))
private val WidgetAccent = ColorProvider(Color(0xFFFF7A4D))
private val WidgetSecondAccent = ColorProvider(Color(0xFFFFD66E))
