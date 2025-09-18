package so.kontext.ads.internal.ui.model

internal sealed interface IFrameEvent {
    data object InitIframe : IFrameEvent
    data object ShowIframe : IFrameEvent
    data object HideIframe : IFrameEvent

    data class Resize(
        val height: Float,
    ) : IFrameEvent

    data class AdDone(
        val id: String,
        val content: String,
        val messageId: String,
    ) : IFrameEvent

    data class View(
        val id: String,
        val content: String,
        val messageId: String,
    ) : IFrameEvent

    data class Click(
        val id: String,
        val content: String,
        val messageId: String,
        val url: String,
    ) : IFrameEvent

    data class OpenComponent(
        val code: String,
        val component: String,
        val timeout: Int,
    ) : IFrameEvent

    data class InitComponent(
        val code: String,
        val component: String,
    ) : IFrameEvent

    data class ErrorComponent(
        val code: String,
        val component: String,
    ) : IFrameEvent

    data class CloseComponent(
        val code: String,
        val component: String,
    ) : IFrameEvent

    data class Error(
        val message: String,
    ) : IFrameEvent

    data class Unknown(
        val type: String,
        val data: String,
    ) : IFrameEvent

    sealed interface CallbackEvent : IFrameEvent {
        val code: String

        data class Viewed(
            override val code: String,
            val bidId: String,
            val content: String,
            val messageId: String,
            val format: String,
        ) : CallbackEvent

        data class Clicked(
            override val code: String,
            val bidId: String,
            val content: String,
            val messageId: String,
            val url: String,
            val format: String,
            val area: String,
        ) : CallbackEvent

        data class RenderStarted(
            override val code: String,
            val bidId: String,
        ) : CallbackEvent

        data class RenderCompleted(
            override val code: String,
            val bidId: String,
        ) : CallbackEvent

        data class Error(
            override val code: String,
            val message: String,
            val errCode: String,
        ) : CallbackEvent

        data class RewardGranted(
            override val code: String,
            val bidId: String,
        ) : CallbackEvent

        data class VideoStarted(
            override val code: String,
            val bidId: String,
        ) : CallbackEvent

        data class VideoCompleted(
            override val code: String,
            val bidId: String,
        ) : CallbackEvent

        data class Generic(
            override val code: String,
            val payload: Map<String, Any?>,
        ) : CallbackEvent
    }
}
