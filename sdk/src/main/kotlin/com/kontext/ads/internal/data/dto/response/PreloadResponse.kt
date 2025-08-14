package com.kontext.ads.internal.data.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PreloadResponse(
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("bids") val bids: List<BidDto>? = null,
    @SerialName("remoteLogLevel") val remoteLogLevel: String? = null,
    @SerialName("preloadTimeout") val preloadTimeout: Int? = null,
    @SerialName("errCode") val errCode: String? = null,
    @SerialName("permanent") val permanent: Boolean? = null,
)
