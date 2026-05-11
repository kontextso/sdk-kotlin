package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape for `Character` in the `/preload` request body. The domain
 * counterpart is `so.kontext.ads.model.Character`; conversion lives in
 * `Character.toDto()`. `avatarUrl` is the URI's string form (the domain
 * type carries a `java.net.URI`).
 *
 * `id` and `name` are required; everything else is publisher-supplied
 * optional metadata. The deprecated server field `title` is
 * intentionally omitted — server treats it as superseded by `name`.
 *
 * Mirrors iOS `CharacterDTO` (`Networking/DTO/CharacterDTO.swift`).
 */
@Serializable
internal data class CharacterDto(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val greeting: String? = null,
    val persona: String? = null,
    val tags: List<String>? = null,
    val isNsfw: Boolean? = null,
)
