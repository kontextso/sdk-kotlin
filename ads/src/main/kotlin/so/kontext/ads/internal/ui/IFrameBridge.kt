package so.kontext.ads.internal.ui

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.internal.data.dto.request.UpdateIFrameDataDto
import so.kontext.ads.internal.data.dto.request.UpdateIFrameRequest
import so.kontext.ads.internal.data.mapper.toDto
import so.kontext.ads.internal.ui.model.IFrameEvent
import so.kontext.ads.internal.utils.jsonToMap

internal const val IFrameBridgeName = "AndroidBridge"

// General IFrame events
private const val UpdateIFrame = "update-iframe"
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
private const val EventViewedIFrame = "viewed"
private const val EventClickedIFrame = "clicked"
private const val EventVideoPlayedIFrame = "video-played"
private const val EventVideoClosedIFrame = "video-closed"
private const val EventRewardReceivedIFrame = "reward-received"

internal class IFrameBridge(
    private val onEvent: (IFrameEvent) -> Unit,
) {
    companion object {
        @Language("JavaScript")
        internal const val DocumentStartScript = """
            (function() {
              if (window.__androidBridgeInstalled) return;
              window.__androidBridgeInstalled = true;
            
              window.addEventListener('message', function(e) {
                try {
                  var data = e && e.data !== undefined ? e.data : null;
                  if (data == null) return;
                  $IFrameBridgeName.onMessage(typeof data === 'string' ? data : JSON.stringify(data));
                } catch (e) {}
              }, true);
            })();
            """

        internal const val PosterStartScript = """
            (function(){
              // 1x1 transparent PNG
              const T = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIW2NkYGBgAAAABAABJzQnCgAAAABJRU5ErkJggg==";
              // Make videos render on black, no placeholder
              const css = document.createElement('style');
              css.textContent = "video{background:#000!important;}";
              document.documentElement.appendChild(css);

              const apply = () => {
                document.querySelectorAll('video').forEach(v => {
                  // Overwrite any poster to guarantee no placeholder image
                  v.setAttribute('poster', T);
                  v.setAttribute('playsinline','');
                  v.setAttribute('preload','auto');
                });
              };
              apply();
              new MutationObserver(apply).observe(document.documentElement, {childList:true, subtree:true});
            })();
        """
    }

    @JavascriptInterface
    fun onMessage(json: String) {
        val inlineAdEvent = parseEvent(json)
        onEvent(inlineAdEvent)
    }
}

internal fun sendUpdateIframe(webView: WebView, config: AdConfig) {
    val updatePayload = UpdateIFrameRequest(
        type = UpdateIFrame,
        code = config.bid.code,
        data = UpdateIFrameDataDto(
            messages = config.messages.map { it.toDto() },
            messageId = config.messageId,
            sdk = config.sdk,
            otherParams = config.otherParams,
        ),
    )
    val updatePayloadJson = Json.encodeToString<UpdateIFrameRequest>(updatePayload)
    webView.evaluateJavascript("window.postMessage($updatePayloadJson, '*');", null)
}

@Suppress("CyclomaticComplexMethod")
private fun parseEvent(json: String): IFrameEvent = try {
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

private fun parseCallbackEvent(data: JSONObject?): IFrameEvent? {
    val name = data?.optString("name") ?: return null
    val code = data.optString("code") ?: return null
    val payload = data.optJSONObject("payload")

    return when (name) {
        EventViewedIFrame -> IFrameEvent.CallbackEvent.Viewed(code)
        EventClickedIFrame -> IFrameEvent.CallbackEvent.Clicked(code)
        EventVideoPlayedIFrame -> IFrameEvent.CallbackEvent.VideoPlayed(code)
        EventVideoClosedIFrame -> IFrameEvent.CallbackEvent.VideoClosed(code)
        EventRewardReceivedIFrame -> IFrameEvent.CallbackEvent.RewardReceived(code)
        else -> {
            val payloadMap = payload?.toString()?.jsonToMap() ?: emptyMap()
            IFrameEvent.CallbackEvent.Generic(name, code, payloadMap)
        }
    }
}
