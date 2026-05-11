package so.kontext.ads.model

import java.util.UUID

/**
 * Ad lifecycle events emitted by the SDK. Publisher code subscribes via
 * `SessionOptions.onEvent` (or the [Session.events] flow) and pattern-matches
 * on the sealed subclasses.
 *
 * Mirrors iOS `AdEvent` (`KontextSwiftSDK/Model/AdEvent.swift`) and
 * sdk-js's `AdEvent` contract in `sdk-common/src/ad-events.ts` —
 * case names, payload field shapes, and `name` strings are kept aligned
 * so cross-platform JS docs apply unchanged.
 *
 * `bidId` is typed as [UUID] (not [String]) — every emit point inside the
 * SDK already has the typed [Bid.bidId], and `UUID.toString()` produces
 * the canonical lowercase string when publishers stringify it for logs /
 * analytics. Server-emitted `Viewed` / `Clicked` payloads are typed (not
 * free-form maps) so a publisher reading e.g. `Viewed.format` can rely on
 * the field being present rather than fishing it out of `Map<String, Any?>`.
 */
public sealed class AdEvent {
    public data class Filled(
        val bidId: UUID,
        /**
         * Placement code this bid was matched to (e.g. `"inlineAd"`).
         * Required because publishers with multiple `enabledPlacementCodes`
         * receive one [Filled] per matched code and need to disambiguate.
         */
        val code: String,
        val revenue: Double? = null,
    ) : AdEvent()
    public data class NoFill(val skipCode: String) : AdEvent()
    public data class AdHeight(
        val bidId: UUID,
        val messageId: String,
        val height: Float,
    ) : AdEvent()
    public data class Viewed(
        val bidId: UUID,
        val content: String,
        val messageId: String,
        val format: String,
        val revenue: Double? = null,
    ) : AdEvent()
    public data class Clicked(
        val bidId: UUID,
        val content: String,
        val messageId: String,
        val url: String,
        val format: String,
        val area: String,
    ) : AdEvent()
    public data class RenderStarted(val bidId: UUID) : AdEvent()
    public data class RenderCompleted(val bidId: UUID) : AdEvent()
    public data class Error(val message: String, val errCode: String) : AdEvent()
    public data class VideoStarted(val bidId: UUID) : AdEvent()
    public data class VideoCompleted(val bidId: UUID) : AdEvent()
    public data class RewardGranted(val bidId: UUID) : AdEvent()

    /**
     * Stable string identifier of the event, suitable for diagnostics,
     * logs, and telemetry. Matches the wire-name sdk-js / sdk-swift use
     * (e.g. `"ad.filled"`, `"reward.granted"`).
     */
    public val name: String get() = when (this) {
        is Filled -> "ad.filled"
        is NoFill -> "ad.no-fill"
        is AdHeight -> "ad.height"
        is Viewed -> "ad.viewed"
        is Clicked -> "ad.clicked"
        is RenderStarted -> "ad.render-started"
        is RenderCompleted -> "ad.render-completed"
        is Error -> "ad.error"
        is VideoStarted -> "video.started"
        is VideoCompleted -> "video.completed"
        is RewardGranted -> "reward.granted"
    }
}

/** Callback shape passed to `SessionOptions.onEvent`. */
public typealias AdEventHandler = (AdEvent) -> Unit
