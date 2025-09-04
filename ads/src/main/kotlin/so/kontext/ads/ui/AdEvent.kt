package so.kontext.ads.ui

public sealed interface AdEvent {
    public val code: String

    public data class Viewed(
        override val code: String,
        val bidId: String,
        val content: String,
        val messageId: String,
    ) : AdEvent

    public data class Clicked(
        override val code: String,
        val bidId: String,
        val content: String,
        val messageId: String,
        val url: String,
    ) : AdEvent

    public data class RenderStarted(
        override val code: String,
        val bidId: String,
    ) : AdEvent

    public data class RenderCompleted(
        override val code: String,
        val bidId: String,
    ) : AdEvent

    public data class Error(
        override val code: String,
        val message: String,
        val errCode: String,
    ) : AdEvent

    public data class RewardGranted(
        override val code: String,
        val bidId: String,
    ) : AdEvent

    public data class VideoStarted(
        override val code: String,
        val bidId: String,
    ) : AdEvent

    public data class VideoCompleted(
        override val code: String,
        val bidId: String,
    ) : AdEvent

    public data class Generic(
        override val code: String,
        val payload: Map<String, Any?>,
    ) : AdEvent
}
