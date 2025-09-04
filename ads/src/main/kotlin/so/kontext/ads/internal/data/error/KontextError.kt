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
    public class AdUnavailable(
        message: String = "No ad was available.",
    ) : KontextError(message)

    /**
     * Indicates a failure to communicate with the Kontext Ads servers due to a network issue,
     * such as a timeout or lack of connectivity.
     */
    public class NetworkError(
        message: String = "A network error occurred.",
        cause: Throwable?,
    ) : KontextError(message, cause)
}
