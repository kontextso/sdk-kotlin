package so.kontext.ads.internal.data.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OmInfoDto(
    @SerialName("creativeType") val creativeType: String? = null,
)
