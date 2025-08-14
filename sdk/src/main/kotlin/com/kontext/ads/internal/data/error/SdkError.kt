package com.kontext.ads.internal.data.error

internal open class SdkError(
    message: String? = null,
    cause: Throwable?,
) : Exception(message, cause)
