package com.kontext.ads.internal.data.mapper

import com.kontext.ads.domain.AdDisplayPosition

internal fun AdDisplayPosition.Companion.toDomain(text: String): AdDisplayPosition {
    return when (text) {
        "afterUserMessage" -> AdDisplayPosition.AfterUserMessage
        "afterAssistantMessage" -> AdDisplayPosition.AfterAssistantMessage
        else -> {
            // TODO handle error logging
            // IllegalArgumentException("Unknown role: $text")
            AdDisplayPosition.AfterUserMessage
        }
    }
}

internal fun AdDisplayPosition.toDto(): String {
    return when (this) {
        AdDisplayPosition.AfterUserMessage -> "afterUserMessage"
        AdDisplayPosition.AfterAssistantMessage -> "afterAssistantMessage"
    }
}
