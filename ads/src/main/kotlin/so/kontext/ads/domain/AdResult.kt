package so.kontext.ads.domain

import so.kontext.ads.internal.data.error.KontextError

/**
 * Represents the result of an ad request, encapsulating both success and failure states.
 */
public sealed interface AdResult {
    /**
     * Indicates that ads are available and provides the configuration for them.
     * The map's key is the message ID to which the ads should be attached.
     */
    public data class Filled(val ads: Map<String, List<AdConfig>>) : AdResult

    /**
     * Indicates that no ads could be filled for the request and provides an optional skip code
     * describing the reason.
     */
    public data class NoFill(val skipCode: String?) : AdResult

    /**
     * Indicates that an error occurred during the ad request.
     *
     * @property error Kontext sdk error
     */
    public data class Error(val error: KontextError) : AdResult

    public companion object {
        public const val SKIP_CODE_UNFILLED_BID: String = "unfilled_bid"
        public const val SKIP_CODE_SESSION_DISABLED: String = "session_disabled"
        public const val SKIP_CODE_REQUEST_FAILED: String = "request_failed"
        public const val SKIP_CODE_UNKNOWN: String = "unknown"
        public const val SKIP_CODE_ERROR: String = "error"
    }
}
