package com.codegauge.ui.settings

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codegauge.pairing.PairingRecord
import com.codegauge.settings.AppSettings
import com.codegauge.settings.CompanionDiagnostics
import com.codegauge.settings.PairedDevice
import com.codegauge.settings.SettingsRepository
import com.codegauge.settings.SettingsSnapshot
import com.codegauge.ui.design.DashboardBackground
import com.codegauge.ui.design.DashboardBorder
import com.codegauge.ui.design.DashboardMuted
import com.codegauge.ui.design.DashboardSurface
import com.codegauge.ui.design.DashboardSurfaceRaised
import com.codegauge.ui.design.DashboardText
import com.codegauge.ui.design.DesignDot
import com.codegauge.ui.design.DesignPanel
import com.codegauge.ui.design.DesignPill
import com.codegauge.ui.design.GoodGreen
import com.codegauge.ui.design.WarningAmber
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    pairing: PairingRecord,
    repository: SettingsRepository,
    modifier: Modifier = Modifier,
    onClearPairing: () -> Unit,
) {
    var snapshot by remember(pairing.serverUrl, pairing.token) {
        mutableStateOf<SettingsSnapshot?>(null)
    }
    var draft by remember(pairing.serverUrl, pairing.token) {
        mutableStateOf<AppSettings?>(null)
    }
    var intervalText by remember(pairing.serverUrl, pairing.token) {
        mutableStateOf("")
    }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadSettings() {
        isLoading = true
        try {
            val loaded = repository.load(pairing)
            snapshot = loaded
            draft = loaded.settings
            intervalText = loaded.settings.collectIntervalSeconds.toString()
            errorMessage = null
        } catch (exception: Exception) {
            Log.e(Tag, "Load settings failed", exception)
            errorMessage = exception.message ?: "设置加载失败"
        } finally {
            isLoading = false
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        isSaving = true
        try {
            val updated = repository.save(pairing, settings)
            val loaded = repository.load(pairing)
            snapshot = loaded.copy(settings = updated)
            draft = updated
            intervalText = updated.collectIntervalSeconds.toString()
            errorMessage = null
        } catch (exception: Exception) {
            Log.e(Tag, "Save settings failed", exception)
            errorMessage = exception.message ?: "设置保存失败"
        } finally {
            isSaving = false
        }
    }

    LaunchedEffect(pairing.serverUrl, pairing.token) {
        loadSettings()
    }

    val currentDraft = draft
    val savedSettings = snapshot?.settings
    val intervalSeconds = intervalText.toIntOrNull()
    val hasChanges = currentDraft != null && currentDraft != savedSettings
    val canSave = currentDraft != null &&
        currentDraft.warningThreshold < currentDraft.criticalThreshold &&
        intervalSeconds != null &&
        intervalSeconds > 0 &&
        !isSaving &&
        hasChanges

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsStatusStrip(pairing)
        SettingsHeader()

        errorMessage?.let {
            ErrorMessage(it)
        }

        if (isLoading && snapshot == null) {
            LoadingPanel()
        } else {
            ConnectionPanel(
                pairing = pairing,
                hasChanges = hasChanges,
                canSave = canSave,
                isSaving = isSaving,
                diagnostics = snapshot?.diagnostics,
                onRefresh = {
                    scope.launch {
                        loadSettings()
                    }
                },
                onSave = {
                    val settings = currentDraft
                    val parsedInterval = intervalSeconds
                    if (settings != null && parsedInterval != null) {
                        scope.launch {
                            saveSettings(settings.copy(collectIntervalSeconds = parsedInterval))
                        }
                    }
                },
                onClearPairing = onClearPairing,
            )

            currentDraft?.let { settings ->
                NotificationSection(
                    settings = settings,
                    onChange = {
                        draft = it
                    },
                )
                DashboardSection(
                    settings = settings,
                    onChange = {
                        draft = it
                    },
                )
                CollectionSection(
                    intervalSeconds = intervalSeconds ?: settings.collectIntervalSeconds,
                    onIntervalChange = { seconds ->
                        intervalText = seconds.toString()
                        draft = settings.copy(collectIntervalSeconds = seconds)
                    },
                )
            }

            DiagnosticsSection(snapshot?.diagnostics)
            DevicesSection(snapshot?.devices.orEmpty())
        }
    }
}

@Composable
private fun SettingsStatusStrip(pairing: PairingRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesignDot(color = GoodGreen, glow = true)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${pairing.serverName} · 已连接",
                style = MaterialTheme.typography.titleSmall,
                color = DashboardText,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "设置",
            style = MaterialTheme.typography.bodySmall,
            color = DashboardMuted,
        )
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineLarge,
            color = DashboardText,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "通知、采集和连接诊断",
            style = MaterialTheme.typography.bodyMedium,
            color = DashboardMuted,
        )
    }
}

@Composable
private fun ConnectionPanel(
    pairing: PairingRecord,
    hasChanges: Boolean,
    canSave: Boolean,
    isSaving: Boolean,
    diagnostics: CompanionDiagnostics?,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onClearPairing: () -> Unit,
) {
    DesignPanel(
        contentPadding = 0.dp,
        contentSpacing = 0.dp,
    ) {
        SettingInfoRow(
            title = pairing.serverName,
            detail = pairing.serverUrl,
            trailing = {
                DesignPill(
                    text = if (diagnostics?.ok != false) "已连接" else "异常",
                    accent = if (diagnostics?.ok != false) GoodGreen else Color(0xFFE34D5A),
                    filled = true,
                )
            },
        )
        Divider()
        SettingInfoRow(
            title = "最后心跳",
            detail = if (hasChanges) "有未保存的修改" else "设置已同步",
            trailingText = formatInstant(diagnostics?.serverTime),
        )
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionChip(
                modifier = Modifier.weight(1f),
                text = if (isSaving) "保存中" else "保存",
                enabled = canSave,
                highlighted = hasChanges,
                onClick = onSave,
            )
            ActionChip(
                modifier = Modifier.weight(1f),
                text = "刷新",
                onClick = onRefresh,
            )
            ActionChip(
                modifier = Modifier.weight(1f),
                text = "重配",
                onClick = onClearPairing,
            )
        }
    }
}

@Composable
private fun NotificationSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    SectionLabel("通知")
    DesignPanel(
        contentPadding = 0.dp,
        contentSpacing = 0.dp,
    ) {
        SwitchRow(
            title = "通知",
            checked = settings.notificationsEnabled,
            onCheckedChange = {
                onChange(settings.copy(notificationsEnabled = it))
            },
        )
        Divider()
        ThresholdControlRow(
            settings = settings,
            onChange = onChange,
        )
        Divider()
        SwitchRow(
            title = "满血提醒",
            checked = settings.quotaResetNotifications,
            enabled = settings.notificationsEnabled,
            onCheckedChange = {
                onChange(settings.copy(quotaResetNotifications = it))
            },
        )
        Divider()
        SwitchRow(
            title = "任务完成",
            checked = settings.taskDoneNotifications,
            enabled = settings.notificationsEnabled,
            onCheckedChange = {
                onChange(settings.copy(taskDoneNotifications = it))
            },
        )
        Divider()
        SwitchRow(
            title = "需要确认",
            detail = "等待输入时提醒",
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
    }
}

@Composable
private fun ThresholdControlRow(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "额度阈值提醒",
                style = MaterialTheme.typography.titleMedium,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "预警 ${settings.warningThreshold}% · 告急 ${settings.criticalThreshold}%",
                style = MaterialTheme.typography.bodyMedium,
                color = WarningAmber,
                fontWeight = FontWeight.SemiBold,
            )
        }
        RangeSlider(
            value = settings.warningThreshold.toFloat()..settings.criticalThreshold.toFloat(),
            valueRange = 0f..100f,
            onValueChange = { range ->
                val warning = range.start.roundToInt().coerceIn(0, 99)
                val critical = range.endInclusive.roundToInt().coerceIn(warning + 1, 100)
                onChange(
                    settings.copy(
                        warningThreshold = warning,
                        criticalThreshold = critical,
                    ),
                )
            },
        )
    }
}

@Composable
private fun DashboardSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    SectionLabel("仪表盘")
    DesignPanel(
        contentPadding = 14.dp,
        contentSpacing = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "首页主额度",
                    style = MaterialTheme.typography.titleMedium,
                    color = DashboardText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = "额度卡中心优先展示的窗口",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashboardMuted,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            SegmentedPrimaryWindow(
                selectedWindow = settings.dashboardPrimaryWindow,
                onChange = { window ->
                    onChange(settings.copy(dashboardPrimaryWindow = window))
                },
            )
        }
    }
}

@Composable
private fun SegmentedPrimaryWindow(
    selectedWindow: String,
    onChange: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DashboardSurfaceRaised,
        border = BorderStroke(1.dp, DashboardBorder),
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            PrimaryWindowOption.entries.forEach { option ->
                val selected = selectedWindow == option.value
                Surface(
                    modifier = Modifier.clickable { onChange(option.value) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) DashboardBackground else Color.Transparent,
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) DashboardText else DashboardMuted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionSection(
    intervalSeconds: Int,
    onIntervalChange: (Int) -> Unit,
) {
    SectionLabel("采集")
    DesignPanel(
        contentPadding = 14.dp,
        contentSpacing = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "采集间隔",
                style = MaterialTheme.typography.titleMedium,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
            )
            SegmentedInterval(
                currentSeconds = intervalSeconds,
                onChange = onIntervalChange,
            )
        }
    }
}

@Composable
private fun SegmentedInterval(
    currentSeconds: Int,
    onChange: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DashboardSurfaceRaised,
        border = BorderStroke(1.dp, DashboardBorder),
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            IntervalOption.entries.forEach { option ->
                val selected = currentSeconds == option.seconds
                Surface(
                    modifier = Modifier.clickable { onChange(option.seconds) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) DashboardBackground else Color.Transparent,
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) DashboardText else DashboardMuted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(diagnostics: CompanionDiagnostics?) {
    SectionLabel("诊断 / 关于")
    DesignPanel(
        contentPadding = 0.dp,
        contentSpacing = 0.dp,
    ) {
        SettingInfoRow(
            title = "诊断日志",
            trailingText = "›",
        )
        Divider()
        SettingInfoRow(
            title = "版本",
            trailingText = diagnostics?.version ?: "1.0.0",
        )
        Divider()
        SettingInfoRow(
            title = "服务",
            trailingText = diagnostics?.let { "${it.availableProviderCount}/${it.providerCount} 可用" } ?: "未知",
        )
        Divider()
        SettingInfoRow(
            title = "会话",
            trailingText = diagnostics?.let { "${it.runningSessionCount} 运行 · ${it.waitingSessionCount} 等待" } ?: "未知",
        )
    }
}

@Composable
private fun DevicesSection(devices: List<PairedDevice>) {
    if (devices.isEmpty()) {
        return
    }
    SectionLabel("已配对设备")
    DesignPanel(
        contentPadding = 0.dp,
        contentSpacing = 0.dp,
    ) {
        devices.forEachIndexed { index, device ->
            SettingInfoRow(
                title = device.name.ifBlank { "未知设备" },
                detail = "最近在线 ${formatInstant(device.lastSeenAt)}",
            )
            if (index != devices.lastIndex) {
                Divider()
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingInfoRow(
        title = title,
        detail = detail,
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DashboardText,
                    checkedTrackColor = Color(0xFFE97857),
                    uncheckedThumbColor = DashboardMuted,
                    uncheckedTrackColor = DashboardSurfaceRaised,
                ),
            )
        },
    )
}

@Composable
private fun SettingInfoRow(
    title: String,
    detail: String? = null,
    trailingText: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) DashboardText else DashboardMuted.copy(alpha = 0.48f),
                fontWeight = FontWeight.Bold,
            )
            detail?.let {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) DashboardMuted else DashboardMuted.copy(alpha = 0.42f),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        trailing?.invoke() ?: trailingText?.let {
            Text(
                modifier = Modifier.padding(start = 12.dp),
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = DashboardMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ActionChip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted && enabled) Color(0xFFE97857) else DashboardSurfaceRaised,
        border = BorderStroke(1.dp, if (enabled) DashboardBorder else DashboardBorder.copy(alpha = 0.42f)),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !enabled -> DashboardMuted.copy(alpha = 0.48f)
                    highlighted -> DashboardBackground
                    else -> DashboardText
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = DashboardMuted,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DashboardBorder),
    )
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = GoodGreen,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF321820),
        border = BorderStroke(1.dp, Color(0xFFE34D5A).copy(alpha = 0.32f)),
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = message,
            color = Color(0xFFFF8791),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatInstant(value: Instant?): String {
    if (value == null) {
        return "未知"
    }
    return TimeFormatter.format(value)
}

private enum class IntervalOption(
    val label: String,
    val seconds: Int,
) {
    Realtime("实时", 5),
    ThirtySeconds("30s", 30),
    OneMinute("1m", 60),
    FiveMinutes("5m", 300),
}

private enum class PrimaryWindowOption(
    val label: String,
    val value: String,
) {
    FiveHours("5H", "5h"),
    Weekly("周", "weekly"),
}

private val TimeFormatter = DateTimeFormatter
    .ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

private const val Tag = "CodeGaugeSettings"
