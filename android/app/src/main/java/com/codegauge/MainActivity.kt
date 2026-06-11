package com.codegauge

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.codegauge.pairing.CompanionDiscovery
import com.codegauge.pairing.CompanionEndpoint
import com.codegauge.pairing.EncryptedPairingStore
import com.codegauge.pairing.InMemoryPairingStore
import com.codegauge.pairing.NoopCompanionDiscovery
import com.codegauge.pairing.NsdCompanionDiscovery
import com.codegauge.pairing.OkHttpPairingApi
import com.codegauge.pairing.PairRequest
import com.codegauge.pairing.PairResponse
import com.codegauge.pairing.PairingApi
import com.codegauge.pairing.PairingRepository
import com.codegauge.ui.pairing.PairingRoute
import com.codegauge.ui.theme.CodeGaugeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        setContent {
            val repository = remember {
                PairingRepository(
                    api = OkHttpPairingApi(),
                    store = EncryptedPairingStore(appContext),
                )
            }
            val discovery = remember {
                NsdCompanionDiscovery(appContext)
            }
            CodeGaugeTheme {
                CodeGaugeApp(
                    repository = repository,
                    discovery = discovery,
                    deviceName = defaultDeviceName(),
                )
            }
        }
    }

    private fun defaultDeviceName(): String {
        return listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android" }
    }
}

@Composable
fun CodeGaugeApp(
    repository: PairingRepository,
    discovery: CompanionDiscovery,
    deviceName: String,
) {
    PairingRoute(
        repository = repository,
        discovery = discovery,
        deviceName = deviceName,
    )
}

@Preview(showBackground = true)
@Composable
private fun CodeGaugeAppPreview() {
    CodeGaugeTheme {
        CodeGaugeApp(
            repository = PairingRepository(PreviewPairingApi, InMemoryPairingStore()),
            discovery = NoopCompanionDiscovery,
            deviceName = "Preview Android",
        )
    }
}

private object PreviewPairingApi : PairingApi {
    override suspend fun pair(endpoint: CompanionEndpoint, request: PairRequest): PairResponse {
        return PairResponse(
            token = "preview-token",
            serverName = "CodeGauge Companion",
        )
    }
}
