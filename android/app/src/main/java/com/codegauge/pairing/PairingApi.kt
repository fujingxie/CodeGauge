package com.codegauge.pairing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

data class PairRequest(
    val pairCode: String,
    val deviceName: String,
)

data class PairResponse(
    val token: String,
    val serverName: String,
)

interface PairingApi {
    suspend fun pair(endpoint: CompanionEndpoint, request: PairRequest): PairResponse
}

class OkHttpPairingApi(
    private val client: OkHttpClient = OkHttpClient(),
) : PairingApi {
    override suspend fun pair(endpoint: CompanionEndpoint, request: PairRequest): PairResponse {
        return withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("pair_code", request.pairCode)
                .put("device_name", request.deviceName)
                .toString()
                .toRequestBody(JsonMediaType)

            val httpRequest = Request.Builder()
                .url("${endpoint.baseUrl}/api/v1/pair")
                .post(body)
                .build()

            try {
                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw PairingException(
                            parseErrorMessage(responseBody)
                                ?: "配对请求失败：HTTP ${response.code}",
                        )
                    }

                    val json = JSONObject(responseBody)
                    val token = json.optString("token")
                    if (token.isBlank()) {
                        throw PairingException("配对响应缺少 token")
                    }

                    PairResponse(
                        token = token,
                        serverName = json.optString("server_name")
                            .ifBlank { "CodeGauge Companion" },
                    )
                }
            } catch (exception: PairingException) {
                throw exception
            } catch (exception: IOException) {
                throw PairingException("无法连接 ${endpoint.baseUrl}", exception)
            } catch (exception: JSONException) {
                throw PairingException("配对响应不是有效 JSON", exception)
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

    companion object {
        private val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}
