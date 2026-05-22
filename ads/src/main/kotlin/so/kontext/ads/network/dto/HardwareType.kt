package so.kontext.ads.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Device form-factor classification. Mirrors the server's
 * `hardware.type` enum and sdk-swift's `HardwareType`.
 */
@Serializable
internal enum class HardwareType {
    @SerialName("handset")
    HANDSET,

    @SerialName("tablet")
    TABLET,

    @SerialName("desktop")
    DESKTOP,

    @SerialName("tv")
    TV,

    @SerialName("other")
    OTHER,

    ;

    companion object {
        /**
         * Parses the wire-format value; returns `null` for unknown / null
         * input. Mirrors Swift's `HardwareType(rawValue:)` shape so the
         * fallback decision lives at the call site (typically `?: OTHER`),
         * not buried in this helper.
         */
        fun fromString(value: String?): HardwareType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
