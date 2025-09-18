package so.kontext.ads.internal.ui

import org.json.JSONObject
import so.kontext.ads.internal.ui.model.IFrameEvent
import so.kontext.ads.internal.utils.jsonToMap

// General IFrame events
private const val InitIFrameType = "init-iframe"
private const val ShowIFrame = "show-iframe"
private const val HideIFrame = "hide-iframe"
private const val ResizeIFrame = "resize-iframe"
private const val AdDoneIFrame = "ad-done-iframe"
private const val ViewIFrame = "view-iframe"
private const val ClickIFrame = "click-iframe"
private const val ErrorIFrame = "error-iframe"

// Modal related events
private const val OpenComponentIFrame = "open-component-iframe"
private const val InitComponentIFrame = "init-component-iframe"
private const val ErrorComponentIFrame = "error-component-iframe"
private const val CloseComponentIFrame = "close-component-iframe"

// Callback events
private const val EventIFrame = "event-iframe"
private const val EventViewedIFrame = "ad.viewed"
private const val EventClickedIFrame = "ad.clicked"
private const val EventRenderStartedIFrame = "ad.render-started"
private const val EventRenderCompletedIFrame = "ad.render-completed"
private const val EventErrorIFrame = "ad.error"
private const val EventRewardGrantedIFrame = "reward.granted"
private const val EventVideoStartedIFrame = "video.started"
private const val EventVideoCompletedIFrame = "video.completed"

internal class IFrameEventParser {

    @Suppress("CyclomaticComplexMethod")
    fun parse(json: String): IFrameEvent = try {
        val root = JSONObject(json)
        val type = root.optString("type", "")
        val data = root.optJSONObject("data")

        when (type) {
            InitIFrameType -> IFrameEvent.InitIframe
            ShowIFrame -> IFrameEvent.ShowIframe
            HideIFrame -> IFrameEvent.HideIframe
            ResizeIFrame -> parseResizeEvent(data) ?: IFrameEvent.Unknown(type, json)
            AdDoneIFrame -> parseAdDoneEvent(data) ?: IFrameEvent.Unknown(type, json)
            ViewIFrame -> parseViewEvent(data) ?: IFrameEvent.Unknown(type, json)
            ClickIFrame -> parseClickEvent(data) ?: IFrameEvent.Unknown(type, json)
            OpenComponentIFrame -> parseOpenComponentEvent(data) ?: IFrameEvent.Unknown(type, json)
            InitComponentIFrame -> parseInitComponentEvent(data) ?: IFrameEvent.Unknown(type, json)
            ErrorComponentIFrame -> parseErrorComponentEvent(data) ?: IFrameEvent.Unknown(type, json)
            CloseComponentIFrame -> parseCloseComponentEvent(data) ?: IFrameEvent.Unknown(type, json)
            ErrorIFrame -> parseErrorEvent(root)
            EventIFrame -> parseCallbackEvent(data) ?: IFrameEvent.Unknown(type, json)
            else -> IFrameEvent.Unknown(type = type, data = json)
        }
    } catch (_: Throwable) {
        IFrameEvent.Unknown(type = "parse-error", data = json)
    }

    private fun parseResizeEvent(data: JSONObject?): IFrameEvent.Resize? {
        val height = data?.optDouble("height") ?: return null
        return IFrameEvent.Resize(height = height.toFloat())
    }

    private fun parseClickEvent(data: JSONObject?): IFrameEvent.Click? {
        return IFrameEvent.Click(
            id = data?.optString("id") ?: return null,
            content = data.optString("content"),
            messageId = data.optString("messageId"),
            url = data.optString("url"),
        )
    }

    private fun parseAdDoneEvent(data: JSONObject?): IFrameEvent.AdDone? {
        return IFrameEvent.AdDone(
            id = data?.optString("id") ?: return null,
            content = data.optString("content"),
            messageId = data.optString("messageId"),
        )
    }

    private fun parseViewEvent(data: JSONObject?): IFrameEvent.View? {
        return IFrameEvent.View(
            id = data?.optString("id") ?: return null,
            content = data.optString("content"),
            messageId = data.optString("messageId"),
        )
    }

    private fun parseErrorEvent(root: JSONObject): IFrameEvent.Error {
        val message = root.optString("message", "Unknown error")
        return IFrameEvent.Error(message = message)
    }

    private fun parseOpenComponentEvent(data: JSONObject?): IFrameEvent.OpenComponent? {
        return IFrameEvent.OpenComponent(
            code = data?.optString("code") ?: return null,
            component = data.optString("component"),
            timeout = data.optInt("timeout", ModalTimeoutDefault),
        )
    }

    private fun parseInitComponentEvent(data: JSONObject?): IFrameEvent.InitComponent? {
        return IFrameEvent.InitComponent(
            code = data?.optString("code") ?: return null,
            component = data.optString("component"),
        )
    }

    private fun parseErrorComponentEvent(data: JSONObject?): IFrameEvent.ErrorComponent? {
        return IFrameEvent.ErrorComponent(
            code = data?.optString("code") ?: return null,
            component = data.optString("component"),
        )
    }

    private fun parseCloseComponentEvent(data: JSONObject?): IFrameEvent.CloseComponent? {
        return IFrameEvent.CloseComponent(
            code = data?.optString("code") ?: return null,
            component = data.optString("component"),
        )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun parseCallbackEvent(data: JSONObject?): IFrameEvent? {
        val name = data?.optString("name") ?: return null
        val code = data.optString("code") ?: return null
        val payload = data.optJSONObject("payload") ?: return null

        return when (name) {
            EventClickedIFrame -> {
                IFrameEvent.CallbackEvent.Clicked(
                    code = code,
                    bidId = payload.optString("id"),
                    content = payload.optString("content"),
                    messageId = payload.optString("messageId"),
                    url = payload.optString("url"),
                    format = payload.optString("format"),
                    area = payload.optString("area"),
                )
            }
            EventViewedIFrame -> {
                IFrameEvent.CallbackEvent.Viewed(
                    code = code,
                    bidId = payload.optString("id"),
                    content = payload.optString("content"),
                    messageId = payload.optString("messageId"),
                    format = payload.optString("format"),
                )
            }
            EventRenderStartedIFrame -> {
                IFrameEvent.CallbackEvent.RenderStarted(
                    code = code,
                    bidId = payload.optString("id"),
                )
            }
            EventRenderCompletedIFrame -> {
                IFrameEvent.CallbackEvent.RenderCompleted(
                    code = code,
                    bidId = payload.optString("id"),
                )
            }
            EventErrorIFrame -> {
                IFrameEvent.CallbackEvent.Error(
                    code = code,
                    message = payload.optString("message"),
                    errCode = payload.optString("errCode"),
                )
            }
            EventRewardGrantedIFrame -> {
                IFrameEvent.CallbackEvent.RewardGranted(
                    code = code,
                    bidId = payload.optString("id"),
                )
            }
            EventVideoStartedIFrame -> {
                IFrameEvent.CallbackEvent.VideoStarted(
                    code = code,
                    bidId = payload.optString("id"),
                )
            }
            EventVideoCompletedIFrame -> {
                IFrameEvent.CallbackEvent.VideoCompleted(
                    code = code,
                    bidId = payload.optString("id"),
                )
            }
            else -> {
                val payloadMap = payload.toString().jsonToMap() ?: emptyMap()
                IFrameEvent.CallbackEvent.Generic(
                    code = code,
                    payload = payloadMap,
                )
            }
        }
    }
}
