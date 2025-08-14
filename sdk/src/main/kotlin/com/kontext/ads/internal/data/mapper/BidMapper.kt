package com.kontext.ads.internal.data.mapper

import com.kontext.ads.domain.AdDisplayPosition
import com.kontext.ads.domain.Bid
import com.kontext.ads.internal.data.dto.response.BidDto

internal fun BidDto.toDomain(): Bid {
    return Bid(
        bidId = bidId,
        code = code,
        adDisplayPosition = AdDisplayPosition.toDomain(adDisplayPosition),
    )
}
