package so.kontext.ads.internal.ui

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
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
                    eldest?.value?.webView?.let {
                        (it.parent as? ViewGroup)?.removeView(it)
                        it.destroy()
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
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val entry = Entry(webView = webView, lastHeightCssPx = 0)
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
                entry.webView.destroy()
            }
            entries.clear()
        }
    }
}
