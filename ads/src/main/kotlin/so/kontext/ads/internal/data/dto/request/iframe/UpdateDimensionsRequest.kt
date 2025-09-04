package so.kontext.ads.internal.data.dto.request.iframe

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UpdateDimensionsRequest(
    @SerialName("type") val type: String,
    @SerialName("data") val data: UpdateDimensionsDataDto,
)

@Serializable
internal data class UpdateDimensionsDataDto(
    @SerialName("windowWidth") val windowWidth: Float,
    @SerialName("windowHeight") val windowHeight: Float,
    @SerialName("containerWidth") val containerWidth: Float,
    @SerialName("containerHeight") val containerHeight: Float,
    @SerialName("containerX") val containerX: Float,
    @SerialName("containerY") val containerY: Float,
)
