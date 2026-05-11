package so.kontext.ads.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import so.kontext.ads.Ad
import so.kontext.ads.ui.iframe.IframeEvent
import so.kontext.ads.ui.iframe.buildUpdateDimensionsMessage
import so.kontext.ads.ui.iframe.buildUpdateIframeMessage
import so.kontext.ads.ui.iframe.buildUserEventMessage

/**
 * WebView wrapper that loads an ad iframe and handles bidirectional postMessage communication.
 *
 * Uses document-start script injection (via WebViewCompat) for early bridge setup.
 * Falls back to onPageFinished injection if document-start is not supported.
 *
 * Caches last sent dimensions to avoid redundant postMessage calls.
 */
internal class AdWebView(
    private var ad: Ad,
) {
    @Volatile private var webView: WebView? = null

    @Volatile private var initialized = false

    init {
        ad.adWebView = this
    }

    /**
     * Re-binds this AdWebView to a new Ad instance (used when pool returns
     * an existing entry for a recomposed composable).
     */
    fun rebind(newAd: Ad) {
        ad.adWebView = null
        ad = newAd
        newAd.adWebView = this
    }

    fun getWebView(): WebView? = webView

    /**
     * Sets up the JS interface and WebViewClient on an already-configured WebView.
     * WebView settings and document-start scripts are handled by [WebViewPool.baseAdSetup].
     */
    @SuppressLint("JavascriptInterface")
    fun setupWebView(webView: WebView) {
        this.webView = webView

        webView.addJavascriptInterface(BridgeInterface(), "kontextBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Fallback bridge injection for devices that don't support DOCUMENT_START_SCRIPT.
                // If document-start was used, this is harmless (bridge checks __kontextBridgeReady).
                view?.evaluateJavascript(bridgeScript(ad.session.config.adServerUrl), null)
            }
        }
    }

    fun load() {
        val url = ad.iframeUrl ?: return
        ad.session.debug("AdWebView: loading", mapOf("url" to url))
        webView?.loadUrl(url)
    }

    fun destroy() {
        ad.adWebView = null
        webView?.apply {
            removeJavascriptInterface("kontextBridge")
            stopLoading()
            loadUrl("about:blank")
        }
        webView = null
    }

    // ---------------------------------------------------------------------------
    // Sending messages to iframe
    // ---------------------------------------------------------------------------

    fun sendUpdateIframe() {
        postMessage(
            buildUpdateIframeMessage(
                messages = ad.session.messages,
                messageId = ad.messageId,
                code = ad.code,
                theme = ad.theme,
            ),
        )
    }

    /**
     * `windowWidth`/`windowHeight` are the visible app viewport (excluding
     * system bars). `screenWidth`/`screenHeight` are the full physical
     * display. They differ in multi-window / split-screen / freeform / on
     * foldables — callers must pass both real values, not duplicate one.
     */
    @Suppress("LongParameterList")
    fun sendDimensions(
        windowWidth: Float,
        windowHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        containerWidth: Float,
        containerHeight: Float,
        containerX: Float,
        containerY: Float,
        keyboardHeight: Float,
    ) {
        // Heartbeat: post unconditionally on every poll tick. Matches
        // sdk-swift and sdk-react-native, where the iframe and server
        // rely on the steady cadence for viewport-based optimisation
        // (viewability tracking, ML signals). The earlier hash cache
        // here skipped redundant sends and broke that protocol — the
        // 200ms cadence is the contract, not an over-optimisation.
        postMessage(
            buildUpdateDimensionsMessage(
                windowWidth, windowHeight,
                screenWidth, screenHeight,
                containerWidth, containerHeight,
                containerX, containerY,
                keyboardHeight,
            ),
        )
    }

    fun sendUserEvent(name: String, eventPayload: Map<String, Any>?, code: String) {
        postMessage(buildUserEventMessage(name, eventPayload, code))
    }

    private fun postMessage(payload: JSONObject) {
        ad.session.debug(
            "AdWebView: post-message",
            mapOf(
                "type" to payload.optString("type", "unknown"),
                "messageId" to ad.messageId,
                "payload" to payload.toString(),
            ),
        )
        val adServerUrl = ad.session.config.adServerUrl
        val script = "window.postMessage($payload, '$adServerUrl'); null"
        webView?.evaluateJavascript(script, null)
    }

    // ---------------------------------------------------------------------------
    // Message handling (iframe → native)
    // ---------------------------------------------------------------------------

    private fun handleMessage(json: String) {
        try {
            // Console intercept messages are routed to the debug channel
            // separately from the typed iframe-event protocol.
            val rawType = JSONObject(json).optString("type", "")
            if (rawType == "_console") {
                val obj = JSONObject(json)
                val data = obj.optJSONObject("data") ?: return
                val message = data.optString("message").takeIf { it.isNotEmpty() } ?: return
                val level = data.optString("level").ifEmpty { "log" }
                ad.session.debug("WebView: console.$level", message)
                return
            }

            val event = IframeEvent.parse(json) ?: return
            // Data-class toString() carries the typed payload (e.g. Event
            // shows `name` + `payload`; Click shows `url`, `target`, etc.)
            // so a single line is enough to identify what fired.
            ad.session.debug(
                "AdWebView: iframe-event",
                mapOf(
                    "type" to (event::class.simpleName ?: "unknown"),
                    "event" to event.toString(),
                ),
            )

            if (event is IframeEvent.Init && !initialized) {
                initialized = true
                sendUpdateIframe()
            }

            ad.handleIframeEvent(event)
        } catch (e: Exception) {
            ad.session.debug("AdWebView: malformed-message", mapOf("error" to (e.message ?: e.toString())))
            ad.session.reportError(e, "adwebview-handle-message")
        }
    }

    // ---------------------------------------------------------------------------
    // JavaScript bridge
    // ---------------------------------------------------------------------------

    private inner class BridgeInterface {
        @JavascriptInterface
        fun postMessage(json: String) {
            webView?.post { handleMessage(json) }
        }
    }

    companion object {
        /**
         * Bridge script with the expected ad-server origin baked in.
         * Anchoring to `adServerUrl` rather than `window.location.origin`
         * keeps origin validation correct even if the WebView ever ends
         * up navigated to a different page (defence-in-depth — the
         * navigation policy is supposed to prevent that).
         */
        internal fun bridgeScript(adServerUrl: String): String {
            val expectedOrigin = jsEscape(extractOrigin(adServerUrl) ?: adServerUrl)
            return """
            (function() {
                if (window.__kontextBridgeReady) return;
                window.__kontextBridgeReady = true;
                window.__kontextMsgQueue = [];
                var expectedOrigin = '$expectedOrigin';

                function postToNative(data) {
                    try {
                        var parsed = typeof data === 'string' ? JSON.parse(data) : data;
                        if (!parsed || !parsed.type || parsed.type.indexOf('-iframe') === -1) return;
                        if (window.kontextBridge) {
                            window.kontextBridge.postMessage(JSON.stringify(parsed));
                        } else {
                            window.__kontextMsgQueue.push(parsed);
                        }
                    } catch(e) {}
                }

                window.addEventListener('message', function(event) {
                    if (event.origin !== expectedOrigin) return;
                    postToNative(event.data);
                });

                // Flush any queued messages
                var queue = window.__kontextMsgQueue || [];
                window.__kontextMsgQueue = [];
                for (var i = 0; i < queue.length; i++) {
                    postToNative(queue[i]);
                }
            })();
            """
        }

        /** Returns `scheme://host[:port]` from a URL, or `null` if unparseable. */
        internal fun extractOrigin(url: String): String? = try {
            val u = java.net.URI(url)
            if (u.scheme.isNullOrBlank() || u.host.isNullOrBlank()) {
                null
            } else {
                "${u.scheme}://${u.host}${if (u.port > 0) ":${u.port}" else ""}"
            }
        } catch (_: Exception) { null }

        /** Minimal JS string escape for the bridge-script substitution. */
        private fun jsEscape(s: String): String =
            s.replace("\\", "\\\\").replace("'", "\\'")
    }
}
