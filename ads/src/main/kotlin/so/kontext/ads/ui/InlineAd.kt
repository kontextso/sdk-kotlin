package so.kontext.ads.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.internal.AdsProperties
import so.kontext.ads.internal.data.mapper.toPublicAdEvent
import so.kontext.ads.internal.di.KontextDependencies
import so.kontext.ads.internal.ui.IFrameBridge
import so.kontext.ads.internal.ui.IFrameBridgeName
import so.kontext.ads.internal.ui.IFrameCommunicatorImpl
import so.kontext.ads.internal.ui.InlineAdPool
import so.kontext.ads.internal.ui.ModalAdActivity
import so.kontext.ads.internal.ui.model.AdDimensions
import so.kontext.ads.internal.ui.model.IFrameEvent
import so.kontext.ads.internal.utils.extension.launchCustomTab
import kotlin.math.roundToInt

@Composable
public fun InlineAd(
    config: AdConfig,
    modifier: Modifier = Modifier,
    onEvent: (AdEvent) -> Unit = {},
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
            setupWebView(
                webView = webView,
                config = config,
                onResize = { cssPx ->
                    if (cssPx != heightCssPx) {
                        heightCssPx = cssPx
                        InlineAdPool.updateHeight(adKey, cssPx)
                        webView.post { heightDp = cssPx.dp }
                    }
                },
                onClick = { url ->
                    context.launchCustomTab(config.adServerUrl + url)
                },
                onAdEvent = { event ->
                    onEvent(event)
                },
                onOpenModal = { modalUrl, timeout ->
                    val intent = ModalAdActivity.getMainActivityIntent(
                        context = context,
                        timeout = timeout,
                        modalUrl = modalUrl,
                        adServerUrl = config.adServerUrl,
                    )
                    context.startActivity(intent)
                },
            )
            webView.loadUrl(config.iFrameUrl)
        }
    }
    val webView = webViewPoolEntry.webView

    DisposableEffect(webView, adKey) {
        webView.onResume()

        onDispose {
            webView.onPause()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .onGloballyPositioned { layoutCoordinates ->
                reportNewDimensions(
                    webView = webView,
                    layoutCoordinates = layoutCoordinates,
                )
            },
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

@SuppressLint("SetJavaScriptEnabled")
@Suppress("LongParameterList", "CyclomaticComplexMethod")
private fun setupWebView(
    webView: WebView,
    config: AdConfig,
    onResize: (cssPx: Int) -> Unit,
    onClick: (url: String) -> Unit,
    onOpenModal: (url: String, timeout: Int) -> Unit,
    onAdEvent: (AdEvent) -> Unit,
) {
    val iFrameCommunicator = IFrameCommunicatorImpl(webView)

    with(webView.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
    }

    if (WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            IFrameBridge.DocumentStartScript.trimIndent(),
            setOf("*"),
        )
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            IFrameBridge.PosterStartScript.trimIndent(),
            setOf("*"),
        )
    }

    webView.webChromeClient = WebChromeClient()

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            iFrameCommunicator.sendUpdate(config)
        }
    }

    val iFrameBridge = IFrameBridge(
        eventParser = KontextDependencies.iFrameEventParser,
    ) { event ->
        when (event) {
            is IFrameEvent.InitIframe -> {
                iFrameCommunicator.sendUpdate(config)
            }
            is IFrameEvent.Resize -> {
                val cssPx = event.height.roundToInt()
                onResize(cssPx)
            }
            is IFrameEvent.Click -> {
                onClick(event.url)
            }
            is IFrameEvent.OpenComponent -> {
                val modalUrl = AdsProperties.modalIFrameUrl(
                    baseUrl = config.adServerUrl,
                    bidId = config.bid.bidId,
                    bidCode = config.bid.code,
                    messageId = config.messageId,
                    otherParams = config.otherParams,
                )
                onOpenModal(modalUrl, event.timeout)
            }
            is IFrameEvent.CallbackEvent -> {
                onAdEvent(event.toPublicAdEvent())
            }
            else -> {}
        }
    }

    webView.addJavascriptInterface(iFrameBridge, IFrameBridgeName)
}

private fun reportNewDimensions(
    webView: WebView,
    layoutCoordinates: LayoutCoordinates,
) {
    val iFrameCommunicator = IFrameCommunicatorImpl(webView)
    val windowSize = layoutCoordinates.findRootCoordinates().size
    val containerSize = layoutCoordinates.size
    val containerPosition = layoutCoordinates.positionInWindow()

    val geometry = AdDimensions(
        windowWidth = windowSize.width.toFloat(),
        windowHeight = windowSize.height.toFloat(),
        containerWidth = containerSize.width.toFloat(),
        containerHeight = containerSize.height.toFloat(),
        containerX = containerPosition.x,
        containerY = containerPosition.y,
    )
    iFrameCommunicator.sendDimensions(geometry)
}
