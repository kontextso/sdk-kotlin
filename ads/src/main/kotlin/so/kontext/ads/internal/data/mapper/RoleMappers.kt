package so.kontext.ads.internal.data.mapper

import android.util.Log
import so.kontext.ads.domain.Role

internal fun Role.Companion.toDomain(text: String): Role {
    return when (text) {
        "user" -> Role.User
        "assistant" -> Role.Assistant
        else -> {
            Log.e(
                "Kontext SDK",
                "Unknown role: $text, returning default value: Role.User",
                IllegalArgumentException("Unknown role: $text"),
            )
            Role.User
        }
    }
}

internal fun Role.toDto(): String {
    return when (this) {
        Role.User -> "user"
        Role.Assistant -> "assistant"
    }
}
