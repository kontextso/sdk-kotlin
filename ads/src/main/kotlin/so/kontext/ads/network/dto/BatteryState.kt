package so.kontext.ads.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Battery charging state. Mirrors the server's `power.batteryState`
 * enum and sdk-swift's `BatteryState`.
 */
@Serializable
internal enum class BatteryState {
    @SerialName("charging")
    CHARGING,

    @SerialName("full")
    FULL,

    @SerialName("unplugged")
    UNPLUGGED,

    @SerialName("unknown")
    UNKNOWN,

    ;

    companion object {
        /**
         * Parses the wire-format value; returns `null` for unknown / null
         * input. Mirrors Swift's `BatteryState(rawValue:)` shape so the
         * fallback decision lives at the call site (typically `?: UNKNOWN`),
         * not buried in this helper.
         */
        fun fromString(value: String?): BatteryState? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
