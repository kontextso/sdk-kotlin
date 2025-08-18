package so.kontext.ads.domain

public data class AdConfig(
    val url: String,
    val messages: List<ChatMessage>,
    val messageId: String,
    val sdk: String,
    val otherParams: Map<String, String>,
    val bid: Bid,
)
