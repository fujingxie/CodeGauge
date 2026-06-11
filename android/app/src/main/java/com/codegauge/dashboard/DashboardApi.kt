package com.codegauge.dashboard

import com.codegauge.pairing.PairingRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

interface DashboardApi {
    suspend fun status(pairing: PairingRecord): DashboardSnapshot
}

class OkHttpDashboardApi(
    private val client: OkHttpClient = OkHttpClient(),
) : DashboardApi {
    override suspend fun status(pairing: PairingRecord): DashboardSnapshot {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${pairing.serverUrl.trimEnd('/')}/api/v1/status")
                .header("Authorization", "Bearer ${pairing.token}")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw DashboardException(
                            parseErrorMessage(body)
                                ?: "Status failed with HTTP ${response.code}",
                        )
                    }

                    DashboardJsonParser.parseStatus(body)
                }
            } catch (exception: DashboardException) {
                throw exception
            } catch (exception: IOException) {
                throw DashboardException("Cannot reach ${pairing.serverUrl}", exception)
            }
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
}

