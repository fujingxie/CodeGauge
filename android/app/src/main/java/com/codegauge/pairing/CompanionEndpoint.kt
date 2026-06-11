package com.codegauge.pairing

data class CompanionEndpoint(
    val name: String,
    val host: String,
    val port: Int,
) {
    val baseUrl: String
        get() = "http://${hostForUrl()}:$port"

    private fun hostForUrl(): String {
        return if (host.contains(":") && !host.startsWith("[")) {
            "[$host]"
        } else {
            host
        }
    }
}

