package so.kontext.ads.internal

import so.kontext.ads.domain.Character
import so.kontext.ads.domain.Regulatory

internal data class AdsConfiguration(
    val adServerUrl: String,
    val publisherToken: String,
    val userId: String,
    val conversationId: String,
    val enabledPlacementCodes: List<String>,
    val character: Character?,
    val variantId: String?,
    val advertisingId: String?,
    val isDisabled: Boolean,
    val theme: String?,
    val regulatory: Regulatory?,
)
