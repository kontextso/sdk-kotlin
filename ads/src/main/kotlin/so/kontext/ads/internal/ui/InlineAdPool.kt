package so.kontext.ads.internal.ui

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import java.util.Collections

private const val MAX_POOL_SIZE = 10

internal object InlineAdPool {
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
        initIfNew: (WebView) -> Unit,
    ): Entry {
        val existing = entries[key]
        if (existing != null) {
            (existing.webView.parent as? ViewGroup)?.removeView(existing.webView)
            return existing
        }

        val webView = WebView(appContext).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        initIfNew(webView)
        val entry = Entry(webView = webView, lastHeightCssPx = 0)
        entries[key] = entry
        return entry
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
