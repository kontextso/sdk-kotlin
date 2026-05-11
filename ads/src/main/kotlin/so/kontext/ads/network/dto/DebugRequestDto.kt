package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable

/**
 * JSON body sent to `POST /debug` for opt-in remote debug forwarding.
 *
 * Parallel to [ErrorRequestDto] — same `additionalData` shape so the
 * server can ingest both with shared attribution code. Distinct
 * payload (`name` + `data`) because debug events are arbitrary
 * structured logs, not error reports.
 *
 * `data` is a pre-stringified blob (JSON when the source value is
 * JSON-shaped, else `toString()`) rather than a typed payload because
 * `Session`'s debug entry point accepts `Any?` from across the SDK
 * (maps, primitives, exception values) and a typed shape would force
 * every call site to round-trip through a generic encoder. The capture
 * path serialises once at the boundary; the wire is opaque text.
 *
 * Mirrors iOS `DebugRequestDTO` (`Networking/DTO/DebugRequestDTO.swift`).
 */
@Serializable
internal data class DebugRequestDto(
    val name: String,
    val data: String? = null,
    val additionalData: AdditionalData,
) {
    /**
     * Session-scoped attribution metadata. `sessionId` is included
     * (unlike [ErrorRequestDto.AdditionalData]) because debug forwarding
     * is a diagnostic on a live session — the field is meaningful and
     * helps server-side filtering. `bidId` is omitted because debug
     * events fire across the whole session, not bid-by-bid.
     */
    @Serializable
    internal data class AdditionalData(
        val publisherToken: String? = null,
        val conversationId: String? = null,
        val userId: String? = null,
        val sessionId: String? = null,
        val sdk: SdkDto,
    )
}
