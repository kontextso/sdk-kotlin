package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable
import so.kontext.ads.utils.UuidSerializer
import java.util.UUID

/**
 * JSON response from the `/preload` endpoint. One DTO, three response
 * shapes, distinguished by field presence:
 *  * **Success** — `sessionId` set, `errCode == null`, `bids` populated.
 *  * **Skip** — `skip == true`, `skipCode` set (one of the server's
 *    `SkipCode` enum values), no `bids`.
 *  * **Error** — `errCode` set; `permanent == true` means the server has
 *    permanently disabled the session and the SDK should stop preloading.
 *
 * `Preload.handleResponse` performs this discrimination at the consumer
 * side. The DTO itself stays a tolerant container that accepts any of
 * the three shapes.
 *
 * `sessionId` is typed as [UUID] via [UuidSerializer] — strict decode
 * (malformed UUID throws); matches sdk-swift's `UUID?`.
 *
 * Mirrors iOS `PreloadResponseDTO` (`Networking/DTO/PreloadResponseDTO.swift`).
 */
@Serializable
internal data class PreloadResponseDto(
    val bids: List<BidDto>? = null,
    @Serializable(with = UuidSerializer::class) val sessionId: UUID? = null,
    val skipCode: String? = null,
    val skip: Boolean? = null,
    val errCode: String? = null,
    val permanent: Boolean? = null,
)
