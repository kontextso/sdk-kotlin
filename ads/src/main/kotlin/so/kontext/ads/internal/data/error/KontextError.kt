package so.kontext.ads.internal.data.error

/**
 * A sealed class representing all possible public-facing errors that the Kontext Ads SDK can produce.
 */
public sealed class KontextError(
    override val message: String,
    override val cause: Throwable? = null,
    public open val skipCode: String? = null,
) : Exception(message, cause) {

    /**
     * Indicates a failure to communicate with the Kontext Ads servers due to a network issue,
     * such as a timeout or lack of connectivity.
     */
    public data class NetworkError(
        override val message: String = "A network error occurred.",
        override val cause: Throwable?,
        override val skipCode: String? = null,
    ) : KontextError(message, cause, skipCode)
}
