package so.kontext.ads.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Audio output type. Mirrors the server's `audio.outputType` enum and
 * sdk-swift's `AudioOutputType`. Items not in the enum are dropped at
 * the `DeviceCollector` boundary.
 */
@Serializable
internal enum class AudioOutputType {
    @SerialName("wired")
    WIRED,

    @SerialName("hdmi")
    HDMI,

    @SerialName("bluetooth")
    BLUETOOTH,

    @SerialName("usb")
    USB,

    @SerialName("other")
    OTHER,

    ;

    companion object {
        /** Parses the wire-format value; unknown returns null (dropped at collector boundary). */
        fun fromString(value: String?): AudioOutputType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
