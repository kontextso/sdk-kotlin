package so.kontext.ads.network.dto

import kotlinx.serialization.Serializable
import so.kontext.ads.model.ImpressionTrigger
import so.kontext.ads.utils.OmCreativeTypeSerializer
import so.kontext.ads.utils.UuidSerializer
import so.kontext.kit.omsdk.OmCreativeType
import java.util.UUID

/**
 * Wire-format bid as decoded from the `/preload` response. Convert to
 * the SDK-internal `Bid` domain type via [BidDto.toDomain].
 *
 * Strict on identity (`bidId: UUID`, `code: String`) — a malformed
 * required field is treated as a server bug and fails the whole
 * response decode rather than silently producing a half-broken bid.
 * Tolerant on optional metadata (`revenue`, `impressionTrigger`,
 * `creativeType`) — the typed-enum serializers fall back to `null` for
 * unknown values, so server-side additions don't break old SDKs.
 *
 * Server-emitted `skan` (SKAdNetwork attribution) is not carried —
 * that field is iOS-only and `Json { ignoreUnknownKeys = true }`
 * drops it.
 *
 * Mirrors iOS `BidDTO` (`Networking/DTO/BidDTO.swift`).
 */
@Serializable
internal data class BidDto(
    @Serializable(with = UuidSerializer::class) val bidId: UUID,
    val code: String,
    val revenue: Double? = null,
    val impressionTrigger: ImpressionTrigger? = null,
    // Server emits `om: { creativeType: "..." }` nested. Keep a
    // top-level `creativeType` slot too for forward-compat. The
    // domain mapper prefers the nested location since that's what
    // the v4 server sends today.
    val om: OmDto? = null,
    @Serializable(with = OmCreativeTypeSerializer::class) val creativeType: OmCreativeType? = null,
)

/**
 * Nested OM metadata block on a bid. Mirrors the server's
 * `bids[i].om` object shape.
 */
@Serializable
internal data class OmDto(
    @Serializable(with = OmCreativeTypeSerializer::class) val creativeType: OmCreativeType? = null,
)
