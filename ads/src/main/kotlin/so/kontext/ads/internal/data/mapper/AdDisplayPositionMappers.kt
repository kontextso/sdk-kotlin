package so.kontext.ads.internal.data.mapper

import so.kontext.ads.domain.AdDisplayPosition

internal fun AdDisplayPosition.Companion.toDomain(text: String): AdDisplayPosition {
    return when (text) {
        "afterUserMessage" -> AdDisplayPosition.AfterUserMessage
        "afterAssistantMessage" -> AdDisplayPosition.AfterAssistantMessage
        else -> {
            // TODO log error
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
