package com.kontext.ads.internal.data.mapper

import com.kontext.ads.domain.ChatMessage
import com.kontext.ads.domain.Role
import com.kontext.ads.internal.data.dto.request.ChatMessageDto

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
