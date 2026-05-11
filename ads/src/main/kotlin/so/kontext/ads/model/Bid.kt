package so.kontext.ads.model

import so.kontext.ads.network.dto.BidDto
import so.kontext.kit.omsdk.OmCreativeType
import java.util.UUID

/**
 * A bid returned from the `/preload` endpoint. The SDK attaches one bid to
 * the most recent assistant `Message` and resolves the iframe URL for ad
 * rendering once the publisher creates an `Ad` for that message.
 *
 * `bidId`, `impressionTrigger`, and `creativeType` are typed all the way
 * through — [BidDto] decodes them directly via kotlinx serializers
 * (`UuidSerializer`, the `@Serializable` `ImpressionTrigger` enum, and
 * `OmCreativeTypeSerializer`). [BidDto.toDomain] is therefore a near
 * passthrough, falling back to `ImpressionTrigger.IMMEDIATE` only when
 * the wire value was missing or coerced to `null`.
 *
 * Internal — publishers never receive a `Bid` instance; they learn outcomes
 * via `AdEvent` payloads (`Filled.revenue`, `Viewed.messageId`, etc.).
 *
 * Mirrors iOS `Bid` (`KontextSwiftSDK/Model/Bid.swift`), minus `skan` —
 * SKAdNetwork is Apple-only and the field would always be null on Android.
 */
internal data class Bid(
    val bidId: UUID,
    val code: String,
    val revenue: Double? = null,
    val impressionTrigger: ImpressionTrigger = ImpressionTrigger.IMMEDIATE,
    val creativeType: OmCreativeType? = null,
)

internal fun BidDto.toDomain(): Bid = Bid(
    bidId = bidId,
    code = code,
    revenue = revenue,
    impressionTrigger = impressionTrigger ?: ImpressionTrigger.IMMEDIATE,
    creativeType = creativeType,
)
