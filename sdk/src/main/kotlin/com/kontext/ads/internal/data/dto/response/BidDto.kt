package com.kontext.ads.internal.data.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BidDto(
    @SerialName("bidId") val bidId: String,
    @SerialName("code") val code: String,
    @SerialName("adDisplayPosition") val adDisplayPosition: String,
)
