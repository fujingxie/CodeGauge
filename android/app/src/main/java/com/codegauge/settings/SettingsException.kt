package com.codegauge.settings

class SettingsException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
