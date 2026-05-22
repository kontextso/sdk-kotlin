package so.kontext.ads.internal

import java.net.URI
import java.net.URISyntaxException

/**
 * Builds the `allowedOriginRules` entry for
 * [androidx.webkit.WebViewCompat.addDocumentStartJavaScript] so the
 * document-start scripts (OMSDK JS, AndroidBridge installer, viewport
 * meta tag, video-poster shim, console interceptor) run in the main
 * ad iframe only — NOT in the sub-iframes that omsdk-v1.js creates
 * for VAST `adVerifications`.
 *
 * Returns `null` when [adServerUrl] is malformed (missing scheme or
 * host); callers should skip injection rather than fall back to a
 * wildcard. Using `setOf("*")` here would re-introduce the v3 bug
 * (KON-1714) where duplicate `volumeChange` events from third-party
 * verification iframes failed IAB OMID compliance.
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
