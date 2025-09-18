package so.kontext.ads.internal.data.mapper

import so.kontext.ads.internal.ui.model.IFrameEvent
import so.kontext.ads.ui.AdEvent

internal fun IFrameEvent.CallbackEvent.toPublicAdEvent(): AdEvent {
    return when (this) {
        is IFrameEvent.CallbackEvent.Clicked -> {
            AdEvent.Clicked(
                code = code,
                bidId = bidId,
                content = content,
                messageId = messageId,
                url = url,
                format = format,
                area = area,
            )
        }
        is IFrameEvent.CallbackEvent.Viewed -> {
            AdEvent.Viewed(
                code = code,
                bidId = bidId,
                content = content,
                messageId = messageId,
                format = format,
            )
        }
        is IFrameEvent.CallbackEvent.RenderStarted -> {
            AdEvent.RenderStarted(
                code = code,
                bidId = bidId,
            )
        }
        is IFrameEvent.CallbackEvent.RenderCompleted -> {
            AdEvent.RenderCompleted(
                code = code,
                bidId = bidId,
            )
        }
        is IFrameEvent.CallbackEvent.Error -> {
            AdEvent.Error(
                code = code,
                message = message,
                errCode = errCode,
            )
        }
        is IFrameEvent.CallbackEvent.VideoStarted -> {
            AdEvent.VideoStarted(
                code = code,
                bidId = bidId,
            )
        }
        is IFrameEvent.CallbackEvent.VideoCompleted -> {
            AdEvent.VideoCompleted(
                code = code,
                bidId = bidId,
            )
        }
        is IFrameEvent.CallbackEvent.Generic -> {
            AdEvent.Generic(
                code = code,
                payload = payload,
            )
        }
        is IFrameEvent.CallbackEvent.RewardGranted -> {
            AdEvent.RewardGranted(
                code = code,
                bidId = bidId,
            )
        }
    }
}
