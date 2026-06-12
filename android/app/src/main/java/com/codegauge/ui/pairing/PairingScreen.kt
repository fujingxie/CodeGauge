package com.codegauge.ui.pairing

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codegauge.activity.ActivityRepository
import com.codegauge.activity.ActivityStreamClient
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.listener.CodeGaugeListenerService
import com.codegauge.pairing.CompanionDiscovery
import com.codegauge.pairing.CompanionEndpoint
import com.codegauge.pairing.PairingRecord
import com.codegauge.pairing.PairingRepository
import com.codegauge.pairing.parseManualEndpoint
import com.codegauge.settings.SettingsRepository
import com.codegauge.ui.design.DashboardBackground
import com.codegauge.ui.design.DashboardBorder
import com.codegauge.ui.design.DashboardMuted
import com.codegauge.ui.design.DashboardSurface
import com.codegauge.ui.design.DashboardSurfaceRaised
import com.codegauge.ui.design.DashboardText
import com.codegauge.ui.main.MainTabsRoute
import com.codegauge.ui.design.CodexAccent
import com.codegauge.ui.design.ClaudeAccent
import com.codegauge.widget.CodeGaugeWidgetScheduler
import com.codegauge.widget.CodeGaugeWidgetUpdater
import kotlinx.coroutines.launch

@Composable
fun PairingRoute(
    repository: PairingRepository,
    dashboardRepository: DashboardRepository,
    activityRepository: ActivityRepository,
    settingsRepository: SettingsRepository,
    streamClient: ActivityStreamClient,
    discovery: CompanionDiscovery,
    deviceName: String,
) {
    var savedPairing by remember { mutableStateOf<PairingRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var discoveredEndpoints by remember { mutableStateOf(emptyList<CompanionEndpoint>()) }
    var selectedEndpoint by remember { mutableStateOf<CompanionEndpoint?>(null) }
    var manualAddress by remember { mutableStateOf("") }
    var isManualInputVisible by remember { mutableStateOf(false) }
    var isCodePanelVisible by remember { mutableStateOf(false) }
    var pairCode by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        savedPairing = repository.loadPairing()
        isLoading = false
    }

    val shouldDiscover = savedPairing == null
    DisposableEffect(discovery, shouldDiscover) {
        if (shouldDiscover) {
            discovery.start { endpoints ->
                discoveredEndpoints = endpoints
                if (selectedEndpoint == null && endpoints.isNotEmpty()) {
                    selectedEndpoint = endpoints.first()
                }
            }
        }
        onDispose {
            discovery.stop()
        }
    }

    LaunchedEffect(isLoading, savedPairing?.token) {
        if (isLoading) {
            return@LaunchedEffect
        }
        if (savedPairing == null) {
            CodeGaugeListenerService.stop(context)
            CodeGaugeWidgetScheduler.cancel(context)
            CodeGaugeWidgetUpdater.refresh(context)
        } else {
            CodeGaugeWidgetScheduler.schedule(context)
            CodeGaugeWidgetUpdater.refresh(context)
            CodeGaugeListenerService.start(context)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val currentPairing = savedPairing
        when {
            isLoading -> PairingPageContainer {
                Header()
                LoadingPanel()
            }
            currentPairing != null -> MainTabsRoute(
                pairing = currentPairing,
                dashboardRepository = dashboardRepository,
                activityRepository = activityRepository,
                settingsRepository = settingsRepository,
                streamClient = streamClient,
                onClearPairing = {
                    scope.launch {
                        repository.clearPairing()
                        savedPairing = null
                        selectedEndpoint = null
                        pairCode = ""
                        manualAddress = ""
                        isCodePanelVisible = false
                        errorMessage = null
                    }
                },
            )
            else -> PairingPageContainer {
                Header()
                PairingPanel(
                    endpoints = discoveredEndpoints,
                    selectedEndpoint = selectedEndpoint,
                    manualAddress = manualAddress,
                    manualInputVisible = isManualInputVisible || discoveredEndpoints.isEmpty(),
                    codePanelVisible = isCodePanelVisible,
                    pairCode = pairCode,
                    isPairing = isPairing,
                    errorMessage = errorMessage,
                    onEndpointSelected = {
                        selectedEndpoint = it
                        manualAddress = ""
                        isManualInputVisible = false
                        isCodePanelVisible = true
                        errorMessage = null
                    },
                    onManualAddressChanged = {
                        manualAddress = it
                        errorMessage = null
                    },
                    onManualInputToggle = {
                        isManualInputVisible = !isManualInputVisible
                        isCodePanelVisible = false
                        errorMessage = null
                    },
                    onManualConnect = {
                        val endpoint = parseManualEndpoint(manualAddress)
                        if (endpoint == null) {
                            errorMessage = "手动地址格式应类似 192.168.1.20:18770。"
                            isCodePanelVisible = false
                            return@PairingPanel
                        }
                        selectedEndpoint = endpoint
                        isCodePanelVisible = true
                        errorMessage = null
                    },
                    onCodePanelDismiss = {
                        isCodePanelVisible = false
                    },
                    onPairCodeChanged = {
                        pairCode = it.filter(Char::isDigit).take(6)
                        errorMessage = null
                    },
                    onPair = {
                        val endpoint = if (manualAddress.isBlank()) {
                            selectedEndpoint
                        } else {
                            parseManualEndpoint(manualAddress)
                        }

                        if (endpoint == null) {
                            errorMessage = if (manualAddress.isBlank()) {
                                "请选择一个 Companion，或手动输入 IP:Port。"
                            } else {
                                "手动地址格式应类似 192.168.1.20:18770。"
                            }
                            return@PairingPanel
                        }

                        scope.launch {
                            isPairing = true
                            errorMessage = null
                            try {
                                savedPairing = repository.pair(
                                    endpoint = endpoint,
                                    pairCode = pairCode,
                                    deviceName = deviceName,
                                )
                                pairCode = ""
                                manualAddress = ""
                                isCodePanelVisible = false
                            } catch (exception: Exception) {
                                Log.e(Tag, "Pairing failed", exception)
                                errorMessage = exception.message ?: "配对失败。"
                            } finally {
                                isPairing = false
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PairingPageContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
    )
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = DashboardSurface,
                border = BorderStroke(1.dp, DashboardBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "‹",
                        color = DashboardText,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
            Box(modifier = Modifier.width(18.dp))
            Text(
                text = "连接你的电脑",
                style = MaterialTheme.typography.headlineLarge,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "确保手机和电脑在同一 Wi-Fi，并已启动 CodeGauge Companion。",
            style = MaterialTheme.typography.titleMedium,
            color = DashboardMuted,
            lineHeight = 25.sp,
        )
    }
}

@Composable
private fun LoadingPanel() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SearchStatusRow()
        SkeletonEndpointCard()
        SkeletonEndpointCard()
    }
}

@Composable
private fun PairingPanel(
    endpoints: List<CompanionEndpoint>,
    selectedEndpoint: CompanionEndpoint?,
    manualAddress: String,
    manualInputVisible: Boolean,
    codePanelVisible: Boolean,
    pairCode: String,
    isPairing: Boolean,
    errorMessage: String?,
    onEndpointSelected: (CompanionEndpoint) -> Unit,
    onManualAddressChanged: (String) -> Unit,
    onManualInputToggle: () -> Unit,
    onManualConnect: () -> Unit,
    onCodePanelDismiss: () -> Unit,
    onPairCodeChanged: (String) -> Unit,
    onPair: () -> Unit,
) {
    val hasManualAddress = manualAddress.isNotBlank()
    val targetEndpoint = if (hasManualAddress) {
        parseManualEndpoint(manualAddress)
    } else {
        selectedEndpoint
    }
    val canPair = !isPairing && pairCode.length == 6 &&
        targetEndpoint != null

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (endpoints.isEmpty()) {
            EmptyDiscoveryPanel()
        } else {
            SectionTitle("发现的设备 · ${endpoints.size}")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                endpoints.forEach { endpoint ->
                    EndpointButton(
                        endpoint = endpoint,
                        selected = !hasManualAddress && endpoint == selectedEndpoint,
                        onClick = { onEndpointSelected(endpoint) },
                    )
                }
            }
        }

        ManualToggle(
            expanded = manualInputVisible,
            onClick = onManualInputToggle,
        )

        if (manualInputVisible) {
            ManualInputPanel(
                manualAddress = manualAddress,
                onManualAddressChanged = onManualAddressChanged,
                onManualConnect = onManualConnect,
            )
        }

        errorMessage?.let {
            ErrorMessage(it)
        }

        if (codePanelVisible) {
            PairCodePanel(
                targetEndpoint = targetEndpoint,
                pairCode = pairCode,
                canPair = canPair,
                isPairing = isPairing,
                onPairCodeChanged = onPairCodeChanged,
                onPair = onPair,
                onDismiss = onCodePanelDismiss,
            )
        }
    }
}

@Composable
private fun EndpointButton(
    endpoint: CompanionEndpoint,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (selected) CodexAccent else DashboardBorder,
                shape = RoundedCornerShape(12.dp),
            ),
        shape = RoundedCornerShape(12.dp),
        color = DashboardSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComputerBadge(selected = selected)
                Box(modifier = Modifier.width(14.dp))
                EndpointText(endpoint)
            }
            if (selected) {
                Text(
                    text = "✓",
                    color = CodexAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
            } else {
                Text(
                    text = "›",
                    color = DashboardMuted,
                    fontSize = 30.sp,
                )
            }
        }
    }
}

@Composable
private fun EndpointText(endpoint: CompanionEndpoint) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = endpoint.name,
            style = MaterialTheme.typography.titleLarge,
            color = DashboardText,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = "${endpoint.host}:${endpoint.port}",
            style = MaterialTheme.typography.titleMedium,
            color = DashboardMuted,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchStatusRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = DashboardMuted,
            strokeWidth = 2.dp,
        )
        Box(modifier = Modifier.width(12.dp))
        Text(
            text = "正在搜索局域网...",
            style = MaterialTheme.typography.titleMedium,
            color = DashboardMuted,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = DashboardMuted,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun EmptyDiscoveryPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(112.dp),
            shape = RoundedCornerShape(20.dp),
            color = DashboardSurfaceRaised,
            border = BorderStroke(1.dp, DashboardBorder),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "▭",
                    color = DashboardMuted,
                    fontSize = 42.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Box(modifier = Modifier.height(22.dp))
        Text(
            text = "没有发现设备",
            style = MaterialTheme.typography.headlineSmall,
            color = DashboardText,
            fontWeight = FontWeight.Bold,
        )
        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = "确认 Companion 已在电脑上运行，或手动输入地址。",
            style = MaterialTheme.typography.titleMedium,
            color = DashboardMuted,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun SkeletonEndpointCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DashboardSurface,
        border = BorderStroke(1.dp, DashboardBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(DashboardSurfaceRaised, RoundedCornerShape(12.dp)),
            )
            Box(modifier = Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(16.dp)
                        .background(DashboardSurfaceRaised, RoundedCornerShape(99.dp)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.44f)
                        .height(14.dp)
                        .background(DashboardSurfaceRaised, RoundedCornerShape(99.dp)),
                )
            }
        }
    }
}

@Composable
private fun ComputerBadge(selected: Boolean) {
    Surface(
        modifier = Modifier.size(58.dp),
        shape = RoundedCornerShape(14.dp),
        color = DashboardSurfaceRaised,
        border = BorderStroke(1.dp, if (selected) CodexAccent.copy(alpha = 0.38f) else DashboardBorder),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "▭",
                color = if (selected) DashboardText else DashboardMuted,
                fontSize = 31.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ManualToggle(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "手动输入 IP:Port",
            style = MaterialTheme.typography.titleMedium,
            color = DashboardMuted,
            fontWeight = FontWeight.Bold,
        )
        Box(modifier = Modifier.width(8.dp))
        Text(
            text = if (expanded) "⌃" else "⌄",
            style = MaterialTheme.typography.titleLarge,
            color = DashboardMuted,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ManualInputPanel(
    manualAddress: String,
    onManualAddressChanged: (String) -> Unit,
    onManualConnect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "手动输入 IP:PORT",
            style = MaterialTheme.typography.labelLarge,
            color = DashboardMuted,
            fontWeight = FontWeight.Bold,
        )
        DarkTextField(
            value = manualAddress,
            onValueChange = onManualAddressChanged,
            placeholder = "192.168.1.24:18770",
            keyboardType = KeyboardType.Uri,
        )
        PrimaryActionButton(
            text = "连接",
            enabled = manualAddress.isNotBlank(),
            loading = false,
            onClick = onManualConnect,
        )
    }
}

@Composable
private fun PairCodePanel(
    targetEndpoint: CompanionEndpoint?,
    pairCode: String,
    canPair: Boolean,
    isPairing: Boolean,
    onPairCodeChanged: (String) -> Unit,
    onPair: () -> Unit,
    onDismiss: () -> Unit,
) {
    Panel(
        modifier = Modifier.border(
            width = 1.dp,
            color = DashboardBorder,
            shape = RoundedCornerShape(22.dp),
        ),
        contentPadding = 18.dp,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 48.dp, height = 5.dp)
                .background(DashboardBorder, RoundedCornerShape(99.dp)),
        )
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = "输入配对码",
            style = MaterialTheme.typography.headlineSmall,
            color = DashboardText,
            fontWeight = FontWeight.Bold,
        )
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = "在电脑托盘的 CodeGauge 菜单查看 6 位配对码。",
            style = MaterialTheme.typography.titleMedium,
            color = DashboardMuted,
            lineHeight = 23.sp,
        )
        targetEndpoint?.let {
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = "${it.name} · ${it.host}:${it.port}",
                style = MaterialTheme.typography.bodyMedium,
                color = CodexAccent,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        PairCodeInput(
            value = pairCode,
            onValueChange = onPairCodeChanged,
        )
        PrimaryActionButton(
            text = "配对",
            enabled = canPair,
            loading = isPairing,
            onClick = onPair,
        )
        Text(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onDismiss)
                .padding(vertical = 6.dp),
            text = "稍后再说",
            style = MaterialTheme.typography.titleSmall,
            color = DashboardMuted,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PairCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    repeat(6) { index ->
                        CodeDigitBox(
                            digit = value.getOrNull(index)?.toString().orEmpty(),
                            active = index == value.length.coerceAtMost(5),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.01f),
                ) {
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun CodeDigitBox(
    digit: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(10.dp),
        color = DashboardSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) CodexAccent else DashboardBorder,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium,
                color = DashboardText,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = DashboardMuted.copy(alpha = 0.72f),
                fontFamily = FontFamily.Monospace,
            )
        },
        textStyle = TextStyle(
            color = DashboardText,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DashboardText,
            unfocusedTextColor = DashboardText,
            focusedContainerColor = DashboardSurfaceRaised,
            unfocusedContainerColor = DashboardSurfaceRaised,
            focusedBorderColor = CodexAccent,
            unfocusedBorderColor = DashboardBorder,
            cursorColor = CodexAccent,
        ),
    )
}

@Composable
private fun PrimaryActionButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        enabled = enabled && !loading,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DashboardText,
            contentColor = DashboardBackground,
            disabledContainerColor = DashboardText.copy(alpha = 0.26f),
            disabledContentColor = DashboardBackground.copy(alpha = 0.62f),
        ),
    ) {
        if (loading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = DashboardBackground,
                )
                Text("正在配对")
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF321820),
        border = BorderStroke(1.dp, ClaudeAccent.copy(alpha = 0.38f)),
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = message,
            color = Color(0xFFFF8791),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DashboardSurfaceRaised,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

private const val Tag = "CodeGaugePairing"
