package com.codegauge.activity

import com.codegauge.pairing.PairingRecord
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

interface ActivityStreamClient {
    fun connect(
        pairing: PairingRecord,
        onMessage: (ActivityStreamMessage) -> Unit,
        onFailure: (Throwable) -> Unit,
        onClosed: () -> Unit = {},
    ): ActivityStreamConnection
}

fun interface ActivityStreamConnection {
    fun close()
}

class OkHttpActivityStreamClient(
    private val client: OkHttpClient = OkHttpClient(),
) : ActivityStreamClient {
    override fun connect(
        pairing: PairingRecord,
        onMessage: (ActivityStreamMessage) -> Unit,
        onFailure: (Throwable) -> Unit,
        onClosed: () -> Unit,
    ): ActivityStreamConnection {
        val request = Request.Builder()
            .url(streamUrl(pairing.serverUrl))
            .header("Authorization", "Bearer ${pairing.token}")
            .build()
        val webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        onMessage(ActivityJsonParser.parseStreamMessage(text))
                    } catch (exception: Exception) {
                        onFailure(exception)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onFailure(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onClosed()
                }
            },
        )

        return ActivityStreamConnection {
            webSocket.close(1000, "Activity screen closed")
        }
    }

    private fun streamUrl(serverUrl: String): String {
        val baseUrl = serverUrl.trimEnd('/')
        val wsBase = when {
            baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
            else -> "ws://$baseUrl"
        }
        return "$wsBase/api/v1/stream"
    }
}
