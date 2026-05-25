package so.kontext.ads.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import so.kontext.ads.Ad
import so.kontext.ads.Constants
import so.kontext.ads.Session
import so.kontext.ads.model.AdOptions

/**
 * Traditional Android View for rendering inline ads. For use in XML layouts
 * or ViewGroup-based UIs.
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

    private var ad: Ad? = null
    private var adWebView: AdWebView? = null
    private var webView: WebView? = null

    public var onHeightChange: ((Float) -> Unit)? = null

    public fun bind(messageId: String, session: Session, code: String? = null, theme: String? = null) {
        val newAd = session.createAd(messageId, AdOptions(code = code ?: Constants.DEFAULT_PLACEMENT_CODE, theme = theme))
        this.ad = newAd

        // Register bid update listener to detect when iframeUrl resolves
        val listener: () -> Unit = {
            post { setupWebViewIfNeeded() }
        }
        session.bidUpdateListeners.add(listener)

        // Also check immediately
        setupWebViewIfNeeded()

        // Start dimension reporting
        startDimensionReporting()
    }

    private fun setupWebViewIfNeeded() {
        val currentAd = ad ?: return
        currentAd.iframeUrl ?: return
        if (adWebView != null) return

        val awv = AdWebView(ad = currentAd)
        adWebView = awv

        // baseAdSetup enables JavaScript + DOM storage and injects the
        // document-start bridge / OMID / viewport scripts. Without it the
        // WebView loads the iframe URL but runs no JS at all, so the iframe
        // never posts `init-iframe` and the ad stays blank ("stuck on
        // loading"). The Compose path gets this via WebViewPool.obtain();
        // the View path has to apply it explicitly on its own WebView.
        val wv = WebView(context).apply {
            baseAdSetup(context.applicationContext, currentAd.session.config.adServerUrl)
        }
        webView = wv
        awv.setupWebView(wv)
        // Start at 0 height but VISIBLE — never GONE. A GONE WebView is 0×0
        // and is never measured, so its renderer can't lay out the iframe
        // and the iframe never emits resize-iframe / show-iframe — a
        // deadlock where the ad would stay hidden forever. Keeping it
        // visible at full width and 0 height lets the web content lay out
        // and report its real height, exactly like the Compose InlineAd.
        addView(wv, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        awv.load()

        // Observe height changes via polling and drive the View height.
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        lifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val currentHeight = currentAd.height
                val isVisible = currentAd.isVisible

                post {
                    val targetPx = if (isVisible && currentHeight > 0f) {
                        (currentHeight * resources.displayMetrics.density).toInt()
                    } else {
                        0
                    }
                    val lp = wv.layoutParams
                    if (lp != null && lp.height != targetPx) {
                        lp.height = targetPx
                        wv.layoutParams = lp
                        if (targetPx > 0) onHeightChange?.invoke(currentHeight)
                    }
                }

                delay(100)
            }
        }
    }

    private fun startDimensionReporting() {
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        lifecycleOwner.lifecycleScope.launch {
            delay(500) // initial delay
            while (isActive) {
                adWebView?.let { awv ->
                    // Window = visible app viewport (no system bars).
                    // Screen = full display. They differ in multi-window /
                    // split-screen / foldables; sending both lets the iframe
                    // distinguish "available to ad" from "device capability".
                    val windowRect = Rect()
                    getWindowVisibleDisplayFrame(windowRect)
                    val displayMetrics = resources.displayMetrics

                    // Window-absolute coords. View.x / View.y are
                    // parent-relative and don't reflect scroll position;
                    // getLocationInWindow matches sdk-react-native's
                    // measureInWindow + sdk-swift's convertPoint:toWindow.
                    val location = IntArray(2).also { getLocationInWindow(it) }

                    awv.sendDimensions(
                        windowWidth = windowRect.width().toFloat(),
                        windowHeight = windowRect.height().toFloat(),
                        screenWidth = displayMetrics.widthPixels.toFloat(),
                        screenHeight = displayMetrics.heightPixels.toFloat(),
                        containerWidth = width.toFloat(),
                        containerHeight = height.toFloat(),
                        containerX = location[0].toFloat(),
                        containerY = location[1].toFloat(),
                        keyboardHeight = getKeyboardHeight(),
                    )
                }
                delay(Constants.DIMENSION_REPORT_INTERVAL_MS)
            }
        }
    }

    private fun getKeyboardHeight(): Float {
        val rect = Rect()
        getWindowVisibleDisplayFrame(rect)
        val screenHeight = rootView.height
        return (screenHeight - rect.bottom).toFloat().coerceAtLeast(0f)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        adWebView?.destroy()
        ad?.destroy()
    }
}
