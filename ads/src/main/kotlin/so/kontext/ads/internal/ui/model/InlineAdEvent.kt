package so.kontext.ads.internal.ui.model

internal sealed interface InlineAdEvent {
    data object InitIframe : InlineAdEvent
    data object ShowIframe : InlineAdEvent
    data object HideIframe : InlineAdEvent

    data class ResizeIframe(
        val height: Float,
    ) : InlineAdEvent

    data class AdDoneIframe(
        val id: String,
        val content: String,
        val messageId: String,
    ) : InlineAdEvent

    data class ViewIframe(
        val id: String,
        val content: String,
        val messageId: String,
    ) : InlineAdEvent

    data class ClickIframe(
        val id: String,
        val content: String,
        val messageId: String,
        val url: String,
    ) : InlineAdEvent

    data class OpenComponentIframe(
        val code: String,
        val component: String,
        val timeout: Int,
    ) : InlineAdEvent

    data class InitComponentIframe(
        val code: String,
        val component: String,
    ) : InlineAdEvent

    data class ErrorComponentIframe(
        val code: String,
        val component: String,
    ) : InlineAdEvent

    data class CloseComponentIframe(
        val code: String,
        val component: String,
    ) : InlineAdEvent

    data class Error(
        val message: String,
    ) : InlineAdEvent

    data class Unknown(
        val type: String,
        val data: String,
    ) : InlineAdEvent
}
