package com.kontext.ads

import com.kontext.ads.domain.Character

internal data class AdsConfig(
    val adServerUrl: String,
    val publisherToken: String,
    val userId: String,
    val conversationId: String,
    val enabledPlacementCodes: List<String>,
    val character: Character?,
    val variantId: String?,
    val advertisingId: String?,
    val vendorId: String?,
    val isDisabled: Boolean,
)
