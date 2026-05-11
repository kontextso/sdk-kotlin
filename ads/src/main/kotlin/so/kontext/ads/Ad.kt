package so.kontext.ads

import so.kontext.ads.model.AdEvent
import so.kontext.ads.model.Bid
import so.kontext.ads.model.ImpressionTrigger
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import so.kontext.kit.omsdk.OmSession
import so.kontext.ads.ui.AdWebView
import so.kontext.ads.ui.InterstitialAdActivity
import so.kontext.ads.ui.iframe.IframeEvent

/**
 * Represents a single ad for a specific message.
 *
 * Subscribes to bid updates from the session. When a bid matching this ad's code
 * and messageId becomes available, the [iframeUrl] is resolved and the ad can be rendered.
 *
 * Observable via Compose state: [iframeUrl], [height], [isVisible], [destroyed].
 */
public class Ad internal constructor(
    public val messageId: String,
    public val code: String,
    public val theme: String?,
    internal val session: Session,
) {
    public var iframeUrl: String? by mutableStateOf(null)
        internal set

    public var height: Float by mutableFloatStateOf(0f)
        internal set

    public var isVisible: Boolean by mutableStateOf(false)
        internal set

    public var destroyed: Boolean by mutableStateOf(false)
        private set

    public var modalUrl: String? by mutableStateOf(null)
        internal set

    public var onRequestModal: ((String, Int) -> Unit)? = null
    public var onDismissModal: (() -> Unit)? = null

    private var bidUpdateListener: (() -> Unit)? = null

    /** Resolved bid for this ad, set by `checkBid` once a matching bid surfaces. */
    internal var bid: Bid? = null
    private val handler: Handler? = try { Handler(Looper.getMainLooper()) } catch (_: Exception) { null }
    private var modalTimeoutRunnable: Runnable? = null
    private var savedBrightness: Double? = null

    /** The AdWebView bound to this ad, set by the UI layer. */
    internal var adWebView: AdWebView? = null

    init {
        val listener: () -> Unit = { checkBid() }
        bidUpdateListener = listener
        session.bidUpdateListeners.add(listener)
        checkBid()
        session.debug(
            "Ad: mount",
            mapOf("messageId" to messageId, "code" to code, "theme" to theme),
        )
    }

    // ---------------------------------------------------------------------------
    // Bid Resolution
    // ---------------------------------------------------------------------------

    private fun checkBid() {
        if (destroyed || iframeUrl != null) return

        val matchedBid = session.getBid(messageId, code) ?: return
        session.debug(
            "Ad: bid-resolved",
            mapOf("messageId" to messageId, "bidId" to matchedBid.bidId.toString()),
        )
        bid = matchedBid

        val adServerUrl = session.config.adServerUrl
        val urlBuilder = StringBuilder("$adServerUrl/api/frame/${matchedBid.bidId}")
        urlBuilder.append("?code=").append(encode(code))
        urlBuilder.append("&messageId=").append(encode(messageId))
        urlBuilder.append("&sdk=").append(SDKInfo.NAME)
        if (theme != null) {
            urlBuilder.append("&theme=").append(encode(theme))
        }

        iframeUrl = urlBuilder.toString()
    }

    // ---------------------------------------------------------------------------
    // Iframe Event Handling
    // ---------------------------------------------------------------------------

    internal fun handleIframeEvent(event: IframeEvent) {
        if (destroyed) return

        session.debug("Ad: iframe-event", mapOf("type" to event::class.simpleName, "messageId" to messageId))

        // Most lifecycle events carry bidId, populated once a matching bid
        // resolves in checkBid(). In production the iframe can't fire events
        // before the iframe URL is built (which requires a bid), so this is
        // generally non-null at this point — the null guard is defensive.
        val resolvedBidId = bid?.bidId

        when (event) {
            // --- Inline iframe events ---
            is IframeEvent.Init -> resolvedBidId?.let {
                session.emitAdEvent(AdEvent.RenderStarted(bidId = it))
            }

            is IframeEvent.Show -> { isVisible = true }
            is IframeEvent.Hide -> {
                isVisible = false
                height = 0f
            }
            is IframeEvent.Resize -> {
                height = event.height
                resolvedBidId?.let {
                    session.emitAdEvent(
                        AdEvent.AdHeight(bidId = it, messageId = messageId, height = event.height),
                    )
                }
            }
            is IframeEvent.Event -> handleAdEvent(event)
            is IframeEvent.Click -> handleClick(event)
            is IframeEvent.AdDone -> {
                resolvedBidId?.let {
                    session.emitAdEvent(AdEvent.RenderCompleted(bidId = it))
                }
                if (bid?.impressionTrigger == ImpressionTrigger.IMMEDIATE) {
                    startOmSessionDelayed()
                }
            }
            is IframeEvent.Error ->
                session.emitAdEvent(AdEvent.Error(message = "iframe error", errCode = "iframe_error"))

            // --- Component/modal iframe events ---
            is IframeEvent.OpenComponent -> {
                // Brightness increase for interstitials (A/B test).
                // Server's brightnessDelta is in [-1, 1] (iframe protocol);
                // KontextKit's get/set use 0–100, so scale.
                val brightnessDelta = event.brightnessDelta
                if (brightnessDelta != null && brightnessDelta != 0.0) {
                    val ctx = session.context
                    if (ctx != null) {
                        savedBrightness = so.kontext.kit.deviceinfo.BrightnessManager.get(ctx)
                        so.kontext.kit.deviceinfo.BrightnessManager.set(
                            ctx,
                            (savedBrightness!! + brightnessDelta * 100.0).coerceIn(0.0, 100.0),
                        )
                    }
                }
                handleOpenComponent(event)
            }
            is IframeEvent.CloseComponent -> {
                restoreBrightness()
                cancelModalTimeout()
                retireOmSession()
                onDismissModal?.invoke()
            }
            is IframeEvent.InitComponent -> {
                // Modal initialized — timeout is managed by InterstitialAdActivity
                cancelModalTimeout()
                session.debug("Ad: modal-initialized", mapOf("messageId" to messageId))
            }
            is IframeEvent.AdDoneComponent -> {
                // Interstitial ad rendered — start OM session for modal
                startOmSessionDelayed()
                session.debug("Ad: modal-ad-done", mapOf("messageId" to messageId))
            }
            is IframeEvent.ErrorComponent -> {
                restoreBrightness()
                val message = event.message ?: "component error"
                val errCode = event.errorType ?: "component_error"
                session.emitAdEvent(AdEvent.Error(message = message, errCode = errCode))
                retireOmSession()
                onDismissModal?.invoke()
            }
        }
    }

    private fun handleAdEvent(event: IframeEvent.Event) {
        val name = event.name
        val payload = event.payload
        val resolvedBidId = bid?.bidId ?: return

        when (name) {
            "ad.viewed" -> {
                // Required fields per the iframe protocol; if any is missing,
                // skip the emit rather than fabricating values.
                val content = payload?.get("content") as? String ?: return
                val msgId = payload["messageId"] as? String ?: return
                val format = payload["format"] as? String ?: return
                session.emitAdEvent(
                    AdEvent.Viewed(
                        bidId = resolvedBidId,
                        content = content,
                        messageId = msgId,
                        format = format,
                        // Revenue injection: enrich ad.viewed with bid revenue.
                        revenue = bid?.revenue,
                    ),
                )
            }
            "ad.clicked" -> {
                val content = payload?.get("content") as? String ?: return
                val msgId = payload["messageId"] as? String ?: return
                val url = payload["url"] as? String ?: return
                val format = payload["format"] as? String ?: return
                val area = payload["area"] as? String ?: return
                session.emitAdEvent(
                    AdEvent.Clicked(
                        bidId = resolvedBidId,
                        content = content,
                        messageId = msgId,
                        url = url,
                        format = format,
                        area = area,
                    ),
                )
            }
            "video.started" -> session.emitAdEvent(AdEvent.VideoStarted(bidId = resolvedBidId))
            "video.completed" -> session.emitAdEvent(AdEvent.VideoCompleted(bidId = resolvedBidId))
            "reward.granted" -> session.emitAdEvent(AdEvent.RewardGranted(bidId = resolvedBidId))
        }
    }

    private fun handleClick(event: IframeEvent.Click) {
        val ctx = session.context ?: return
        val url = event.url ?: return
        val fallbackUrl = event.fallbackUrl

        val resolvedUrl = resolveAdUrl(url)

        // 1. target=in-app → In-App Browser (Chrome Custom Tabs).
        // On any failure (bad scheme, no Activity), fall through to the
        // direct ACTION_VIEW path below — that intent has its own error
        // handling and may succeed where Custom Tabs couldn't.
        if (event.target == IframeEvent.Target.IN_APP) {
            val openResult = so.kontext.kit.ui.InAppBrowserManager.open(ctx, resolvedUrl)
            if (openResult.isSuccess) return
        }

        // 2. Direct URL open (system browser / deep link)
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(resolvedUrl))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            return
        } catch (e: Exception) {
            session.reportError(e, "ad-click-open-url", bidId = bid?.bidId)
        }

        // 3. Fallback URL (deep link failed / app not installed)
        if (fallbackUrl != null) {
            val resolvedFallback = resolveAdUrl(fallbackUrl)
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(resolvedFallback))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            } catch (e: Exception) {
                session.reportError(e, "ad-click-open-fallback-url", bidId = bid?.bidId)
            }
        }
    }

    /**
     * Resolves a potentially relative ad URL against the ad server base URL.
     * Only prepends adServerUrl for server-relative paths (starting with `/`).
     * Absolute URLs and custom schemes (amazon://, fb://) pass through unchanged.
     */
    private fun resolveAdUrl(url: String): String =
        so.kontext.ads.utils.resolveAdUrl(url, session.config.adServerUrl)

    @Suppress("UNCHECKED_CAST")
    private fun handleOpenComponent(event: IframeEvent.OpenComponent) {
        val timeoutMs = event.timeout
        val currentBid = bid ?: return
        val adServerUrl = session.config.adServerUrl

        // Build modal URL with componentParams (KON-1566)
        val modalPath = "/api/modal/${currentBid.bidId}"
        val urlBuilder = StringBuilder("$adServerUrl$modalPath")
        urlBuilder.append("?code=").append(encode(code))
        urlBuilder.append("&messageId=").append(encode(messageId))
        urlBuilder.append("&sdk=").append(SDKInfo.NAME)

        val componentParams = event.componentParams
        if (componentParams != null) {
            try {
                val paramsJson = org.json.JSONObject(componentParams as Map<*, *>).toString()
                urlBuilder.append("&componentParams=").append(encode(paramsJson))
            } catch (e: Exception) {
                session.reportError(e, "ad-encode-component-params", bidId = currentBid.bidId)
            }
        }

        modalUrl = urlBuilder.toString()

        // Start OM impression for component trigger
        if (currentBid.impressionTrigger == ImpressionTrigger.COMPONENT) {
            startOmSessionDelayed()
        }

        // Register callback for modal events → routed back to handleIframeEvent
        InterstitialAdActivity.modalEventCallback = { modalEvent ->
            handleIframeEvent(modalEvent)
        }

        // Launch modal Activity
        val ctx = session.context
        if (ctx != null) {
            val intent = InterstitialAdActivity.getIntent(
                context = ctx,
                url = modalUrl!!,
                adServerUrl = adServerUrl,
                timeoutMs = timeoutMs,
            )
            ctx.startActivity(intent)
        }

        onRequestModal?.invoke(modalUrl!!, timeoutMs)
    }

    // ---------------------------------------------------------------------------
    // OM SDK — delegates to OmManager which tracks sessions per WebView
    // ---------------------------------------------------------------------------

    // ---------------------------------------------------------------------------
    // Brightness
    // ---------------------------------------------------------------------------

    private fun restoreBrightness() {
        val saved = savedBrightness ?: return
        savedBrightness = null
        val ctx = session.context ?: return
        so.kontext.kit.deviceinfo.BrightnessManager.set(ctx, saved)
    }

    // ---------------------------------------------------------------------------
    // OM SDK
    // ---------------------------------------------------------------------------

    /** OMID session owned by this Ad. Lives from `ad-done-iframe` to teardown. */
    private var omSession: OmSession? = null

    private fun startOmSessionDelayed() {
        val webView = adWebView?.getWebView() ?: return
        val omManager = session.omManager ?: return
        if (omSession != null) return // session already running for this ad
        val creativeType = bid?.creativeType ?: return

        // createSession suspends for the 50 ms geometry-stability window
        // and starts the session before returning. Coroutine launches on
        // Main so the OMID JS layer + WebView reflection happen on the UI
        // thread the OMID native SDK expects.
        session.scopeOnMain.launch {
            val newSession = omManager.createSession(webView, iframeUrl, creativeType)
            omSession = newSession
            session.debug("Ad: om-session-started", mapOf("messageId" to messageId, "valid" to (newSession != null)))
        }
    }

    private fun retireOmSession() {
        val active = omSession ?: return
        omSession = null
        active.retire()
        active.finish()
    }

    // ---------------------------------------------------------------------------
    // User Events
    // ---------------------------------------------------------------------------

    internal fun sendUserEvent(name: String, payload: Map<String, Any>?, code: String) {
        adWebView?.sendUserEvent(name, payload, code)
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    public fun destroy() {
        if (destroyed) return
        destroyed = true
        session.debug("Ad: destroy", mapOf("messageId" to messageId, "code" to code))
        restoreBrightness()
        cancelModalTimeout()
        retireOmSession()
        bidUpdateListener?.let { session.bidUpdateListeners.remove(it) }
        bidUpdateListener = null
        adWebView = null
        session.removeAd(this)
    }

    private fun cancelModalTimeout() {
        modalTimeoutRunnable?.let { handler?.removeCallbacks(it) }
        modalTimeoutRunnable = null
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
