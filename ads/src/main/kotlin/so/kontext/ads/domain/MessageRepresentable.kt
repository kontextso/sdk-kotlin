package so.kontext.ads.domain

/**
 * Provides necessary information for the AdsProvider about the message's content.
 * Either use this protocol for your own message data class or use [AdsMessage]
 *
 * @param id unique message id
 * @param role message role
 * @param content content of the message
 * @param createdAt creation of the message timestamp according to ISO 8601 format
 */
public interface MessageRepresentable {
    public val id: String
    public val role: Role
    public val content: String
    public val createdAt: String
}

/**
 * Convenience type that already represents a message. This can be useful if conforming to
 * `MessageRepresentable` directly is not desired.
 *
 * @param id unique message id
 * @param role message role
 * @param content content of the message
 * @param createdAt creation of the message timestamp according to ISO 8601 format
 */
public data class AdsMessage(
    override val id: String,
    override val role: Role,
    override val content: String,
    override val createdAt: String,
) : MessageRepresentable
