package so.kontext.ads.internal.data.error

internal sealed class ApiError(
    cause: Throwable? = null,
) : SdkError(cause = cause) {

    internal data class TemporaryError(
        override val cause: Throwable? = null,
        val code: String,
    ) : ApiError(cause)

    internal data class PermanentError(
        override val cause: Throwable? = null,
        val code: String,
    ) : ApiError(cause)

    internal data class Timeout(
        override val cause: Throwable,
    ) : ApiError(cause)

    internal data class Connection(
        override val cause: Throwable,
    ) : ApiError(cause)

    internal data class Serialization(
        override val cause: Throwable,
    ) : ApiError(cause)

    internal data class Http(
        override val cause: Throwable,
        val code: Int,
    ) : ApiError(cause)

    internal data class UnexpectedError(
        override val cause: Throwable?,
    ) : ApiError(cause)
}
