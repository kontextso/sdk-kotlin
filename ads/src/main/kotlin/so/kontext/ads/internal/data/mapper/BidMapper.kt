package so.kontext.ads.internal.data.mapper

import so.kontext.ads.domain.AdDisplayPosition
import so.kontext.ads.domain.Bid
import so.kontext.ads.internal.data.dto.response.BidDto

internal fun BidDto.toDomain(): Bid {
    return Bid(
        bidId = bidId,
        code = code,
        adDisplayPosition = AdDisplayPosition.toDomain(adDisplayPosition),
    )
}
