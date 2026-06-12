package com.codegauge.ui.pairing

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codegauge.activity.ActivityRepository
import com.codegauge.activity.ActivityStreamClient
import com.codegauge.dashboard.DashboardRepository
import com.codegauge.listener.CodeGaugeListenerService
import com.codegauge.pairing.CompanionDiscovery
import com.codegauge.pairing.CompanionEndpoint
import com.codegauge.pairing.PairingRecord
import com.codegauge.pairing.PairingRepository
import com.codegauge.pairing.parseManualEndpoint
import com.codegauge.ui.main.MainTabsRoute
import kotlinx.coroutines.launch

@Composable
fun PairingRoute(
    repository: PairingRepository,
    dashboardRepository: DashboardRepository,
    activityRepository: ActivityRepository,
    streamClient: ActivityStreamClient,
    discovery: CompanionDiscovery,
    deviceName: String,
) {
    var savedPairing by remember { mutableStateOf<PairingRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var discoveredEndpoints by remember { mutableStateOf(emptyList<CompanionEndpoint>()) }
    var selectedEndpoint by remember { mutableStateOf<CompanionEndpoint?>(null) }
    var manualAddress by remember { mutableStateOf("") }
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
        } else {
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
                streamClient = streamClient,
                onClearPairing = {
                    scope.launch {
                        repository.clearPairing()
                        savedPairing = null
                        pairCode = ""
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
                    pairCode = pairCode,
                    isPairing = isPairing,
                    errorMessage = errorMessage,
                    onEndpointSelected = {
                        selectedEndpoint = it
                        manualAddress = ""
                        errorMessage = null
                    },
                    onManualAddressChanged = {
                        manualAddress = it
                        errorMessage = null
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
                                "Select a Companion or enter IP:Port."
                            } else {
                                "Manual address must look like 192.168.1.20:18770."
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
                            } catch (exception: Exception) {
                                Log.e(Tag, "Pairing failed", exception)
                                errorMessage = exception.message ?: "Pairing failed."
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
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "CodeGauge",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Local AI usage dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PairingPanel(
    endpoints: List<CompanionEndpoint>,
    selectedEndpoint: CompanionEndpoint?,
    manualAddress: String,
    pairCode: String,
    isPairing: Boolean,
    errorMessage: String?,
    onEndpointSelected: (CompanionEndpoint) -> Unit,
    onManualAddressChanged: (String) -> Unit,
    onPairCodeChanged: (String) -> Unit,
    onPair: () -> Unit,
) {
    val hasManualAddress = manualAddress.isNotBlank()
    val canPair = !isPairing && pairCode.length == 6 &&
        (selectedEndpoint != null || hasManualAddress)

    Panel {
        Text(
            text = "Companion",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )

        if (endpoints.isEmpty()) {
            Text(
                text = "Searching local network...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                endpoints.forEach { endpoint ->
                    EndpointButton(
                        endpoint = endpoint,
                        selected = !hasManualAddress && endpoint == selectedEndpoint,
                        onClick = { onEndpointSelected(endpoint) },
                    )
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = manualAddress,
            onValueChange = onManualAddressChanged,
            label = { Text("Manual IP:Port") },
            placeholder = { Text("192.168.1.20:18770") },
            singleLine = true,
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = pairCode,
            onValueChange = onPairCodeChanged,
            label = { Text("Pair code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
        )

        errorMessage?.let {
            ErrorMessage(it)
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = canPair,
            onClick = onPair,
        ) {
            if (isPairing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text("Pairing")
                }
            } else {
                Text("Pair")
            }
        }
    }
}

@Composable
private fun EndpointButton(
    endpoint: CompanionEndpoint,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            EndpointText(endpoint)
        }
    } else {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
        ) {
            EndpointText(endpoint)
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${endpoint.host}:${endpoint.port}",
            style = MaterialTheme.typography.bodySmall,
        )
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

private const val Tag = "CodeGaugePairing"
