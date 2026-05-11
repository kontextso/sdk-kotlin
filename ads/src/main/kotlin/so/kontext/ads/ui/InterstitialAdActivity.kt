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
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import so.kontext.kit.omsdk.OmManager
import so.kontext.ads.ui.iframe.IframeEvent

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

            addJavascriptInterface(ModalBridgeInterface(adServerUrl), "kontextBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Fallback bridge injection
                    view?.evaluateJavascript(AdWebView.bridgeScript(adServerUrl), null)
                }
            }

            webChromeClient = WebChromeClient()
        }
    }

    private fun handleMessage(json: String, adServerUrl: String) {
        val event = IframeEvent.parse(json) ?: return
        when (event) {
            is IframeEvent.InitComponent -> {
                initialized = true
                timeoutRunnable?.let { handler.removeCallbacks(it) }
                webView?.visibility = View.VISIBLE
            }
            is IframeEvent.CloseComponent -> {
                modalEventCallback?.invoke(event)
                finish()
            }
            is IframeEvent.ErrorComponent -> {
                modalEventCallback?.invoke(event)
                finish()
            }
            is IframeEvent.AdDoneComponent,
            is IframeEvent.Event,
            -> modalEventCallback?.invoke(event)
            is IframeEvent.Click -> {
                val url = event.url ?: return
                val fullUrl = if (url.startsWith("/") && !url.startsWith("//")) "$adServerUrl$url" else url
                so.kontext.kit.ui.InAppBrowserManager.open(this, fullUrl)
            }
            // Other IframeEvent variants don't apply inside the modal (init-iframe,
            // resize-iframe, etc. are inline-only) — drop silently.
            else -> Unit
        }
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
        webView?.let { wv ->
            wv.removeJavascriptInterface("kontextBridge")
            // OmSession lifecycle is owned by the parent Ad — close-component-iframe
            // routes through Ad.retireOmSession() which handles retire + finish.
            wv.stopLoading()
            wv.loadUrl("about:blank")
            wv.destroyDelayed()
        }
        webView = null
        modalEventCallback = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Disable back button for modal ads
    }

    private inner class ModalBridgeInterface(private val adServerUrl: String) {
        @JavascriptInterface
        fun postMessage(json: String) {
            handler.post { handleMessage(json, adServerUrl) }
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
