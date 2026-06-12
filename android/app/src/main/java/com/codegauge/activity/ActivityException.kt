package com.codegauge.activity

class ActivityException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

