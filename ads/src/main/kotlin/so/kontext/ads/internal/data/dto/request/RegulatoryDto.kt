package so.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RegulatoryDto(
    @SerialName("gdpr") val gdpr: Int? = null,
    @SerialName("gdprConsent") val gdprConsent: String? = null,
    @SerialName("coppa") val coppa: Int? = null,
    @SerialName("gpp") val gpp: String? = null,
    @SerialName("gppSid") val gppSid: List<Int>? = null,
    @SerialName("usPrivacy") val usPrivacy: String? = null,
)
