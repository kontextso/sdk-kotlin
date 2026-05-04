package so.kontext.ads.internal.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import so.kontext.ads.R
import so.kontext.ads.internal.utils.om.WebViewOmSession
import java.net.URI
import java.net.URISyntaxException
import java.util.Collections

private const val MAX_POOL_SIZE = 10
private const val WEBVIEW_DESTROY_DELAY_MS = 1000L

// Holds up to MAX_POOL_SIZE instances of webviews per SDK instance. This is done to support displaying
// webviews inside RecyclerView or LazyColumn without reloading the webview every time the item is recycled.
internal object InlineAdWebViewPool {
    internal data class Entry(
        val webView: WebView,
        val iFrameCommunicator: IFrameCommunicator,
        var lastHeightCssPx: Int = 0,
    )

    private val entries: MutableMap<String, Entry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Entry>(MAX_POOL_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
                val shouldRemove = size > MAX_POOL_SIZE
                if (shouldRemove) {
                    eldest?.value?.webView?.let { webview ->
                        (webview.parent as? ViewGroup)?.removeView(webview)
                        WebViewOmSession.finish(webview)
                        webview.destroyDelayed()
                    }
                }
                return shouldRemove
            }
        },
    )

    internal fun obtain(
        key: String,
        appContext: Context,
        adServerUrl: String,
        initialize: (Entry) -> Unit,
    ): Entry {
        val existing = entries[key]
        if (existing != null) {
            (existing.webView.parent as? ViewGroup)?.removeView(existing.webView)
            return existing
        }
        val webView = WebView(appContext).apply {
            baseAdSetup(adServerUrl)
        }
        val iFrameCommunicator = IFrameCommunicatorImpl(webView)
        val entry = Entry(
            webView = webView,
            lastHeightCssPx = 0,
            iFrameCommunicator = iFrameCommunicator,
        )
        initialize(entry)

        entries[key] = entry
        return entry
    }

    fun updateHeight(key: String, cssPx: Int) {
        entries[key]?.lastHeightCssPx = cssPx
    }

    fun lastHeight(key: String): Int = entries[key]?.lastHeightCssPx ?: 0

    fun clearAll() {
        synchronized(entries) {
            val snapshot = entries.values.toList()
            entries.clear()
            Handler(Looper.getMainLooper()).post {
                snapshot.forEach { entry ->
                    (entry.webView.parent as? ViewGroup)?.removeView(entry.webView)
                    WebViewOmSession.finish(entry.webView)
                    entry.webView.destroyDelayed()
                }
            }
        }
    }
}

internal fun WebView.destroyDelayed() {
    Handler(Looper.getMainLooper()).postDelayed({
        loadUrl("about:blank")
        destroy()
    }, WEBVIEW_DESTROY_DELAY_MS)
}

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.baseAdSetup(adServerUrl: String) {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)

    if (WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)) {
        // Scope the document-start scripts to the ad server's origin only. Using setOf("*")
        // also injects them into iframes that omsdk-v1.js creates internally for VAST
        // adVerifications, which produces duplicate volumeChange events (IAB compliance
        // failure on video + interstitial). Mirrors sdk-swift's `forMainFrameOnly: true`.
        val originRule = adServerOriginRule(adServerUrl)
        if (originRule == null) {
            Log.w(
                "Kontext SDK",
                "Could not derive ad server origin from \"$adServerUrl\" — OMID JS not injected",
            )
            return
        }
        val originRules = setOf(originRule)
        val omidJs = context.resources.openRawResource(R.raw.omsdk_v1).use { it.readBytes().toString(Charsets.UTF_8) }
        WebViewCompat.addDocumentStartJavaScript(this, omidJs, originRules)

        WebViewCompat.addDocumentStartJavaScript(
            this,
            IFrameBridge.DocumentStartScript.trimIndent(),
            originRules,
        )

        WebViewCompat.addDocumentStartJavaScript(
            this,
            IFrameBridge.PosterStartScript.trimIndent(),
            originRules,
        )
    } else {
        Log.w("Kontext SDK", "DOCUMENT_START_SCRIPT not supported — OMID JS not injected, OM sessions will not function")
    }

    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
    }

    webChromeClient = WebChromeClient()
}

/**
 * Builds the `allowedOriginRules` entry for [WebViewCompat.addDocumentStartJavaScript] so
 * the document-start scripts run in the main ad iframe only and not in sub-iframes that
 * the OMID JS library creates for VAST adVerifications.
 *
 * Returns `null` when [adServerUrl] is malformed (missing scheme or host); callers should
 * skip injection rather than fall back to a wildcard.
 */
internal fun adServerOriginRule(adServerUrl: String): String? = try {
    val uri = URI(adServerUrl)
    val scheme = uri.scheme
    val host = uri.host
    if (scheme.isNullOrEmpty() || host.isNullOrEmpty()) {
        null
    } else if (uri.port == -1) {
        "$scheme://$host"
    } else {
        "$scheme://$host:${uri.port}"
    }
} catch (_: URISyntaxException) {
    null
}
