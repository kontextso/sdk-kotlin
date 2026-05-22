package so.kontext.ads.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import so.kontext.kit.omsdk.OmManager
import java.util.Collections

private const val MAX_POOL_SIZE = 10
private const val WEBVIEW_DESTROY_DELAY_MS = 1000L

/**
 * LRU pool of WebViews keyed by ad identifier (messageId-bidId).
 *
 * Supports RecyclerView/LazyColumn reuse: when a composable is recycled and
 * later recomposed with the same key, it gets back the same WebView without
 * reloading the iframe.
 *
 * When the pool exceeds [MAX_POOL_SIZE], the least recently used entry is
 * evicted and its WebView destroyed (with a short delay to avoid crashes).
 */
internal object WebViewPool {

    internal data class Entry(
        val webView: WebView,
        val adWebView: AdWebView,
        var lastHeightCssPx: Int = 0,
    )

    private val entries: MutableMap<String, Entry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Entry>(MAX_POOL_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
                val shouldRemove = size > MAX_POOL_SIZE
                if (shouldRemove) {
                    eldest?.value?.let { entry ->
                        (entry.webView.parent as? ViewGroup)?.removeView(entry.webView)
                        entry.adWebView.destroy()
                        entry.webView.destroyDelayed()
                    }
                }
                return shouldRemove
            }
        },
    )

    /**
     * Obtains a pooled WebView for the given key.
     * If a cached entry exists, detaches it from its current parent and returns it.
     * Otherwise, creates a new WebView, initializes it, and caches it.
     *
     * @param key Unique key for this ad slot (typically "messageId-bidId")
     * @param appContext Application context for WebView creation
     * @param initialize Callback to set up the new entry (add JS interface, load URL, etc.)
     */
    fun obtain(
        key: String,
        appContext: android.content.Context,
        ad: so.kontext.ads.Ad,
        initialize: (Entry) -> Unit,
    ): Entry {
        val existing = entries[key]
        if (existing != null) {
            // Detach from previous parent (LazyColumn recycling)
            (existing.webView.parent as? ViewGroup)?.removeView(existing.webView)
            // Re-bind to the current Ad instance
            existing.adWebView.rebind(ad)
            return existing
        }

        val webView = WebView(appContext).apply {
            baseAdSetup(appContext, ad.session.config.adServerUrl)
        }
        val adWebView = AdWebView(ad)
        val entry = Entry(
            webView = webView,
            adWebView = adWebView,
            lastHeightCssPx = 0,
        )
        adWebView.setupWebView(webView)
        initialize(entry)

        entries[key] = entry
        return entry
    }

    fun updateHeight(key: String, cssPx: Int) {
        entries[key]?.lastHeightCssPx = cssPx
    }

    fun lastHeight(key: String): Int = entries[key]?.lastHeightCssPx ?: 0

    /**
     * Clears all pooled WebViews. Called when a new user message invalidates all ads.
     */
    fun clearAll() {
        synchronized(entries) {
            val snapshot = entries.values.toList()
            entries.clear()
            try {
                Handler(Looper.getMainLooper()).post {
                    snapshot.forEach { entry ->
                        (entry.webView.parent as? ViewGroup)?.removeView(entry.webView)
                        entry.adWebView.destroy()
                        entry.webView.destroyDelayed()
                    }
                }
            } catch (_: RuntimeException) {
                // Looper not available (JVM test environment) — entries already cleared
            }
        }
    }

    /**
     * Removes a single entry from the pool.
     */
    fun remove(key: String) {
        val entry = entries.remove(key)
        if (entry != null) {
            try {
                Handler(Looper.getMainLooper()).post {
                    (entry.webView.parent as? ViewGroup)?.removeView(entry.webView)
                    entry.adWebView.destroy()
                    entry.webView.destroyDelayed()
                }
            } catch (_: RuntimeException) {
                // Looper not available (JVM test environment)
            }
        }
    }
}

/**
 * Delayed WebView destruction to avoid crashes when removing from a parent
 * that hasn't finished its layout pass.
 */
internal fun WebView.destroyDelayed() {
    Handler(Looper.getMainLooper()).postDelayed({
        loadUrl("about:blank")
        destroy()
    }, WEBVIEW_DESTROY_DELAY_MS)
}

/**
 * Base WebView setup applied to all pooled WebViews.
 * Configures JS, DOM storage, transparent background, and document-start scripts.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.baseAdSetup(appContext: android.content.Context, adServerUrl: String) {
    setBackgroundColor(android.graphics.Color.TRANSPARENT)

    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        // Scope every document-start script to the ad-server origin only.
        // Using `setOf("*")` re-introduces KON-1714 — omsdk-v1.js gets
        // re-injected into the verification iframes it itself creates for
        // VAST adVerifications, causing duplicate volumeChange events
        // that fail IAB OMID compliance. If the URL is malformed we
        // skip injection entirely rather than fall back to a wildcard.
        val originRule = so.kontext.ads.internal.adServerOriginRule(adServerUrl)
        if (originRule == null) {
            android.util.Log.w(
                "Kontext SDK",
                "Malformed adServerUrl=$adServerUrl — document-start scripts skipped",
            )
        } else {
            val originRules = setOf(originRule)

            // OMSDK JS — must be first so OMID is available before any ad code runs
            OmManager.omsdkScript(appContext)?.let { omidJs ->
                WebViewCompat.addDocumentStartJavaScript(this, omidJs, originRules)
            }

            // Bridge script (catches early postMessages); origin baked from adServerUrl.
            WebViewCompat.addDocumentStartJavaScript(
                this,
                AdWebView.bridgeScript(adServerUrl).trimIndent(),
                originRules,
            )

            // Viewport meta tag for proper scaling
            WebViewCompat.addDocumentStartJavaScript(
                this,
                VIEWPORT_SCRIPT.trimIndent(),
                originRules,
            )

            // Video poster override to avoid Android's default play icon
            WebViewCompat.addDocumentStartJavaScript(
                this,
                VIDEO_POSTER_SCRIPT.trimIndent(),
                originRules,
            )

            // Console.log interceptor — forwards [kontext] and [OMID] logs to native debug
            WebViewCompat.addDocumentStartJavaScript(
                this,
                CONSOLE_INTERCEPT_SCRIPT.trimIndent(),
                originRules,
            )
        }
    } else {
        android.util.Log.w("Kontext SDK", "DOCUMENT_START_SCRIPT not supported — OMID JS not injected")
    }

    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        loadWithOverviewMode = true
        useWideViewPort = true
    }

    // Console-message routing to publishers' `onDebugEvent` happens via
    // the JS-side `CONSOLE_INTERCEPT_SCRIPT` injected at document-start
    // (see below): it forwards [kontext]/[OMID] log lines through the
    // `kontextBridge.postMessage({type:'_console'})` channel, which
    // AdWebView.handleMessage routes to `ad.session.debug(...)`.
    // Matches sdk-swift's `_console` channel — no native WKChromeClient
    // equivalent on iOS, and no native fallback here either.
}

private const val VIEWPORT_SCRIPT = """
    (function() {
        if (document.querySelector('meta[name="viewport"]')) return;
        var meta = document.createElement('meta');
        meta.name = 'viewport';
        meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
        document.head.appendChild(meta);
    })();
"""

private const val CONSOLE_INTERCEPT_SCRIPT = """
    (function() {
        var _log = console.log;
        var _warn = console.warn;
        var _error = console.error;
        function intercept(original, level) {
            return function() {
                var msg = Array.prototype.slice.call(arguments).join(' ');
                if (msg.indexOf('[kontext') !== -1 || msg.indexOf('[OMID') !== -1) {
                    try {
                        if (window.kontextBridge) {
                            window.kontextBridge.postMessage(JSON.stringify({type:'_console', data:{level:level, message:msg}}));
                        }
                    } catch(e) {}
                }
                original.apply(console, arguments);
            };
        }
        console.log = intercept(_log, 'log');
        console.warn = intercept(_warn, 'warn');
        console.error = intercept(_error, 'error');
    })();
"""

// 1×1 transparent PNG set as `poster` on every <video>. Any poster
// attribute tells Chromium "we provide our own placeholder", so it
// skips drawing the default gray-and-black play icon between layout
// completion and the first decoded frame. The 1×1 PNG by itself
// makes OMID's bounding-rect-based geometry collapse toward 1×1 —
// that's neutralised by `useOmidVideoSession` (in
// ads/packages/omsdk) setting `videoEl.style.aspectRatio` from the
// HTML width/height attributes before OMID measures, so the
// layout box keeps the React-intended size.
internal const val VIDEO_POSTER_SCRIPT: String = """
    (function() {
        var T = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIW2NkYGBgAAAABAABJzQnCgAAAABJRU5ErkJggg==";
        var css = document.createElement('style');
        css.textContent = 'video{background:#000!important;}' +
            'video::-webkit-media-controls-overlay-play-button{display:none;}';
        document.documentElement.appendChild(css);

        var apply = function() {
            document.querySelectorAll('video').forEach(function(v) {
                v.setAttribute('playsinline', '');
                v.setAttribute('preload', 'auto');
                if (!v.getAttribute('poster')) {
                    v.setAttribute('poster', T);
                }
            });
        };
        apply();
        new MutationObserver(apply).observe(
            document.documentElement,
            { childList: true, subtree: true },
        );
    })();
"""
