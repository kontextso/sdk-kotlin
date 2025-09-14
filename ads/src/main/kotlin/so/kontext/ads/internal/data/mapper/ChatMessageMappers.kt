package so.kontext.ads.internal.data.mapper

import so.kontext.ads.domain.ChatMessage
import so.kontext.ads.domain.MessageRepresentable
import so.kontext.ads.domain.Role
import so.kontext.ads.internal.data.dto.request.ChatMessageDto

internal fun ChatMessage.toDto(): ChatMessageDto {
    return ChatMessageDto(
        id = id,
        role = role.toDto(),
        content = content,
        createdAt = createdAt,
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
        createdAt = createdAt,
    )
}
