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
        val id = data?.optString("id") ?: return null
        val content = data.optString("content") ?: return null
        val messageId = data.optString("messageId") ?: return null
        val url = data.optString("url") ?: return null

        return IFrameEvent.Click(
            id = id,
            content = content,
            messageId = messageId,
            url = url,
        )
    }

    private fun parseAdDoneEvent(data: JSONObject?): IFrameEvent.AdDone? {
        val id = data?.optString("id") ?: return null
        val content = data.optString("content") ?: return null
        val messageId = data.optString("messageId") ?: return null

        return IFrameEvent.AdDone(id, content, messageId)
    }

    private fun parseViewEvent(data: JSONObject?): IFrameEvent.View? {
        val id = data?.optString("id") ?: return null
        val content = data.optString("content") ?: return null
        val messageId = data.optString("messageId") ?: return null

        return IFrameEvent.View(id, content, messageId)
    }

    private fun parseErrorEvent(root: JSONObject): IFrameEvent.Error {
        val message = root.optString("message", "Unknown error")
        return IFrameEvent.Error(message = message)
    }

    private fun parseOpenComponentEvent(data: JSONObject?): IFrameEvent.OpenComponent? {
        val code = data?.optString("code") ?: return null
        val component = data.optString("component") ?: return null
        val timeout = data.optInt("timeout", ModalTimeoutDefault)

        return IFrameEvent.OpenComponent(code, component, timeout)
    }

    private fun parseInitComponentEvent(data: JSONObject?): IFrameEvent.InitComponent? {
        val code = data?.optString("code") ?: return null
        val component = data.optString("component") ?: return null

        return IFrameEvent.InitComponent(code, component)
    }

    private fun parseErrorComponentEvent(data: JSONObject?): IFrameEvent.ErrorComponent? {
        val code = data?.optString("code") ?: return null
        val component = data.optString("component") ?: return null

        return IFrameEvent.ErrorComponent(code, component)
    }

    private fun parseCloseComponentEvent(data: JSONObject?): IFrameEvent.CloseComponent? {
        val code = data?.optString("code") ?: return null
        val component = data.optString("component") ?: return null

        return IFrameEvent.CloseComponent(code, component)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun parseCallbackEvent(data: JSONObject?): IFrameEvent? {
        val name = data?.optString("name") ?: return null
        val code = data.optString("code") ?: return null
        val payload = data.optJSONObject("payload")

        return when (name) {
            EventClickedIFrame -> {
                val bidId = payload?.optString("id") ?: return null
                val content = data.optString("content") ?: return null
                val messageId = data.optString("messageId") ?: return null
                val url = data.optString("url") ?: return null

                IFrameEvent.CallbackEvent.Clicked(code, bidId, content, messageId, url)
            }
            EventViewedIFrame -> {
                val bidId = payload?.optString("id") ?: return null
                val content = data.optString("content") ?: return null
                val messageId = data.optString("messageId") ?: return null

                IFrameEvent.CallbackEvent.Viewed(code, bidId, content, messageId)
            }
            EventRenderStartedIFrame -> {
                val bidId = payload?.optString("id") ?: return null
                IFrameEvent.CallbackEvent.RenderStarted(code, bidId)
            }
            EventRenderCompletedIFrame -> {
                val bidId = payload?.optString("id") ?: return null
                IFrameEvent.CallbackEvent.RenderCompleted(code, bidId)
            }
            EventErrorIFrame -> {
                val message = payload?.optString("message") ?: return null
                val errCode = payload.optString("errCode") ?: return null
                IFrameEvent.CallbackEvent.Error(code, message, errCode)
            }
            EventRewardGrantedIFrame -> {
                val bidId = payload?.optString("id") ?: return null
                IFrameEvent.CallbackEvent.RewardGranted(code, bidId)
            }
            EventVideoStartedIFrame -> {
                val bidId = payload?.optString("id") ?: return null
                IFrameEvent.CallbackEvent.VideoStarted(code, bidId)
            }
            EventVideoCompletedIFrame -> {
                val bidId = payload?.optString("id") ?: return null
                IFrameEvent.CallbackEvent.VideoCompleted(code, bidId)
            }
            else -> {
                val payloadMap = payload?.toString()?.jsonToMap() ?: emptyMap()
                IFrameEvent.CallbackEvent.Generic(code, payloadMap)
            }
        }
    }
}
