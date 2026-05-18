package so.kontext.ads.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `AdWebView.extractOrigin` bakes the expected origin into the bridge
 * script that gates `window.message` traffic against `event.origin`.
 * The output must match the browser's `URL.origin` / `event.origin`
 * canonicalisation, otherwise the bridge silently drops every iframe
 * message when a publisher configures the ad-server URL with mixed
 * case, an explicit default port, or a trailing path.
 *
 * Mirrors sdk-swift `AdWebView.canonicalOrigin(of:)` test coverage.
 */
class AdWebViewTest {

    @Test
    fun `lowercases scheme`() {
        assertEquals("https://server.megabrain.co", AdWebView.extractOrigin("HTTPS://server.megabrain.co"))
    }

    @Test
    fun `lowercases host`() {
        assertEquals("https://server.megabrain.co", AdWebView.extractOrigin("https://Server.MegaBrain.co"))
    }

    @Test
    fun `strips default https port`() {
        assertEquals("https://server.megabrain.co", AdWebView.extractOrigin("https://server.megabrain.co:443"))
    }

    @Test
    fun `strips default http port`() {
        assertEquals("http://server.megabrain.co", AdWebView.extractOrigin("http://server.megabrain.co:80"))
    }

    @Test
    fun `keeps non-default port`() {
        assertEquals("https://server.megabrain.co:8443", AdWebView.extractOrigin("https://server.megabrain.co:8443"))
    }

    @Test
    fun `drops path, query, and fragment`() {
        assertEquals(
            "https://server.megabrain.co",
            AdWebView.extractOrigin("https://server.megabrain.co/preload?x=1#hash"),
        )
    }

    @Test
    fun `drops userinfo`() {
        assertEquals(
            "https://server.megabrain.co",
            AdWebView.extractOrigin("https://user:pass@server.megabrain.co"),
        )
    }

    @Test
    fun `composes mixed-case scheme, mixed-case host, default port, and path`() {
        // Combined: every normalisation step has to run for the bridge to
        // match the browser's event.origin on this URL.
        assertEquals(
            "https://server.megabrain.co",
            AdWebView.extractOrigin("HTTPS://Server.MegaBrain.co:443/preload"),
        )
    }

    @Test
    fun `returns null for unparseable URL`() {
        assertNull(AdWebView.extractOrigin("not a url"))
    }

    @Test
    fun `returns null for missing host`() {
        assertNull(AdWebView.extractOrigin("https:///path"))
    }

    @Test
    fun `bridgeScript bakes canonical origin into expectedOrigin`() {
        // End-to-end check: the bridge script is what actually compares
        // against event.origin in the WebView. A mixed-case URL with an
        // explicit default port must produce a canonical baked-in value.
        val script = AdWebView.bridgeScript("HTTPS://Server.MegaBrain.co:443")
        assertTrue(script.contains("var expectedOrigin = 'https://server.megabrain.co';"))
    }
}
