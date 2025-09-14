package so.kontext.ads.internal.data.error

/**
 * A sealed class representing all possible public-facing errors that the Kontext Ads SDK can produce.
 */
public sealed class KontextError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * Indicates that an ad was requested, but none were available to be served.
     */
    public data class AdUnavailable(
        override val message: String = "No ad was available.",
    ) : KontextError(message)

    /**
     * Indicates a failure to communicate with the Kontext Ads servers due to a network issue,
     * such as a timeout or lack of connectivity.
     */
    public data class NetworkError(
        override val message: String = "A network error occurred.",
        override val cause: Throwable?,
    ) : KontextError(message, cause)
}
