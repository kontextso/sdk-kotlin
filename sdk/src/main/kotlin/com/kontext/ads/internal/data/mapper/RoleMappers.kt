package com.kontext.ads.internal.data.mapper

import com.kontext.ads.domain.Role

internal fun Role.Companion.toDomain(text: String): Role {
    return when (text) {
        "user" -> Role.User
        "assistant" -> Role.Assistant
        else -> {
            // TODO implement error logging
            // IllegalArgumentException("Unknown role: $text")
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
