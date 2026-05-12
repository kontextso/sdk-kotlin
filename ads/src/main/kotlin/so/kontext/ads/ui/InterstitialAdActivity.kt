package so.kontext.ads.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import so.kontext.ads.model.ImpressionTrigger
import so.kontext.ads.ui.iframe.IframeEvent
import so.kontext.kit.omsdk.OmCreativeType
import so.kontext.kit.omsdk.OmManager
import so.kontext.kit.omsdk.OmSession

// OmManager is the iOS-style caller-owned manager — its only role here is the
// static `omsdkScript(context)` accessor for document-start JS injection.

/**
 * Full-screen Activity for rendering modal/interstitial ads.
 *
 * Has a full JavaScript bridge matching the inline ad's bridge, handling:
 * - init-component-iframe → cancel timeout, show WebView
 * - ad-done-component-iframe → notify parent (OM session start)
 * - close-component-iframe → finish Activity
 * - error-component-iframe → finish Activity
 * - click-iframe → open URL in Custom Tabs
 * - event-iframe → forward to parent via callback
 */
public class InterstitialAdActivity : Activity() {

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var initialized = false

    // OM session owned by THIS Activity. Mirrors v3 sdk-kotlin's
    // ModalAdActivity ownership model — the modal Activity creates its own
    // session on `ad-done-component-iframe` (when the inner ad is rendered
    // inside the modal WebView) and finishes it on close/error/destroy.
    // The parent inline `Ad` does NOT own this — that's the bug v3 → v4
    // regression. `adWebView.getWebView()` in `Ad.startOmSessionDelayed()`
    // would attach the session to the inline WebView (which is in the
    // background), not the visible modal WebView.
    private var modalOmSession: OmSession? = null
    private val modalScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var omSessionStartJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        val adServerUrl = intent.getStringExtra(EXTRA_AD_SERVER_URL) ?: ""
        val timeoutMs = intent.getIntExtra(EXTRA_TIMEOUT, DEFAULT_TIMEOUT).toLong()

        val wv = createWebView(adServerUrl)
        webView = wv
        setContentView(wv)

        // Start invisible — shown on init-component-iframe
        wv.visibility = View.INVISIBLE

        wv.loadUrl(url)

        // Auto-close on timeout if init-component-iframe not received
        timeoutRunnable = Runnable {
            if (!initialized) {
                modalEventCallback?.invoke(
                    IframeEvent.ErrorComponent(message = "modal init timeout", errorType = "timeout"),
                )
                finish()
            }
        }
        handler.postDelayed(timeoutRunnable!!, timeoutMs)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(adServerUrl: String): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.BLACK)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            // Inject OMSDK JS + bridge at document start
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                OmManager.omsdkScript(context)?.let { omidJs ->
                    WebViewCompat.addDocumentStartJavaScript(this, omidJs, setOf("*"))
                }

                WebViewCompat.addDocumentStartJavaScript(
                    this,
                    AdWebView.bridgeScript(adServerUrl).trimIndent(),
                    setOf("*"),
                )
            }

            addJavascriptInterface(ModalBridgeInterface(), "kontextBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Fallback bridge injection
                    view?.evaluateJavascript(AdWebView.bridgeScript(adServerUrl), null)
                }
            }

            webChromeClient = WebChromeClient()
        }
    }

    private fun handleMessage(json: String) {
        val event = IframeEvent.parse(json) ?: return
        when (event) {
            is IframeEvent.InitComponent -> {
                initialized = true
                timeoutRunnable?.let { handler.removeCallbacks(it) }
                webView?.visibility = View.VISIBLE
            }
            is IframeEvent.CloseComponent -> {
                finishModalOmSession()
                modalEventCallback?.invoke(event)
                finish()
            }
            is IframeEvent.ErrorComponent -> {
                finishModalOmSession()
                modalEventCallback?.invoke(event)
                finish()
            }
            is IframeEvent.AdDoneComponent -> {
                // Mirror v3 sdk-kotlin's ModalAdActivity: start OM session
                // on THIS modal WebView (not the inline one). Gate behind
                // `post {}` + `OnPreDrawListener` to hop off the JS-bridge
                // worker thread to main and to wait for the first layout
                // pass — otherwise OMID samples the pre-layout 1×1 modal
                // WebView and pins that geometry on the session forever.
                //
                // Only component-trigger bids own their OMID session on
                // the modal WebView; immediate-trigger bids already
                // started OMID on the inline WebView at `ad-done-iframe`
                // time. Without this guard a misrouted event would
                // double-start OMID for the same bid.
                if (impressionTrigger == ImpressionTrigger.COMPONENT) {
                    startModalOmSession()
                }
                modalEventCallback?.invoke(event)
            }
            is IframeEvent.Event -> modalEventCallback?.invoke(event)
            // Forward Click to Ad.handleClick (via the callback) so modal
            // clicks get the same target / fallbackUrl / error-reporting
            // treatment as inline clicks. Doing it here previously ignored
            // `event.target` (always opened in-app browser, never the
            // system browser) and `event.fallbackUrl`.
            is IframeEvent.Click -> modalEventCallback?.invoke(event)
            // Other IframeEvent variants don't apply inside the modal (init-iframe,
            // resize-iframe, etc. are inline-only) — drop silently.
            else -> Unit
        }
    }

    private fun startModalOmSession() {
        val wv = webView ?: return
        val mgr = omManager ?: return
        val type = omCreativeType ?: return
        val url = intent.getStringExtra(EXTRA_URL)
        if (modalOmSession != null) return // already running
        // Hop to main + wait for first pre-draw so the modal WebView has
        // measured size at registerAdView time.
        handler.post {
            val observer = wv.viewTreeObserver
            observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    wv.viewTreeObserver.removeOnPreDrawListener(this)
                    omSessionStartJob = modalScope.launch {
                        val sess = mgr.createSession(wv, url, type)
                        modalOmSession = sess
                        // Display sessions use NATIVE impression owner — fire
                        // loaded() + impressionOccurred() natively. Video
                        // sessions are JS-owned; iframe fires them.
                        if (sess != null && type == OmCreativeType.DISPLAY) {
                            sess.loaded()
                            sess.impressionOccurred()
                        }
                    }
                    return true
                }
            })
        }
    }

    private fun finishModalOmSession() {
        omSessionStartJob?.cancel()
        omSessionStartJob = null
        val sess = modalOmSession ?: return
        modalOmSession = null
        sess.retire()
        sess.finish()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        // Finish OM session BEFORE tearing down the WebView. `OmSession.finish()`
        // dispatches `sessionFinish` to the JS verification scripts and then
        // holds the WebView reference for 1s so the scripts can flush their
        // HTTP postbacks. Critically: do NOT call `wv.loadUrl("about:blank")`
        // here — it synchronously tears down the JS context, killing the
        // verification scripts inside that 1s flush window. The pool's
        // `destroyDelayed()` (1s later) handles the blank URL + destroy,
        // matching the inline `AdWebView.destroy()` pattern.
        finishModalOmSession()
        webView?.let { wv ->
            wv.removeJavascriptInterface("kontextBridge")
            wv.stopLoading()
            wv.destroyDelayed()
        }
        webView = null
        modalEventCallback = null
        omManager = null
        omCreativeType = null
        impressionTrigger = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Disable back button for modal ads
    }

    private inner class ModalBridgeInterface {
        @JavascriptInterface
        fun postMessage(json: String) {
            handler.post { handleMessage(json) }
        }
    }

    public companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TIMEOUT = "extra_timeout"
        private const val EXTRA_AD_SERVER_URL = "extra_ad_server_url"
        internal const val DEFAULT_TIMEOUT = 5000

        /**
         * Static callback for modal events. Set by Ad before launching the Activity.
         * Cleared on Activity destroy.
         */
        internal var modalEventCallback: ((IframeEvent) -> Unit)? = null

        /**
         * Static references to OMID dependencies the Activity needs at
         * `ad-done-component-iframe` time. Set by `Ad.handleOpenComponent`
         * just before launching the Activity; cleared on Activity destroy.
         * Static (vs Intent extras) because `OmManager` is not `Parcelable`.
         */
        internal var omManager: OmManager? = null
        internal var omCreativeType: OmCreativeType? = null
        internal var impressionTrigger: ImpressionTrigger? = null

        public fun getIntent(
            context: Context,
            url: String,
            adServerUrl: String,
            timeoutMs: Int = DEFAULT_TIMEOUT,
        ): Intent {
            return Intent(context, InterstitialAdActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TIMEOUT, timeoutMs)
                putExtra(EXTRA_AD_SERVER_URL, adServerUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
