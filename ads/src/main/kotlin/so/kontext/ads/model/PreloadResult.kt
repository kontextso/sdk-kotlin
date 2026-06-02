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
 * Both `Success` and `Failure` carry the server `sessionId` when the
 * response included one. The server returns a `sessionId` on skip /
 * no-fill / ads-disabled responses too (not just fills), and the
 * `Session` must persist it from any of them — otherwise a session that
 * only ever skips (e.g. trackOnly / frequency-capped) never captures a
 * `sessionId`, sends an empty one every request, and the server mints a
 * fresh session each time. `sessionId` is nullable for the genuinely
 * empty cases (network/decode failure, or a body without one).
 *
 * `Failure` is also used for hard errors — network problems, schema
 * validation, or `disableSession = true` to opt the publisher out of
 * further preloads for this run.
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
        val sessionId: UUID? = null,
    ) : PreloadResult()
}
