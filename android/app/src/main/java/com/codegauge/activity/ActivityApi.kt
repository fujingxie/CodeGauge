package com.codegauge.activity

import com.codegauge.pairing.PairingRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

interface ActivityApi {
    suspend fun events(pairing: PairingRecord, limit: Int = 50): List<ActivityEvent>
}

class OkHttpActivityApi(
    private val client: OkHttpClient = OkHttpClient(),
) : ActivityApi {
    override suspend fun events(pairing: PairingRecord, limit: Int): List<ActivityEvent> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${pairing.serverUrl.trimEnd('/')}/api/v1/events?limit=${limit.coerceIn(1, 200)}")
                .header("Authorization", "Bearer ${pairing.token}")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw ActivityException(
                            parseErrorMessage(body)
                                ?: "事件请求失败：HTTP ${response.code}",
                        )
                    }
                    ActivityJsonParser.parseEvents(body)
                }
            } catch (exception: ActivityException) {
                throw exception
            } catch (exception: IOException) {
                throw ActivityException("无法连接 ${pairing.serverUrl}", exception)
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
