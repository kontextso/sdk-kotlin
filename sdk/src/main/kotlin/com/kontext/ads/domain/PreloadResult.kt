package com.kontext.ads.domain

internal data class PreloadResult(
    val bids: List<Bid>?,
    val sessionId: String?,
    val remoteLogLevel: String?,
    val preloadTimeout: Int?,
)
