package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable
import so.kontext.ads.model.Role

/**
 * Wire shape for a single message in the `/preload` request body. The
 * domain counterpart is `so.kontext.ads.model.Message`; conversion lives
 * in `Message.toDto()`.
 *
 * `role` is typed as the [Role] enum so the wire-format string set is
 * fixed at compile time; kotlinx-serialization emits the lowercase
 * `@SerialName` value (`"user"` / `"assistant"`).
 *
 * `createdAt` is a pre-formatted ISO 8601 string with millisecond
 * precision (see `DateFormatting`); it's already a `String` here
 * because formatting happens at the toDto boundary, not at JSON encode.
 *
 * Mirrors iOS `MessageDTO` (`Networking/DTO/PreloadRequestDTO.swift`).
 */
@Serializable
internal data class MessageDto(
    val id: String,
    val role: Role,
    val content: String,
    val createdAt: String,
)
