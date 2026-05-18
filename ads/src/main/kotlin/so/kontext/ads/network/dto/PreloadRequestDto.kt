package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable
import so.kontext.ads.utils.UuidSerializer
import java.util.UUID

/**
 * JSON body sent to `POST /preload` — the SDK's main ad-fetching call.
 *
 * Mirrors the server's `preloadRequestBodySchema` (and sdk-swift's
 * `PreloadRequestDTO`). Required fields go first; every optional has
 * an explicit `null` default so the entire DTO can be built in one
 * pass at the call site (`Preload.buildRequestDto`).
 *
 * `device` and `app` are required (matches Swift). `regulatory` is
 * sent only when at least one privacy field has a real value, to
 * avoid shipping an empty object.
 *
 * `sessionId` is typed as [UUID] via [UuidSerializer] — the wire form
 * is the canonical UUID string, identical to sdk-swift's `UUID?`
 * encode.
 *
 * `vendorId` from sdk-swift is intentionally omitted — Android has no
 * `IdentifierForVendor` equivalent.
 *
 * Mirrors iOS `PreloadRequestDTO` (`Networking/DTO/PreloadRequestDTO.swift`).
 */
@Serializable
internal data class PreloadRequestDto(
    val publisherToken: String,
    val userId: String,
    val installId: String,
    val conversationId: String,
    val enabledPlacementCodes: List<String>,
    val messages: List<MessageDto>,
    val sdk: SdkDto,
    val device: DeviceDto,
    val app: AppDto,
    @Serializable(with = UuidSerializer::class) val sessionId: UUID? = null,
    val character: CharacterDto? = null,
    val regulatory: RegulatoryDto? = null,
    val userEmail: String? = null,
    val variantId: String? = null,
    val advertisingId: String? = null,
)
