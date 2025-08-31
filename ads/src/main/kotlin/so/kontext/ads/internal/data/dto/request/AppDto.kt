package so.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AppDto(
    @SerialName("bundleId") val bundleId: String,
    @SerialName("version") val version: String,
    @SerialName("storeUrl") val storeUrl: String?,
    @SerialName("firstInstallTime") val firstInstallTime: Long,
    @SerialName("lastUpdateTime") val lastUpdateTime: Long,
    @SerialName("startTime") val startTime: Long,
)
