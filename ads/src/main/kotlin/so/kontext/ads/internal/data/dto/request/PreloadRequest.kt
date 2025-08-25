package so.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PreloadRequest(
    @SerialName("publisherToken") val publisherToken: String,
    @SerialName("conversationId") val conversationId: String,
    @SerialName("userId") val userId: String,
    @SerialName("messages") val messages: List<ChatMessageDto>,
    @SerialName("device") val device: DeviceInfoDto,
    @SerialName("regulatory") val regulatory: RegulatoryDto? = null,
    @SerialName("variantId") val variantId: String? = null,
    @SerialName("character") val character: CharacterDto? = null,
    @SerialName("advertisingId") val advertisingId: String? = null,
    @SerialName("vendorId") val vendorId: String? = null,
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("sdk") val sdk: String? = null,
    @SerialName("sdkVersion") val sdkVersion: String? = null,
)
