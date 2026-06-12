package com.codegauge.settings

import com.codegauge.pairing.PairingRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

interface SettingsApi {
    suspend fun settings(pairing: PairingRecord): AppSettings

    suspend fun updateSettings(
        pairing: PairingRecord,
        update: SettingsUpdate,
    ): AppSettings

    suspend fun devices(pairing: PairingRecord): List<PairedDevice>

    suspend fun diagnostics(pairing: PairingRecord): CompanionDiagnostics
}

class OkHttpSettingsApi(
    private val client: OkHttpClient = OkHttpClient(),
) : SettingsApi {
    override suspend fun settings(pairing: PairingRecord): AppSettings {
        return withContext(Dispatchers.IO) {
            val body = execute(pairing, "/api/v1/settings") {
                get()
            }
            SettingsJsonParser.parseSettings(body)
        }
    }

    override suspend fun updateSettings(
        pairing: PairingRecord,
        update: SettingsUpdate,
    ): AppSettings {
        return withContext(Dispatchers.IO) {
            val requestBody = SettingsJsonParser.settingsPatchBody(update)
                .toRequestBody(JsonMediaType)
            val body = execute(pairing, "/api/v1/settings") {
                patch(requestBody)
            }
            SettingsJsonParser.parseSettings(body)
        }
    }

    override suspend fun devices(pairing: PairingRecord): List<PairedDevice> {
        return withContext(Dispatchers.IO) {
            val body = execute(pairing, "/api/v1/devices") {
                get()
            }
            SettingsJsonParser.parseDevices(body)
        }
    }

    override suspend fun diagnostics(pairing: PairingRecord): CompanionDiagnostics {
        return withContext(Dispatchers.IO) {
            val body = execute(pairing, "/api/v1/diagnostics") {
                get()
            }
            SettingsJsonParser.parseDiagnostics(body)
        }
    }

    private fun execute(
        pairing: PairingRecord,
        path: String,
        configure: Request.Builder.() -> Request.Builder,
    ): String {
        val request = Request.Builder()
            .url("${pairing.serverUrl.trimEnd('/')}$path")
            .header("Authorization", "Bearer ${pairing.token}")
            .configure()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SettingsException(
                        parseErrorMessage(body)
                            ?: "设置请求失败：HTTP ${response.code}",
                    )
                }
                return body
            }
        } catch (exception: SettingsException) {
            throw exception
        } catch (exception: IOException) {
            throw SettingsException("无法连接 ${pairing.serverUrl}", exception)
        }
    }

    private fun parseErrorMessage(responseBody: String): String? {
        if (responseBody.isBlank()) {
            return null
        }
        return runCatching {
            JSONObject(responseBody).optString("error").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    companion object {
        private val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}
