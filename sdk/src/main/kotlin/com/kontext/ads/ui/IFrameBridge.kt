package com.kontext.ads.ui

import android.webkit.JavascriptInterface
import com.kontext.ads.ui.model.InlineAdEvent
import org.json.JSONObject

private const val InitIFrameType = "init-iframe"
private const val ShowIFrame = "show-iframe"
private const val HideIFrame = "hide-iframe"
private const val ResizeIFrame = "resize-iframe"
private const val AdDoneIFrame = "ad-done-iframe"
private const val ViewIFrame = "view-iframe"
private const val ClickIFrame = "click-iframe"
private const val ErrorIFrame = "error-iframe"

internal class IFrameBridge(
    private val onEvent: (InlineAdEvent) -> Unit,
) {
    @JavascriptInterface
    fun onMessage(json: String) {
        val inlineAdEvent = parseEvent(json)
        onEvent(inlineAdEvent)
    }
}

@Suppress("CyclomaticComplexMethod")
private fun parseEvent(json: String): InlineAdEvent = try {
    val root = JSONObject(json)
    val type = root.optString("type", "")
    val data = root.optJSONObject("data")

    when (type) {
        InitIFrameType -> InlineAdEvent.InitIframe
        ShowIFrame -> InlineAdEvent.ShowIframe
        HideIFrame -> InlineAdEvent.HideIframe
        ResizeIFrame -> parseResizeEvent(data) ?: InlineAdEvent.Unknown(type, json)
        AdDoneIFrame -> parseAdDoneEvent(data) ?: InlineAdEvent.Unknown(type, json)
        ViewIFrame -> parseViewEvent(data) ?: InlineAdEvent.Unknown(type, json)
        ClickIFrame -> parseClickEvent(data) ?: InlineAdEvent.Unknown(type, json)
        ErrorIFrame -> parseErrorEvent(root)
        else -> InlineAdEvent.Unknown(type = type, data = json)
    }
} catch (_: Throwable) {
    InlineAdEvent.Unknown(type = "parse-error", data = json)
}

private fun parseResizeEvent(data: JSONObject?): InlineAdEvent.ResizeIframe? {
    val height = data?.optDouble("height") ?: return null
    return InlineAdEvent.ResizeIframe(height = height.toFloat())
}

private fun parseClickEvent(data: JSONObject?): InlineAdEvent.ClickIframe? {
    val id = data?.optString("id") ?: return null
    val content = data.optString("content") ?: return null
    val messageId = data.optString("messageId") ?: return null
    val url = data.optString("url") ?: return null

    return InlineAdEvent.ClickIframe(
        id = id,
        content = content,
        messageId = messageId,
        url = url,
    )
}

private fun parseAdDoneEvent(data: JSONObject?): InlineAdEvent.AdDoneIframe? {
    val id = data?.optString("id") ?: return null
    val content = data.optString("content") ?: return null
    val messageId = data.optString("messageId") ?: return null

    return InlineAdEvent.AdDoneIframe(id, content, messageId)
}

private fun parseViewEvent(data: JSONObject?): InlineAdEvent.ViewIframe? {
    val id = data?.optString("id") ?: return null
    val content = data.optString("content") ?: return null
    val messageId = data.optString("messageId") ?: return null

    return InlineAdEvent.ViewIframe(id, content, messageId)
}

private fun parseErrorEvent(root: JSONObject): InlineAdEvent.Error {
    val message = root.optString("message", "Unknown error")
    return InlineAdEvent.Error(message = message)
}
