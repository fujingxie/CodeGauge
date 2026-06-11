package com.codegauge.pairing

import java.net.URI

private const val DefaultCompanionPort = 8765
private val SchemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

fun parseManualEndpoint(input: String, defaultPort: Int = DefaultCompanionPort): CompanionEndpoint? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isBlank()) {
        return null
    }

    val valueWithScheme = if (SchemePattern.containsMatchIn(trimmed)) {
        trimmed
    } else {
        "http://$trimmed"
    }

    return runCatching {
        val uri = URI(valueWithScheme)
        val host = uri.host?.trim()?.trim('[', ']') ?: return null
        if (host.isBlank()) {
            return null
        }

        val port = if (uri.port == -1) defaultPort else uri.port
        if (port !in 1..65535) {
            return null
        }

        CompanionEndpoint(
            name = "$host:$port",
            host = host,
            port = port,
        )
    }.getOrNull()
}

