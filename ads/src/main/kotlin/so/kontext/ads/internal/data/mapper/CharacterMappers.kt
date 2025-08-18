package so.kontext.ads.internal.data.mapper

import so.kontext.ads.domain.Character
import so.kontext.ads.internal.data.dto.request.CharacterDto

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
