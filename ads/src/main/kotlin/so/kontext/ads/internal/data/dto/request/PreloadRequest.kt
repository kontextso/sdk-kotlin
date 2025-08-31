package so.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PreloadRequest(
    @SerialName("publisherToken") val publisherToken: String,
    @SerialName("conversationId") val conversationId: String,
    @SerialName("userId") val userId: String,
    @SerialName("enabledPlacementCodes") val enabledPlacementCodes: List<String>? = null,
    @SerialName("messages") val messages: List<ChatMessageDto>,
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("advertisingId") val advertisingId: String? = null,
    @SerialName("sdk") val sdk: SdkDto,
    @SerialName("app") val app: AppDto? = null,
    @SerialName("device") val device: DeviceDto,
    @SerialName("regulatory") val regulatory: RegulatoryDto? = null,
    @SerialName("character") val character: CharacterDto? = null,
    @SerialName("variantId") val variantId: String? = null,
)
