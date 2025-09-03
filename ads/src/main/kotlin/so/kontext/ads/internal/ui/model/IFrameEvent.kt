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

        data class Viewed(override val code: String) : CallbackEvent
        data class Clicked(override val code: String) : CallbackEvent
        data class VideoPlayed(override val code: String) : CallbackEvent
        data class VideoClosed(override val code: String) : CallbackEvent
        data class RewardReceived(override val code: String) : CallbackEvent
        data class Generic(
            override val code: String,
            val name: String,
            val payload: Map<String, Any?>,
        ) : CallbackEvent
    }
}
