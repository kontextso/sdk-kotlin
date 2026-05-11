package so.kontext.ads.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * When the SDK fires its impression event.
 *
 * `IMMEDIATE` (the default) fires as soon as the WebView reports that the
 * ad has rendered; `COMPONENT` defers until the iframe explicitly signals
 * `ad-done-component-iframe` so the server can hold the impression for
 * sequenced creatives (interstitials with a delayed reveal, e.g.).
 *
 * Internal — publishers learn outcomes via `AdEvent.Filled` / `Viewed` /
 * `RewardGranted` etc., not by inspecting bid metadata.
 *
 * The `@SerialName` annotations carry the lowercase wire form to
 * kotlinx.serialization so [BidDto.impressionTrigger] (a typed
 * `ImpressionTrigger?`) decodes directly from the wire — unknown
 * values coerce to `null` via the Preload Json's `coerceInputValues`
 * setting.
 *
 * Mirrors iOS `ImpressionTrigger` (`KontextSwiftSDK/Model/ImpressionTrigger.swift`).
 */
@Serializable
internal enum class ImpressionTrigger {
    @SerialName("immediate")
    IMMEDIATE,

    @SerialName("component")
    COMPONENT,

    ;

    companion object {
        /**
         * Parses the wire-format value (used by paths that read raw
         * strings — e.g. iframe-protocol payloads). Tolerates casing
         * drift and unknown values: any input that isn't a known case
         * (including `null`) falls back to [IMMEDIATE], the safe
         * default.
         */
        fun fromString(value: String?): ImpressionTrigger =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: IMMEDIATE
    }
}
