package so.kontext.ads.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `resolveAdUrl` decides whether to prepend the ad-server base URL to a
 * potentially relative URL. Only server-relative paths (`/` but not
 * `//`) get prefixed. Mirrors iOS `Utils/AdURLResolver.swift` and
 * sdk-react-native's `resolveAdUrl(url, adServerUrl)`.
 */
class AdURLResolverTest {

    private val adServerUrl = "https://server.megabrain.co"

    @Test
    fun `server-relative path gets adServerUrl prefix`() {
        assertEquals(
            "https://server.megabrain.co/api/frame/abc",
            resolveAdUrl("/api/frame/abc", adServerUrl),
        )
    }

    @Test
    fun `absolute https URL passes through unchanged`() {
        assertEquals(
            "https://example.com/foo",
            resolveAdUrl("https://example.com/foo", adServerUrl),
        )
    }

    @Test
    fun `absolute http URL passes through unchanged`() {
        assertEquals(
            "http://example.com/foo",
            resolveAdUrl("http://example.com/foo", adServerUrl),
        )
    }

    @Test
    fun `custom-scheme deep links pass through unchanged`() {
        // amazon://, fb://, intent://, market:// are app deep links —
        // prepending https://server.example.com would break them.
        assertEquals("amazon://product/B123", resolveAdUrl("amazon://product/B123", adServerUrl))
        assertEquals("fb://profile/me", resolveAdUrl("fb://profile/me", adServerUrl))
        assertEquals("market://details?id=foo", resolveAdUrl("market://details?id=foo", adServerUrl))
    }

    @Test
    fun `protocol-relative URL passes through unchanged`() {
        // `//cdn.example.com/foo` resolves against the iframe's protocol,
        // NOT the ad server's. Prepending adServerUrl would silently
        // rewrite it to a different host.
        assertEquals(
            "//cdn.example.com/foo",
            resolveAdUrl("//cdn.example.com/foo", adServerUrl),
        )
    }

    @Test
    fun `URL with query parameters preserved on prefix`() {
        assertEquals(
            "https://server.megabrain.co/api/frame/abc?code=inlineAd&messageId=m1",
            resolveAdUrl("/api/frame/abc?code=inlineAd&messageId=m1", adServerUrl),
        )
    }

    @Test
    fun `empty URL returns empty (no prefix)`() {
        // Empty string isn't `/`-prefixed, so it's left alone — the
        // caller's responsibility to filter empty URLs upstream.
        assertEquals("", resolveAdUrl("", adServerUrl))
    }

    @Test
    fun `adServerUrl with trailing components composes correctly`() {
        assertEquals(
            "https://staging.megabrain.co/api/frame/abc",
            resolveAdUrl("/api/frame/abc", "https://staging.megabrain.co"),
        )
    }
}
