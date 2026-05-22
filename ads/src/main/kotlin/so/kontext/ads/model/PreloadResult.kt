package so.kontext.ads.model

import java.util.UUID

/**
 * Standardised result of a preload operation. Internal to the SDK:
 * produced by `Preload.requestAd(...)`, consumed by `Session.addMessage`
 * to drive publisher-facing [AdEvent]s. Not exposed in any public API
 * surface — publishers learn outcomes via `Session.events` / the
 * `onEvent` callback.
 *
 * `Success.bids` may be empty if the server responded OK but no bid won
 * the auction. When multiple placement codes are enabled, this carries
 * one bid per matching placement.
 *
 * `Success.sessionId` is nullable because trackOnly preloads can come
 * back with an empty body (no sessionId, no bids) — the server treats
 * them as analytics-only. Non-trackOnly successful responses always
 * carry a sessionId.
 *
 * `Failure` is for hard errors — network problems, schema validation,
 * or `disableSession = true` to opt the publisher out of further
 * preloads for this run.
 *
 * Mirrors iOS `PreloadResult` (`KontextSwiftSDK/Model/PreloadResult.swift`).
 */
internal sealed class PreloadResult {
    internal data class Success(
        val bids: List<Bid>,
        val sessionId: UUID?,
    ) : PreloadResult()

    internal data class Failure(
        val reason: String,
        val event: AdEvent? = null,
        val disableSession: Boolean = false,
    ) : PreloadResult()
}
