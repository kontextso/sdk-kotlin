package so.kontext.ads.internal.data.mapper

import android.util.Log
import so.kontext.ads.domain.AdDisplayPosition

internal fun AdDisplayPosition.Companion.toDomain(text: String): AdDisplayPosition {
    return when (text) {
        "afterUserMessage" -> AdDisplayPosition.AfterUserMessage
        "afterAssistantMessage" -> AdDisplayPosition.AfterAssistantMessage
        else -> {
            Log.e(
                "Kontext SDK",
                "Unknown role: $text, returning default value: AdDisplayPosition.AfterUserMessage",
                IllegalArgumentException("Unknown role: $text"),
            )
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
