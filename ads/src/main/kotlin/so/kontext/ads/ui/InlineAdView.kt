package so.kontext.ads.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import so.kontext.ads.Ad
import so.kontext.ads.Constants
import so.kontext.ads.Session
import so.kontext.ads.model.AdOptions
import kotlin.math.roundToInt

/**
 * Traditional Android View for rendering inline ads (XML layouts /
 * RecyclerView / any ViewGroup-based UI).
 *
 * Hosts the pooled ad [android.webkit.WebView] **directly** as a child (no
 * Compose layer): the WebView is laid out by the standard Android layout
 * system, which reliably re-lays-out recycled RecyclerView rows — a
 * `ComposeView` wrapper does not (a recycled one re-composes but never fires
 * `onGloballyPositioned`, so its content stays 0-width and the iframe runs
 * away). Mirrors v2.0.1's rendering semantics: one pooled WebView per bid,
 * height driven by the iframe's `resize`, and a 200 ms dimension heartbeat.
 *
 * The ad's state ([Ad.iframeUrl], [Ad.height]) is owned by the [Session] and
 * **polled** on the heartbeat — not observed via `snapshotFlow`, which only
 * delivers updates when a Compose Recomposer pumps `sendApplyNotifications()`,
 * and there is no composition in this View path.
 *
 * Usage:
 * ```kotlin
 * val adView = InlineAdView(context)
 * adView.bind(messageId = "a1", session = session)
 * parentLayout.addView(adView)
 * ```
 */
public class InlineAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * Invoked when the ad's height changes to a non-zero value (CSS px, 1:1
     * with dp). The view already resizes itself; this is a convenience hook
     * for hosts that need to react (e.g. a RecyclerView item re-measuring).
     */
    public var onHeightChange: ((Float) -> Unit)? = null

    private var ad: Ad? = null
    private var entry: WebViewPool.Entry? = null
    private var attachedKey: String? = null
    private var lastHeightPx: Int = -1
    private var scope: CoroutineScope? = null

    /** Binds the ad slot to [messageId] in [session] and renders its ad. */
    public fun bind(messageId: String, session: Session, code: String? = null, theme: String? = null) {
        val resolvedCode = code ?: Constants.DEFAULT_PLACEMENT_CODE
        val newAd = session.createAd(messageId, AdOptions(code = resolvedCode, theme = theme))
        if (newAd === ad) return
        teardown()
        ad = newAd
        if (isAttachedToWindow) {
            start()
            resumeOm()
        }
    }

    /**
     * Clears the ad slot — stops the loop and detaches the WebView (returning
     * it to the pool). The v4 equivalent of v2.0.1's `setConfig(null)`. Call
     * for a recycled / no-longer-active slot.
     */
    public fun unbind() {
        teardown()
        ad = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ad != null && scope == null) start()
        // Resume the WebView on every re-attach. On a RecyclerView view-cache
        // re-attach the pool key is unchanged, so syncAd skips its attach
        // branch — without this the WebView stays paused from the onDetached →
        // stop() → onPause() and renders blank (the white-ad bug).
        entry?.webView?.onResume()
        resumeOm()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        retireOm()
        stop()
    }

    private fun start() {
        val ad = ad ?: return
        val s = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        scope = s
        s.launch {
            while (isActive) {
                syncAd(ad)
                delay(Constants.DIMENSION_REPORT_INTERVAL_MS)
            }
        }
    }

    private fun stop() {
        scope?.cancel()
        scope = null
        entry?.webView?.onPause()
    }

    private fun teardown() {
        retireOm()
        stop()
        detachWebView()
    }

    // OMID viewability follows the on-screen lifecycle. The session *start* is
    // automatic (Ad.handleAdDone on `ad-done`); here we retire when the row
    // goes off-screen / its slot is cleared, and cancel-or-restart when it
    // comes back — so a quick scroll-off-and-back keeps one continuous session.
    private fun resumeOm() {
        // No-ops unless a session exists / the ad ever started OMID, so safe
        // to call unconditionally — OMID presence is decided per bid server-side.
        ad?.cancelOmSessionFinish()
        ad?.restartOmSessionIfRetired()
    }

    private fun retireOm() {
        ad?.scheduleOmSessionFinish()
    }

    /** One heartbeat tick: attach/detach the WebView, size it, post dimensions. */
    private fun syncAd(ad: Ad) {
        val url = ad.iframeUrl
        if (url == null || ad.destroyed) {
            detachWebView()
            return
        }

        val key = "${ad.messageId}-${url.hashCode()}"
        if (key != attachedKey) {
            detachWebView()
            val e = WebViewPool.obtain(key, context.applicationContext, ad) { it.adWebView.load() }
            entry = e
            attachedKey = key
            val wv = e.webView
            (wv.parent as? ViewGroup)?.removeView(wv)
            addView(wv, LayoutParams(LayoutParams.MATCH_PARENT, heightToPx(ad.height)))
            wv.onResume()
        }

        applyHeight(ad.height)
        sendDimensions()
    }

    private fun detachWebView() {
        val e = entry ?: return
        entry = null
        attachedKey = null
        lastHeightPx = -1
        e.webView.onPause()
        (e.webView.parent as? ViewGroup)?.removeView(e.webView)
    }

    private fun heightToPx(cssPx: Float): Int =
        (cssPx * resources.displayMetrics.density).roundToInt().coerceAtLeast(0)

    private fun applyHeight(cssPx: Float) {
        val wv = entry?.webView ?: return
        val hPx = heightToPx(cssPx)
        if (hPx == lastHeightPx) return
        lastHeightPx = hPx
        wv.layoutParams = (wv.layoutParams as? LayoutParams ?: LayoutParams(LayoutParams.MATCH_PARENT, hPx))
            .also { it.height = hPx }
        if (cssPx > 0f) onHeightChange?.invoke(cssPx)
    }

    private fun sendDimensions() {
        val e = entry ?: return
        val wv = e.webView
        if (!wv.isAttachedToWindow || wv.width <= 0) return
        val location = IntArray(2).also { wv.getLocationInWindow(it) }
        val rootView = wv.rootView
        val dm = resources.displayMetrics
        val imeInset = ViewCompat.getRootWindowInsets(wv)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.toFloat() ?: 0f

        e.adWebView.sendDimensions(
            windowWidth = rootView.width.toFloat(),
            windowHeight = rootView.height.toFloat(),
            screenWidth = dm.widthPixels.toFloat(),
            screenHeight = dm.heightPixels.toFloat(),
            containerWidth = wv.width.toFloat(),
            containerHeight = wv.height.toFloat(),
            containerX = location[0].toFloat(),
            containerY = location[1].toFloat(),
            keyboardHeight = imeInset,
        )
    }
}
