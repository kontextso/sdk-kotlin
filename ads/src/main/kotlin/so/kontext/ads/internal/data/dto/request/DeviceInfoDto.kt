package so.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DeviceInfoDto(
    @SerialName("os") val os: String,
    @SerialName("systemVersion") val systemVersion: String,
    @SerialName("model") val model: String,
    @SerialName("brand") val brand: String,
    @SerialName("deviceType") val deviceType: String,
    @SerialName("appBundleId") val appBundleId: String,
    @SerialName("appVersion") val appVersion: String,
    @SerialName("soundOn") val soundOn: Boolean,
    @SerialName("appStoreUrl") val appStoreUrl: String?,
)
