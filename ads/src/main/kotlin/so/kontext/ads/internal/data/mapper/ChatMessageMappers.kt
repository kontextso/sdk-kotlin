package so.kontext.ads.internal.data.mapper

import java.time.Instant
import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.MessageRepresentable
import so.kontext.ads.domain.Role
import so.kontext.ads.internal.data.dto.request.ChatMessageDto

internal fun ChatMessage.toDto(): ChatMessageDto {
    return ChatMessageDto(
        id = id,
        role = role.toDto(),
        content = content,
        createdAt = createdAt.ensureIso8601Timestamp(),
    )
}

internal fun ChatMessageDto.toDomain() {
    ChatMessage(
        id = id,
        role = Role.toDomain(role),
        content = content,
        createdAt = createdAt,
    )
}

internal fun MessageRepresentable.toInternalMessage(): ChatMessage {
    return ChatMessage(
        id = id,
        role = role,
        content = content,
        createdAt = createdAt.ensureIso8601Timestamp(),
    )
}

private fun String.ensureIso8601Timestamp(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) {
        return trimmed
    }

    runCatching {
        Instant.parse(trimmed)
    }.onSuccess {
        return it.toString()
    }

    return runCatching {
        val epochValue = trimmed.toLongOrNull() ?: return trimmed
        val instant = if (epochValue < 1_000_000_000_000L) {
            Instant.ofEpochSecond(epochValue)
        } else {
            Instant.ofEpochMilli(epochValue)
        }
        instant.toString()
    }.getOrElse { trimmed }
}
