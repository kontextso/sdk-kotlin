package so.kontext.ads.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import so.kontext.ads.network.dto.MessageDto
import so.kontext.ads.utils.DateFormatting
import java.util.Date

/**
 * A single message in the ongoing conversation. Authored either by the user or
 * by the assistant — the publisher app is responsible for assigning the correct
 * [Role] before passing it to `Session.addMessage(...)`.
 *
 * `id` must be stable across re-renders so the SDK can attach bids to a
 * specific message. `createdAt` is opaque to the SDK; it's currently
 * unused but kept on the public type for future server-side ordering.
 *
 * Wire encoding goes through [MessageDto] (`createdAt` becomes an ISO 8601
 * string; `role` becomes its lowercase name).
 *
 * Mirrors iOS `Message` (`KontextSwiftSDK/Model/Message.swift`).
 */
public data class Message(
    val id: String,
    val role: Role,
    val content: String,
    val createdAt: Date = Date(),
)

/**
 * Conversation participant — `value` is the lowercase wire form
 * (`"user"` / `"assistant"`) the server expects on the preload payload.
 *
 * The server's role enum also includes `"system"`, reserved for
 * server-generated system prompts; it's intentionally not exposed
 * here so publishers can't author system messages from the SDK.
 *
 * The `@SerialName` annotations carry the lowercase wire form to
 * kotlinx.serialization so [MessageDto.role] (a typed `Role`)
 * serializes to the same JSON as iOS's `Message.Role` enum.
 */
@Serializable
public enum class Role {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,

    ;

    public val value: String get() = name.lowercase()
}

internal fun Message.toDto(): MessageDto = MessageDto(
    id = id,
    role = role,
    content = content,
    createdAt = DateFormatting.iso8601String(createdAt),
)
