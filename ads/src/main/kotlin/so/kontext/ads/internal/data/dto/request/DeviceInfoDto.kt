package so.kontext.ads.internal.data.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OsDto(
    @SerialName("name") val name: String,
    @SerialName("version") val version: String,
    @SerialName("locale") val locale: String,
    @SerialName("timezone") val timezone: String,
)

@Serializable
internal data class HardwareDto(
    @SerialName("brand") val brand: String?,
    @SerialName("model") val model: String?,
    @SerialName("deviceType") val deviceType: String,
    @SerialName("bootTime") val bootTime: Long?,
    @SerialName("sdCardAvailable") val sdCardAvailable: Boolean?,
)

@Serializable
internal data class ScreenDto(
    @SerialName("width") val width: Int,
    @SerialName("height") val height: Int,
    @SerialName("dpr") val dpr: Float,
    @SerialName("orientation") val orientation: String,
    @SerialName("darkMode") val darkMode: Boolean,
)

@Serializable
internal data class PowerDto(
    @SerialName("batteryLevel") val batteryLevel: Int?,
    @SerialName("batteryState") val batteryState: String?,
    @SerialName("lowPowerMode") val lowPowerMode: Boolean?,
)

@Serializable
internal data class AudioDto(
    @SerialName("volume") val volume: Int?,
    @SerialName("muted") val muted: Boolean?,
    @SerialName("outputPluggedIn") val outputPluggedIn: Boolean?,
    @SerialName("outputType") val outputType: List<String>?,
)

@Serializable
internal data class NetworkDto(
    @SerialName("userAgent") val userAgent: String?,
    @SerialName("type") val type: String?,
    @SerialName("detail") val detail: String?,
    @SerialName("carrier") val carrier: String?,
)

@Serializable
internal data class DeviceDto(
    @SerialName("os") val os: OsDto,
    @SerialName("hardware") val hardware: HardwareDto,
    @SerialName("screen") val screen: ScreenDto,
    @SerialName("power") val power: PowerDto,
    @SerialName("audio") val audio: AudioDto,
    @SerialName("network") val network: NetworkDto,
)
