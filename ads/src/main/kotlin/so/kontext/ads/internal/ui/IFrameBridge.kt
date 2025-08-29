package so.kontext.ads.internal.ui

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.internal.data.dto.request.UpdateIFrameDataDto
import so.kontext.ads.internal.data.dto.request.UpdateIFrameRequest
import so.kontext.ads.internal.data.mapper.toDto
import so.kontext.ads.internal.ui.model.InlineAdEvent

internal const val IFrameBridgeName = "AndroidBridge"

private const val UpdateIFrame = "update-iframe"
private const val InitIFrameType = "init-iframe"
private const val ShowIFrame = "show-iframe"
private const val HideIFrame = "hide-iframe"
private const val ResizeIFrame = "resize-iframe"
private const val AdDoneIFrame = "ad-done-iframe"
private const val ViewIFrame = "view-iframe"
private const val ClickIFrame = "click-iframe"
private const val ErrorIFrame = "error-iframe"
private const val OpenComponentIFrame = "open-component-iframe"
private const val InitComponentIFrame = "init-component-iframe"
private const val ErrorComponentIFrame = "error-component-iframe"
private const val CloseComponentIFrame = "close-component-iframe"

internal class IFrameBridge(
    private val onEvent: (InlineAdEvent) -> Unit,
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
    val updatePayloadJson = Json.encodeToString(updatePayload)
    webView.evaluateJavascript("window.postMessage($updatePayloadJson, '*');", null)
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
        OpenComponentIFrame -> parseOpenComponentEvent(data) ?: InlineAdEvent.Unknown(type, json)
        InitComponentIFrame -> parseInitComponentEvent(data) ?: InlineAdEvent.Unknown(type, json)
        ErrorComponentIFrame -> parseErrorComponentEvent(data) ?: InlineAdEvent.Unknown(type, json)
        CloseComponentIFrame -> parseCloseComponentEvent(data) ?: InlineAdEvent.Unknown(type, json)
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

private fun parseOpenComponentEvent(data: JSONObject?): InlineAdEvent.OpenComponentIframe? {
    val code = data?.optString("code") ?: return null
    val component = data.optString("component") ?: return null
    val timeout = data.optInt("timeout") ?: return null

    return InlineAdEvent.OpenComponentIframe(code, component, timeout)
}

private fun parseInitComponentEvent(data: JSONObject?): InlineAdEvent.InitComponentIframe? {
    val code = data?.optString("code") ?: return null
    val component = data.optString("component") ?: return null

    return InlineAdEvent.InitComponentIframe(code, component)
}

private fun parseErrorComponentEvent(data: JSONObject?): InlineAdEvent.ErrorComponentIframe? {
    val code = data?.optString("code") ?: return null
    val component = data.optString("component") ?: return null

    return InlineAdEvent.ErrorComponentIframe(code, component)
}

private fun parseCloseComponentEvent(data: JSONObject?): InlineAdEvent.CloseComponentIframe? {
    val code = data?.optString("code") ?: return null
    val component = data.optString("component") ?: return null

    return InlineAdEvent.CloseComponentIframe(code, component)
}
