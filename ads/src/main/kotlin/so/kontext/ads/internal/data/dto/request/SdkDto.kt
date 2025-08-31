package so.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SdkDto(
    @SerialName("name") val name: String,
    @SerialName("version") val version: String,
    @SerialName("platform") val platform: String,
)
