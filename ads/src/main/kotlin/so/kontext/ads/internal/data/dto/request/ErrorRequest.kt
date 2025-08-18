package so.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public data class ErrorRequest(
    @SerialName("error") val error: String,
    @SerialName("additionalData") val additionalData: JsonObject? = null,
)
