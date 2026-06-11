package com.codegauge.dashboard

class DashboardException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

