package so.kontext.ads.internal.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import so.kontext.ads.internal.utils.om.WebViewOmSession
import java.util.Collections

private const val MAX_POOL_SIZE = 10

// Holds up to MAX_POOL_SIZE instances of webviews per SDK instance. This is done to support displaying
// webviews inside RecyclerView or LazyColumn without reloading the webview every time the item is recycled.
internal object InlineAdWebViewPool {
    internal data class Entry(
        val webView: WebView,
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
                        webview.destroy()
                    }
                }
                return shouldRemove
            }
        },
    )

    internal fun obtain(
        key: String,
        appContext: Context,
    ): WebView {
        val existing = entries[key]
        if (existing != null) {
            (existing.webView.parent as? ViewGroup)?.removeView(existing.webView)
            return existing.webView
        }
        val webView = WebView(appContext).apply {
            baseAdSetup()
        }
        val entry = Entry(
            webView = webView,
            lastHeightCssPx = 0,
        )
        entries[key] = entry
        return entry.webView
    }

    fun updateHeight(key: String, cssPx: Int) {
        entries[key]?.lastHeightCssPx = cssPx
    }

    fun lastHeight(key: String): Int = entries[key]?.lastHeightCssPx ?: 0

    fun clearAll() {
        synchronized(entries) {
            entries.values.forEach { entry ->
                (entry.webView.parent as? ViewGroup)?.removeView(entry.webView)
                WebViewOmSession.finish(entry.webView)
                entry.webView.destroy()
            }
            entries.clear()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.baseAdSetup() {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)

    if (WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(
            this,
            IFrameBridge.DocumentStartScript.trimIndent(),
            setOf("*"),
        )

        // To avoid Android WebView loader for videos, inject JS code with 1x1 transparent pixel
        WebViewCompat.addDocumentStartJavaScript(
            this,
            IFrameBridge.PosterStartScript.trimIndent(),
            setOf("*"),
        )
    }

    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
    }

    webChromeClient = WebChromeClient()

    webViewClient = object : WebViewClient() {
        override fun onPageFinished(webView: WebView, url: String) {
            WebViewOmSession.startIfNeeded(webView, url)
        }
    }
}
