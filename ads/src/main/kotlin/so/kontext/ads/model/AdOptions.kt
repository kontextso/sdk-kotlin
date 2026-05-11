package so.kontext.ads.model

import so.kontext.ads.Constants

/**
 * Per-ad overrides for `Session.createAd(...)`.
 *
 * `code` selects which placement on the server matches this ad slot;
 * defaults to [Constants.DEFAULT_PLACEMENT_CODE] (the standard inline
 * placement). `theme` passes a publisher-defined theme string through
 * to the iframe so ad creatives can adapt to dark / light / branded
 * color schemes.
 *
 * Mirrors iOS `AdOptions` (`KontextSwiftSDK/Model/AdOptions.swift`).
 */
public data class AdOptions(
    val code: String = Constants.DEFAULT_PLACEMENT_CODE,
    val theme: String? = null,
)
