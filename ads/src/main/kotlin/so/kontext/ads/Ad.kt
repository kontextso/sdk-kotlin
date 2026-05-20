package so.kontext.ads

import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import so.kontext.ads.internal.findActivityContext
import so.kontext.ads.model.AdEvent
import so.kontext.ads.model.Bid
import so.kontext.ads.model.ImpressionTrigger
import so.kontext.ads.ui.AdWebView
import so.kontext.ads.ui.InterstitialAdActivity
import so.kontext.ads.ui.iframe.IframeEvent
import so.kontext.kit.omsdk.OmSession

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
        session.debug(
            "Ad: iframe-event",
            mapOf(
                "type" to event::class.simpleName,
                "messageId" to messageId,
                "event" to event.toString(),
            ),
        )
        if (!dispatchInlineEvent(event)) dispatchComponentEvent(event)
    }

    /** Returns true if `event` was an inline iframe event and was handled. */
    private fun dispatchInlineEvent(event: IframeEvent): Boolean {
        when (event) {
            is IframeEvent.Init -> handleInit()
            is IframeEvent.Show -> { isVisible = true }
            is IframeEvent.Hide -> handleHide()
            is IframeEvent.Resize -> handleResize(event)
            is IframeEvent.Event -> handleAdEvent(event)
            is IframeEvent.Click -> handleClick(event)
            is IframeEvent.AdDone -> handleAdDone()
            is IframeEvent.Error -> handleError()
            else -> return false
        }
        return true
    }

    private fun dispatchComponentEvent(event: IframeEvent) {
        when (event) {
            is IframeEvent.OpenComponent -> handleOpenComponentEvent(event)
            is IframeEvent.CloseComponent -> handleCloseComponent()
            is IframeEvent.InitComponent -> handleInitComponent()
            is IframeEvent.AdDoneComponent -> handleAdDoneComponent()
            is IframeEvent.ErrorComponent -> handleErrorComponent(event)
            else -> Unit
        }
    }

    // Most lifecycle events carry bidId, populated once a matching bid
    // resolves in checkBid(). In production the iframe can't fire events
    // before the iframe URL is built (which requires a bid), so this is
    // generally non-null at this point — the null guard is defensive.
    private fun resolvedBidId(): java.util.UUID? = bid?.bidId

    private fun handleInit() {
        resolvedBidId()?.let { session.emitAdEvent(AdEvent.RenderStarted(bidId = it)) }
    }

    private fun handleHide() {
        isVisible = false
        height = 0f
    }

    private fun handleResize(event: IframeEvent.Resize) {
        height = event.height
        resolvedBidId()?.let {
            session.emitAdEvent(
                AdEvent.AdHeight(bidId = it, messageId = messageId, height = event.height),
            )
        }
    }

    private fun handleAdDone() {
        resolvedBidId()?.let { session.emitAdEvent(AdEvent.RenderCompleted(bidId = it)) }
        if (bid?.impressionTrigger == ImpressionTrigger.IMMEDIATE) {
            startOmSessionDelayed()
        }
    }

    private fun handleError() {
        session.emitAdEvent(AdEvent.Error(message = "iframe error", errCode = "iframe_error"))
    }

    private fun handleOpenComponentEvent(event: IframeEvent.OpenComponent) {
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

    private fun handleCloseComponent() {
        restoreBrightness()
        cancelModalTimeout()
        retireOmSession()
        onDismissModal?.invoke()
    }

    private fun handleInitComponent() {
        // Modal initialized — timeout is managed by InterstitialAdActivity
        cancelModalTimeout()
        session.debug("Ad: modal-initialized", mapOf("messageId" to messageId))
    }

    private fun handleAdDoneComponent() {
        // Modal Activity owns its own OM session (created on this event,
        // attached to the modal's own WebView). The inline WebView is in
        // the background and is NOT the right view to register with OMID.
        // See `InterstitialAdActivity.startModalOmSession()`.
        session.debug("Ad: modal-ad-done", mapOf("messageId" to messageId))
    }

    private fun handleErrorComponent(event: IframeEvent.ErrorComponent) {
        restoreBrightness()
        val message = event.message ?: "component error"
        val errCode = event.errorType ?: "component_error"
        session.emitAdEvent(AdEvent.Error(message = message, errCode = errCode))
        retireOmSession()
        onDismissModal?.invoke()
    }

    private fun handleAdEvent(event: IframeEvent.Event) {
        val resolvedBidId = bid?.bidId ?: return
        when (event.name) {
            "ad.viewed" -> emitAdViewed(resolvedBidId, event.payload)
            "ad.clicked" -> emitAdClicked(resolvedBidId, event.payload)
            "video.started" -> session.emitAdEvent(AdEvent.VideoStarted(bidId = resolvedBidId))
            "video.completed" -> session.emitAdEvent(AdEvent.VideoCompleted(bidId = resolvedBidId))
            "reward.granted" -> session.emitAdEvent(AdEvent.RewardGranted(bidId = resolvedBidId))
        }
    }

    private fun emitAdViewed(bidId: java.util.UUID, payload: Map<String, Any?>?) {
        // Required fields per the iframe protocol; if any is missing,
        // skip the emit rather than fabricating values.
        val content = payload?.get("content") as? String ?: return
        val msgId = payload["messageId"] as? String ?: return
        val format = payload["format"] as? String ?: return
        session.emitAdEvent(
            AdEvent.Viewed(
                bidId = bidId,
                content = content,
                messageId = msgId,
                format = format,
                // Revenue injection: enrich ad.viewed with bid revenue.
                revenue = bid?.revenue,
            ),
        )
    }

    private fun emitAdClicked(bidId: java.util.UUID, payload: Map<String, Any?>?) {
        val content = payload?.get("content") as? String ?: return
        val msgId = payload["messageId"] as? String ?: return
        val url = payload["url"] as? String ?: return
        val format = payload["format"] as? String ?: return
        val area = payload["area"] as? String ?: return
        session.emitAdEvent(
            AdEvent.Clicked(
                bidId = bidId,
                content = content,
                messageId = msgId,
                url = url,
                format = format,
                area = area,
            ),
        )
    }

    private fun handleClick(event: IframeEvent.Click) {
        val ctx = session.context ?: return
        val url = event.url ?: return
        val fallbackUrl = event.fallbackUrl
        val resolvedUrl = resolveAdUrl(url)
        session.debug(
            "Ad: click-handle",
            mapOf("target" to event.target.name, "url" to resolvedUrl, "fallbackUrl" to fallbackUrl),
        )

        // 1. target=in-app → In-App Browser (Chrome Custom Tabs).
        // Custom Tabs require an Activity context. Publishers commonly
        // pass `applicationContext` into createSession (to avoid leaks),
        // so we resolve an Activity in priority order:
        //   1. The WebView's rootView.context — its decor view's context
        //      is the host Activity, regardless of how the WebView was
        //      created. Always works when the ad is on-screen, which it
        //      is when a click comes in.
        //   2. `ActivityTracker.current()` — fallback for ads clicked
        //      after the WebView became detached (rare).
        //   3. `ctx` (session context) — final fallback; almost certainly
        //      not an Activity, so the next branch's ACTION_VIEW handles
        //      it via FLAG_ACTIVITY_NEW_TASK.
        if (event.target == IframeEvent.Target.IN_APP) {
            val tabCtx: android.content.Context =
                so.kontext.ads.internal.ActivityTracker.current()
                    ?: adWebView?.findActivityContext()
                    ?: ctx
            val openResult = so.kontext.kit.ui.InAppBrowserManager.open(tabCtx, resolvedUrl)
            if (openResult.isSuccess) {
                session.debug("Ad: click-in-app-opened", mapOf("url" to resolvedUrl))
                return
            }
            session.debug(
                "Ad: click-in-app-failed",
                mapOf("url" to resolvedUrl, "error" to openResult.exceptionOrNull()?.toString()),
            )
        }

        // 2. Direct URL open (system browser / deep link)
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(resolvedUrl))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            session.debug("Ad: click-action-view-opened", mapOf("url" to resolvedUrl))
            return
        } catch (e: Exception) {
            session.debug(
                "Ad: click-action-view-failed",
                mapOf("url" to resolvedUrl, "error" to e.toString()),
            )
            session.reportError(e, "ad-click-open-url", bidId = bid?.bidId)
        }

        // 3. Fallback URL (deep link failed / app not installed)
        if (fallbackUrl != null) {
            val resolvedFallback = resolveAdUrl(fallbackUrl)
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(resolvedFallback))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                session.debug("Ad: click-fallback-opened", mapOf("url" to resolvedFallback))
            } catch (e: Exception) {
                session.debug(
                    "Ad: click-fallback-failed",
                    mapOf("url" to resolvedFallback, "error" to e.toString()),
                )
                session.reportError(e, "ad-click-open-fallback-url", bidId = bid?.bidId)
            }
        } else {
            session.debug("Ad: click-no-fallback", null)
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

        // Register callback for modal events → routed back to handleIframeEvent.
        // OM session for the modal is owned by `InterstitialAdActivity` itself
        // (set on `ad-done-component-iframe`, attached to the modal's own
        // WebView). Pass the dependencies via static refs because `OmManager`
        // is not Parcelable.
        InterstitialAdActivity.modalEventCallback = { modalEvent ->
            handleIframeEvent(modalEvent)
        }
        InterstitialAdActivity.omManager = session.omManager
        InterstitialAdActivity.omCreativeType = currentBid.creativeType
        InterstitialAdActivity.impressionTrigger = currentBid.impressionTrigger

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

    /**
     * Pending dispose. When the InlineAd composable leaves composition we
     * don't destroy immediately — LazyColumn recycling re-mounts the same
     * Ad almost instantly. After [PENDING_DESTROY_DELAY_MS] without a
     * remount we treat it as a real removal and tear down (including the
     * OMID `finish` that fires `sessionFinish` to verification scripts).
     */
    private var pendingDestroyJob: kotlinx.coroutines.Job? = null

    private fun startOmSessionDelayed() {
        val webView = adWebView?.getWebView() ?: return
        val omManager = session.omManager ?: return
        if (omSession != null) return // session already running for this ad
        val creativeType = bid?.creativeType ?: return

        // OMID caches the WebView's measured size at registerAdView time;
        // creating the session before the first layout pass samples the
        // pre-resize 1×1 WebView and pins that geometry on the session
        // forever — verification scripts then never see a real ad and
        // skip `loaded` + `impression` events. Match v3 sdk-kotlin:
        // post() to main, then wait for the next pre-draw (layout pass
        // complete) before building the session.
        webView.post {
            val observer = webView.viewTreeObserver
            observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    webView.viewTreeObserver.removeOnPreDrawListener(this)
                    session.scopeOnMain.launch {
                        val newSession = omManager.createSession(webView, iframeUrl, creativeType)
                        omSession = newSession
                        session.debug(
                            "Ad: om-session-started",
                            mapOf("messageId" to messageId, "valid" to (newSession != null)),
                        )
                        // Display sessions use NATIVE impression owner — the SDK fires
                        // loaded() + impressionOccurred() so the JS verification script
                        // does NOT poll geometry and won't emit `notFound` on detach.
                        // Video sessions are JS-owned; do not fire natively.
                        if (newSession != null && creativeType == so.kontext.kit.omsdk.OmCreativeType.DISPLAY) {
                            newSession.loaded()
                            newSession.impressionOccurred()
                        }
                    }
                    return true
                }
            })
        }
    }

    private fun retireOmSession() {
        val active = omSession ?: return
        omSession = null
        active.retire()
        active.finish()
    }

    /**
     * Finishes the OMID session **synchronously**, while the WebView is
     * still attached to the view tree. Called from `InlineAd.onDispose`
     * BEFORE Compose detaches the AndroidView — the JS verification
     * script emits `sessionFinish` cleanly without polling geometry on
     * a detached view (which would produce a spurious `notFound`
     * geometryChange between the last valid geometryChange and
     * sessionFinish).
     *
     * The 1s WebView hold for verification-script flush is handled
     * inside [OmSession.finish].
     */
    internal fun finishOmSessionNow() {
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
        pendingDestroyJob?.cancel()
        pendingDestroyJob = null
        session.debug("Ad: destroy", mapOf("messageId" to messageId, "code" to code))
        restoreBrightness()
        cancelModalTimeout()
        retireOmSession()
        bidUpdateListener?.let { session.bidUpdateListeners.remove(it) }
        bidUpdateListener = null
        adWebView = null
        session.removeAd(this)
    }

    /**
     * Schedule [destroy] after [PENDING_DESTROY_DELAY_MS] unless
     * [cancelPendingDestroy] is called first. Used by `InlineAd`
     * composable's `onDispose` to distinguish LazyColumn recycling
     * (rapid dispose → remount) from a real removal (no remount).
     */
    internal fun schedulePendingDestroy() {
        if (destroyed) return
        pendingDestroyJob?.cancel()
        pendingDestroyJob = session.scopeOnMain.launch {
            kotlinx.coroutines.delay(PENDING_DESTROY_DELAY_MS)
            destroy()
        }
    }

    /**
     * Cancel any in-flight [schedulePendingDestroy] — called from
     * `InlineAd`'s `DisposableEffect` when it re-enters composition.
     */
    internal fun cancelPendingDestroy() {
        pendingDestroyJob?.cancel()
        pendingDestroyJob = null
    }

    private companion object {
        /** Grace window for LazyColumn recycle vs real removal. */
        private const val PENDING_DESTROY_DELAY_MS = 500L
    }

    private fun cancelModalTimeout() {
        modalTimeoutRunnable?.let { handler?.removeCallbacks(it) }
        modalTimeoutRunnable = null
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
