package com.codegauge.ui.settings

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codegauge.pairing.PairingRecord
import com.codegauge.settings.AppSettings
import com.codegauge.settings.CompanionDiagnostics
import com.codegauge.settings.PairedDevice
import com.codegauge.settings.SettingsRepository
import com.codegauge.settings.SettingsSnapshot
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
            errorMessage = exception.message ?: "Could not load settings."
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
            errorMessage = exception.message ?: "Could not save settings."
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
    val canSave = currentDraft != null &&
        currentDraft.warningThreshold < currentDraft.criticalThreshold &&
        intervalSeconds != null &&
        intervalSeconds > 0 &&
        !isSaving &&
        currentDraft != savedSettings

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsHeader()

        errorMessage?.let {
            ErrorMessage(it)
        }

        if (isLoading && snapshot == null) {
            LoadingPanel()
            return@Column
        }

        SettingsActionsPanel(
            pairing = pairing,
            hasChanges = currentDraft != null && currentDraft != savedSettings,
            canSave = canSave,
            isSaving = isSaving,
            onRefresh = {
                scope.launch {
                    loadSettings()
                }
            },
            onSave = {
                val settings = currentDraft ?: return@SettingsActionsPanel
                val parsedInterval = intervalSeconds ?: return@SettingsActionsPanel
                scope.launch {
                    saveSettings(settings.copy(collectIntervalSeconds = parsedInterval))
                }
            },
            onClearPairing = onClearPairing,
        )

        currentDraft?.let { settings ->
            NotificationPanel(
                settings = settings,
                onChange = {
                    draft = it
                },
            )
            ThresholdPanel(
                settings = settings,
                intervalText = intervalText,
                onSettingsChange = {
                    draft = it
                },
                onIntervalTextChange = { value ->
                    intervalText = value.filter(Char::isDigit).take(5)
                    intervalText.toIntOrNull()?.let { seconds ->
                        draft = settings.copy(collectIntervalSeconds = seconds)
                    }
                },
            )
        }

        snapshot?.diagnostics?.let {
            DiagnosticsPanel(it)
        }
        DevicesPanel(snapshot?.devices.orEmpty())
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Alerts, companion health, and paired devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsActionsPanel(
    pairing: PairingRecord,
    hasChanges: Boolean,
    canSave: Boolean,
    isSaving: Boolean,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onClearPairing: () -> Unit,
) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pairing.serverName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = pairing.serverUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (hasChanges) "Unsaved changes" else "Settings are up to date",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasChanges) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = canSave,
                onClick = onSave,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save")
                }
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onRefresh,
            ) {
                Text("Refresh")
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClearPairing,
        ) {
            Text("Pair again")
        }
    }
}

@Composable
private fun NotificationPanel(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    Panel {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        SettingSwitchRow(
            title = "Enable notifications",
            detail = "Master switch for CodeGauge alerts.",
            checked = settings.notificationsEnabled,
            onCheckedChange = {
                onChange(settings.copy(notificationsEnabled = it))
            },
        )
        SettingSwitchRow(
            title = "Quota recovery",
            detail = "Notify when usage drops below the warning threshold.",
            checked = settings.quotaResetNotifications,
            enabled = settings.notificationsEnabled,
            onCheckedChange = {
                onChange(settings.copy(quotaResetNotifications = it))
            },
        )
        SettingSwitchRow(
            title = "Task done",
            detail = "Notify when Claude reports a completed task.",
            checked = settings.taskDoneNotifications,
            enabled = settings.notificationsEnabled,
            onCheckedChange = {
                onChange(settings.copy(taskDoneNotifications = it))
            },
        )
    }
}

@Composable
private fun ThresholdPanel(
    settings: AppSettings,
    intervalText: String,
    onSettingsChange: (AppSettings) -> Unit,
    onIntervalTextChange: (String) -> Unit,
) {
    Panel {
        Text(
            text = "Usage thresholds",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        ThresholdSlider(
            title = "Warning",
            value = settings.warningThreshold,
            range = 0f..99f,
            onValueChange = { value ->
                val next = value.roundToInt().coerceIn(0, settings.criticalThreshold - 1)
                onSettingsChange(settings.copy(warningThreshold = next))
            },
        )
        ThresholdSlider(
            title = "Critical",
            value = settings.criticalThreshold,
            range = 1f..100f,
            onValueChange = { value ->
                val next = value.roundToInt().coerceIn(settings.warningThreshold + 1, 100)
                onSettingsChange(settings.copy(criticalThreshold = next))
            },
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = intervalText,
            onValueChange = onIntervalTextChange,
            label = { Text("Collect interval seconds") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = intervalText.toIntOrNull()?.let { it <= 0 } ?: true,
        )
    }
}

@Composable
private fun DiagnosticsPanel(diagnostics: CompanionDiagnostics) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Companion",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${diagnostics.serverName} · ${diagnostics.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(if (diagnostics.ok) "OK" else "Issue")
        }
        MetricRow("Providers", "${diagnostics.availableProviderCount}/${diagnostics.providerCount} available")
        MetricRow("Sessions", "${diagnostics.runningSessionCount} running · ${diagnostics.waitingSessionCount} waiting")
        MetricRow("Devices", "${diagnostics.pairedDeviceCount} paired")
        MetricRow("Server time", formatInstant(diagnostics.serverTime))
        MetricRow("Latest event", formatInstant(diagnostics.latestEventAt))
    }
}

@Composable
private fun DevicesPanel(devices: List<PairedDevice>) {
    Panel {
        Text(
            text = "Paired devices",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        if (devices.isEmpty()) {
            Text(
                text = "No paired devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            devices.forEach { device ->
                DeviceRow(device)
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ThresholdSlider(
    title: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DeviceRow(device: PairedDevice) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = device.name.ifBlank { "Unknown device" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Last seen ${formatInstant(device.lastSeenAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private fun formatInstant(value: Instant?): String {
    if (value == null) {
        return "Unknown"
    }
    return TimeFormatter.format(value)
}

private val TimeFormatter = DateTimeFormatter
    .ofPattern("MMM d, HH:mm")
    .withZone(ZoneId.systemDefault())

private const val Tag = "CodeGaugeSettings"
