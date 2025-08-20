package so.kontext.ads.domain

public interface AdChatMessage {
    public val id: String
    public val role: Role
    public val content: String
    public val createdAt: String
}
