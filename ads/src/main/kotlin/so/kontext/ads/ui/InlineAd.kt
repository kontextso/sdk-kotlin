package so.kontext.ads.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.internal.AdsProperties
import so.kontext.ads.internal.ui.IFrameBridge
import so.kontext.ads.internal.ui.InlineAdPool
import so.kontext.ads.internal.ui.doc_start_js
import so.kontext.ads.internal.ui.model.InlineAdEvent
import so.kontext.ads.internal.ui.sendUpdateIframe
import so.kontext.ads.internal.utils.extension.launchCustomTab
import kotlin.math.roundToInt

internal const val IFrameBridgeName = "AndroidBridge"

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

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Proactively send update in case init was missed
                    sendUpdateIframe(view, config)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest,
                ): Boolean {
                    if (request.isForMainFrame) {
                        context.launchCustomTab(request.url.toString())
                        return true
                    }
                    return false
                }

                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    context.launchCustomTab(url)
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
                            runCatching {
                                context.launchCustomTab(event.url)
                            }
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
                        is InlineAdEvent.OpenComponentIframe -> {
                            val modalUrl = AdsProperties.iFrameUrl(
                                baseUrl = config.adServerUrl,
                                bidId = config.bid.bidId,
                                bidCode = config.bid.code,
                                messageId = config.messageId,
                                component = "modal",
                                theme = "dark", // TODO
                            )
                            val intent = ModalAdActivity.getMainActivityIntent(
                                context = context,
                                timeout = event.timeout,
                                url = modalUrl,
                            )
                            context.startActivity(intent)
                        }
                    }
                },
                IFrameBridgeName,
            )
            webView.loadUrl(config.iFrameUrl)
        }
    }
    val webView = webViewPoolEntry.webView

    // Attach lifecycle for timers; do NOT destroy on detach
    DisposableEffect(webView, adKey) {
        webView.onResume()
        webView.resumeTimers()

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
                if (heightCssPx > 0 && heightDp != heightCssPx.dp) {
                    heightDp = heightCssPx.dp
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
