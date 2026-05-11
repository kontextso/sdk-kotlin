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
import so.kontext.ads.model.AdOptions
import so.kontext.ads.Session

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

        val wv = WebView(context)
        webView = wv
        awv.setupWebView(wv)
        addView(wv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        awv.load()

        // Observe height/visibility changes via polling
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        lifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val currentHeight = currentAd.height
                val isVisible = currentAd.isVisible

                post {
                    if (isVisible && currentHeight > 0f) {
                        wv.visibility = VISIBLE
                        val heightPx = (currentHeight * resources.displayMetrics.density).toInt()
                        wv.layoutParams = wv.layoutParams?.apply { height = heightPx }
                        onHeightChange?.invoke(currentHeight)
                    } else {
                        wv.visibility = GONE
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

                    awv.sendDimensions(
                        windowWidth = windowRect.width().toFloat(),
                        windowHeight = windowRect.height().toFloat(),
                        screenWidth = displayMetrics.widthPixels.toFloat(),
                        screenHeight = displayMetrics.heightPixels.toFloat(),
                        containerWidth = width.toFloat(),
                        containerHeight = height.toFloat(),
                        containerX = x,
                        containerY = y,
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
