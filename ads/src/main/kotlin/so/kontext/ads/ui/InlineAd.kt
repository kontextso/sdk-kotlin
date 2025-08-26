package so.kontext.ads.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.internal.data.dto.request.UpdateIFrameDataDto
import so.kontext.ads.internal.data.dto.request.UpdateIFrameRequest
import so.kontext.ads.internal.data.mapper.toDto
import so.kontext.ads.internal.ui.IFrameBridge
import so.kontext.ads.internal.ui.InlineAdPool
import so.kontext.ads.internal.ui.model.InlineAdEvent
import kotlin.math.roundToInt

private const val IFrameBridgeName = "AndroidBridge"

@SuppressLint("SetJavaScriptEnabled")
@Suppress("CyclomaticComplexMethod")
@Composable
public fun InlineAd(
    config: AdConfig,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val adKey = remember(config) { config.messageId }

    var heightCssPx by rememberSaveable("inline_ad_height_$adKey") {
        mutableIntStateOf(InlineAdPool.lastHeight(adKey))
    }
    var heightDp by remember(adKey, heightCssPx) {
        mutableStateOf(if (heightCssPx > 0) heightCssPx.dp else 0.dp)
    }

    val webViewPoolEntry = remember(adKey) {
        InlineAdPool.obtain(
            key = adKey,
            appContext = context.applicationContext,
        ) { webView ->
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            if (WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    doc_start_js.trimIndent(),
                    setOf("*"),
                )
            }

            webView.alpha = 1f
            webView.webViewClient = object : WebViewClient() {
                override fun onPageCommitVisible(view: WebView, url: String) {
                    if (view.alpha != 1f) view.alpha = 1f
                }
                override fun onPageFinished(view: WebView, url: String) {
                    // Proactively send update in case init was missed
                    sendUpdateIframe(view, config)
                    if (view.alpha != 1f) view.alpha = 1f
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest,
                ): Boolean {
                    if (request.isForMainFrame) {
                        launchCustomTab(context, request.url.toString())
                        return true
                    }
                    return false
                }

                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    launchCustomTab(context, url)
                    return true
                }
            }

            webView.addJavascriptInterface(
                IFrameBridge { event ->
                    when (event) {
                        is InlineAdEvent.InitIframe -> {
                            sendUpdateIframe(webView, config)
                        }
                        is InlineAdEvent.ResizeIframe -> {
                            val cssPx = event.height.roundToInt()
                            if (cssPx != heightCssPx) {
                                heightCssPx = cssPx
                                InlineAdPool.updateHeight(adKey, cssPx)
                                webView.post { heightDp = cssPx.dp }
                            }
                        }
                        is InlineAdEvent.ClickIframe -> {
                            runCatching { launchCustomTab(context, event.url) }
                        }
                        is InlineAdEvent.ShowIframe -> {}
                        is InlineAdEvent.HideIframe -> {}
                        is InlineAdEvent.AdDoneIframe -> {}
                        is InlineAdEvent.Error -> {}
                        is InlineAdEvent.Unknown -> {}
                        is InlineAdEvent.ViewIframe -> {}
                        is InlineAdEvent.CloseComponentIframe -> {}
                        is InlineAdEvent.ErrorComponentIframe -> {}
                        is InlineAdEvent.InitComponentIframe -> {}
                        is InlineAdEvent.OpenComponentIframe -> {}
                    }
                },
                IFrameBridgeName,
            )
            webView.loadUrl(config.url)
        }
    }
    val webView = webViewPoolEntry.webView

    // Attach lifecycle for timers; do NOT destroy on detach
    DisposableEffect(webView, adKey) {
        webView.onResume()
        webView.resumeTimers()
        if (webView.alpha != 1f) webView.alpha = 1f

        sendUpdateIframe(webView, config)
        onDispose {
            webView.onPause()
            webView.pauseTimers()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp),
    ) {
        AndroidView(
            factory = {
                // Ensure it has no previous parent before attaching here
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView
            },
            update = {
                if (it.alpha != 1f) it.alpha = 1f
                if (heightCssPx > 0 && heightDp != heightCssPx.dp) {
                    heightDp = heightCssPx.dp
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

private fun launchCustomTab(context: Context, url: String) {
    val customTab = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        .build()
    customTab.launchUrl(context, url.toUri())
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
