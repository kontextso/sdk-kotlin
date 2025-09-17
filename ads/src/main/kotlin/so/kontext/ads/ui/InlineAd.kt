package so.kontext.ads.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import so.kontext.ads.domain.AdConfig
import so.kontext.ads.internal.AdsProperties
import so.kontext.ads.internal.data.mapper.toPublicAdEvent
import so.kontext.ads.internal.di.KontextDependencies
import so.kontext.ads.internal.ui.IFrameBridge
import so.kontext.ads.internal.ui.IFrameBridgeName
import so.kontext.ads.internal.ui.IFrameCommunicator
import so.kontext.ads.internal.ui.IFrameCommunicatorImpl
import so.kontext.ads.internal.ui.InlineAdWebViewPool
import so.kontext.ads.internal.ui.ModalAdActivity
import so.kontext.ads.internal.ui.model.AdDimensions
import so.kontext.ads.internal.ui.model.IFrameEvent
import so.kontext.ads.internal.utils.extension.launchCustomTab
import kotlin.math.roundToInt

@Immutable
private data class LayoutSnapshot(
    val windowSize: IntSize,
    val containerSize: IntSize,
    val containerPos: Offset,
)

@Composable
public fun InlineAd(
    config: AdConfig,
    modifier: Modifier = Modifier,
    onEvent: (AdEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    val adKey = remember(config) { config.messageId }
    var heightCssPx by rememberSaveable("inline_ad_height_$adKey") {
        mutableIntStateOf(InlineAdWebViewPool.lastHeight(adKey))
    }
    val heightDp = remember(heightCssPx) {
        if (heightCssPx > 0) heightCssPx.dp else 0.dp
    }
    val webView = remember(adKey) {
        InlineAdWebViewPool.obtain(
            key = adKey,
            appContext = context.applicationContext,
        )
    }
    val iFrameCommunicator = remember(webView) { IFrameCommunicatorImpl(webView) }
    var lastLayoutSnapshot by remember { mutableStateOf<LayoutSnapshot?>(null) }
    var lastKeyboard by remember { mutableStateOf(0f) }

    LaunchedEffect(webView, config.iFrameUrl) {
        setupWebView(
            webView = webView,
            iFrameCommunicator = iFrameCommunicator,
            config = config,
            onResize = { cssPx ->
                if (cssPx != heightCssPx) {
                    heightCssPx = cssPx
                    InlineAdWebViewPool.updateHeight(adKey, cssPx)
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

    LaunchedEffect(imeInsets, density) {
        snapshotFlow { imeInsets.getBottom(density).toFloat() }
            .distinctUntilChanged()
            .collect { newHeight ->
                lastKeyboard = newHeight
            }
    }

    LaunchedEffect(webView) {
        while (isActive) {
            delay(200)
            lastLayoutSnapshot?.let { layoutSnapshot ->
                val adDimensions = AdDimensions(
                    windowWidth = layoutSnapshot.windowSize.width.toFloat(),
                    windowHeight = layoutSnapshot.windowSize.height.toFloat(),
                    containerWidth = layoutSnapshot.containerSize.width.toFloat(),
                    containerHeight = layoutSnapshot.containerSize.height.toFloat(),
                    containerX = layoutSnapshot.containerPos.x,
                    containerY = layoutSnapshot.containerPos.y,
                    keyboardHeight = lastKeyboard,
                )
                iFrameCommunicator.sendDimensions(adDimensions)
            }
        }
    }

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
            .onGloballyPositioned { coords ->
                val snap = LayoutSnapshot(
                    windowSize = coords.findRootCoordinates().size,
                    containerSize = coords.size,
                    containerPos = coords.positionInWindow(),
                )
                lastLayoutSnapshot = snap
            },
    ) {
        AndroidView(
            factory = {
                // Ensure it has no previous parent before attaching here
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("LongParameterList", "CyclomaticComplexMethod")
private fun setupWebView(
    webView: WebView,
    iFrameCommunicator: IFrameCommunicator,
    config: AdConfig,
    onResize: (cssPx: Int) -> Unit,
    onClick: (url: String) -> Unit,
    onOpenModal: (url: String, timeout: Int) -> Unit,
    onAdEvent: (AdEvent) -> Unit,
) {
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
