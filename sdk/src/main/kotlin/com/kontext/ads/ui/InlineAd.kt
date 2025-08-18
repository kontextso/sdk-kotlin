package com.kontext.ads.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import com.kontext.ads.domain.AdConfig
import com.kontext.ads.internal.data.dto.request.UpdateIFrameDataDto
import com.kontext.ads.internal.data.dto.request.UpdateIFrameRequest
import com.kontext.ads.internal.data.mapper.toDto
import com.kontext.ads.internal.ui.IFrameBridge
import com.kontext.ads.internal.ui.model.InlineAdEvent
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language

private const val IFrameBridgeName = "AndroidBridge"

@SuppressLint("SetJavaScriptEnabled")
@Composable
public fun InlineAdView(
    config: AdConfig,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var heightDp by remember { mutableStateOf(1.dp) }
    var isVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp),
    ) {
        AndroidView(
            factory = {
                WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    if (WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(
                            this,
                            doc_start_js.trimIndent(),
                            setOf("*"),
                        )
                    }

                    alpha = 0f
                    webViewClient = object : WebViewClient() {
                        override fun onPageCommitVisible(view: WebView, url: String) {
                            if (!isVisible) {
                                isVisible = true
                                view.animate().alpha(1f).setDuration(150).start()
                            }
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            // Proactively send update in case init was missed
                            sendUpdateIframe(view, config)
                        }
                    }

                    addJavascriptInterface(
                        IFrameBridge { event ->
                            when (event) {
                                is InlineAdEvent.InitIframe -> {
                                    sendUpdateIframe(this, config)
                                }

                                is InlineAdEvent.ShowIframe -> {
                                }

                                is InlineAdEvent.HideIframe -> {
                                }

                                is InlineAdEvent.ResizeIframe -> {
                                    val cssPx = event.height
                                    this.post { heightDp = cssPx.dp }
                                }

                                is InlineAdEvent.ClickIframe -> {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }

                                else -> {}
                            }
                        },
                        IFrameBridgeName,
                    )
                    loadUrl(config.url)
                }
            },
            update = { webView ->
                if (webView.url != config.url) {
                    webView.loadUrl(config.url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun sendUpdateIframe(webView: WebView, config: AdConfig) {
    val updatePayload = UpdateIFrameRequest(
        type = "update-iframe",
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

@Language("JavaScript")
private const val doc_start_js = """
(function() {
    if (window.__androidBridgeInstalled) return;
    window.__androidBridgeInstalled = true;

    function forward(data) {
        try {
            $IFrameBridgeName.onMessage(JSON.stringify(data));
        } catch (e) {}
    }

    const _post = window.postMessage.bind(window);
    window.postMessage = function(data, targetOrigin) {
        forward(data);
        return _post(data, targetOrigin);
    };

    window.addEventListener('message', function(e) {
        forward(e.data);
    }, true);
})();
"""
