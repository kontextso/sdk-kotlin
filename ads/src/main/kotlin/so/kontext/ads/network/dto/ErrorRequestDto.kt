package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * JSON body sent to `POST /error` for SDK diagnostics.
 *
 * Mirrors the shape sent by sdk-js / sdk-swift / sdk-react-native /
 * sdk-flutter so the server's ingestion path is identical across SDKs.
 *
 * Mirrors iOS `ErrorRequestDTO` (`Networking/DTO/ErrorRequestDTO.swift`).
 */
@Serializable
internal data class ErrorRequestDto(
    val error: String,
    val stack: String? = null,
    val additionalData: AdditionalData,
) {
    /**
     * Session-/bid-scoped attribution metadata. All fields optional so
     * callers without full context (e.g. `/init` failures, where the
     * session isn't established yet) can still emit a useful report.
     * Null fields are omitted from the wire — matches sdk-swift's
     * default `Encodable` behaviour.
     */
    @Serializable
    internal data class AdditionalData(
        val publisherToken: String? = null,
        val conversationId: String? = null,
        val userId: String? = null,
        val bidId: String? = null,
        val sdk: SdkDto,
    )
}
