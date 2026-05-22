package so.kontext.ads.utils

/**
 * Resolves a potentially relative ad URL against the ad-server base URL.
 *
 * Server-relative paths (starting with `/` but not `//`) are prefixed
 * with [adServerUrl]. Everything else passes through unchanged:
 *
 * - Absolute URLs (`https://example.com/foo`) — already complete.
 * - Custom schemes (`amazon://product/123`, `fb://...`) — deep links;
 *   would be broken by prepending `https://server.example.com`.
 * - Protocol-relative URLs (`//cdn.example.com/foo`) — these resolve
 *   against the *iframe's* protocol, not the ad server's. Prepending
 *   `adServerUrl` would silently rewrite them to a different host.
 *
 * Lifted out of `Ad` because the resolution is pure (no instance state)
 * and easier to unit-test as a free function. Mirrors iOS's
 * `Utils/AdURLResolver.swift` / sdk-react-native's `resolveAdUrl(url, adServerUrl)`.
 */
internal fun resolveAdUrl(url: String, adServerUrl: String): String {
    return if (url.startsWith("/") && !url.startsWith("//")) {
        "$adServerUrl$url"
    } else {
        url
    }
}
