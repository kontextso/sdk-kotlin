package com.kontext.ads.internal.data.mapper

import com.kontext.ads.domain.Character
import com.kontext.ads.internal.data.dto.request.CharacterDto

internal fun Character.toDto(): CharacterDto {
    return CharacterDto(
        id = id,
        name = name,
        avatarUrl = avatarUrl,
        isNsfw = isNsfw,
        greeting = greeting,
        persona = persona,
        tags = tags,
    )
}

internal fun CharacterDto.toDomain(): Character {
    return Character(
        id = id,
        name = name,
        avatarUrl = avatarUrl,
        isNsfw = isNsfw,
        greeting = greeting,
        persona = persona,
        tags = tags,
    )
}
