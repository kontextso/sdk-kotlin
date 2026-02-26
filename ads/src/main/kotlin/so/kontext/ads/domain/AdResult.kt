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
     * Indicates that no ad is available for the current context.
     *
     * @property skipCode The code indicating the reason why the ad was skipped.
     */
    public data class NoFill(val skipCode: String) : AdResult

    /**
     * Indicates that an error occurred during the ad request.
     *
     * @property error Kontext sdk error
     */
    public data class Error(val error: KontextError) : AdResult
}
