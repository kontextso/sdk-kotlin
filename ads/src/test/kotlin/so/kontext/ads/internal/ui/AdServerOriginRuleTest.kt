package so.kontext.ads.internal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression test for KON-1714 — IAB OMID compliance flagged duplicate volumeChange
 * events on v3 sdk-kotlin video and interstitial ads. Root cause: the
 * `addDocumentStartJavaScript` calls in `baseAdSetup` used `setOf("*")`, which also
 * injected omsdk-v1.js into the verification iframes that omsdk-v1.js itself creates
 * for VAST adVerifications. The fix scopes the rule to the ad server origin only.
 */
class AdServerOriginRuleTest {

    @Test
    fun `https URL with no port returns scheme + host`() {
        assertEquals("https://server.megabrain.co", adServerOriginRule("https://server.megabrain.co"))
    }

    @Test
    fun `https URL with trailing slash strips path`() {
        assertEquals("https://server.megabrain.co", adServerOriginRule("https://server.megabrain.co/"))
    }

    @Test
    fun `URL with path returns origin only`() {
        assertEquals(
            "https://server.megabrain.co",
            adServerOriginRule("https://server.megabrain.co/api/frame/abc"),
        )
    }

    @Test
    fun `URL with explicit port preserves port`() {
        assertEquals("http://localhost:3000", adServerOriginRule("http://localhost:3000/preload"))
    }

    @Test
    fun `URL without scheme returns null`() {
        assertNull(adServerOriginRule("server.megabrain.co"))
    }

    @Test
    fun `empty URL returns null`() {
        assertNull(adServerOriginRule(""))
    }

    @Test
    fun `malformed URL returns null`() {
        assertNull(adServerOriginRule("ht!tp://broken"))
    }

    @Test
    fun `URL with subdomain preserved`() {
        assertEquals(
            "https://staging.server.megabrain.co",
            adServerOriginRule("https://staging.server.megabrain.co/"),
        )
    }
}
