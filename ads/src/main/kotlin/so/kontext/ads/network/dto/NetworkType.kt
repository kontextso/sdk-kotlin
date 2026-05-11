package so.kontext.ads.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * High-level network connection type. Mirrors the server's
 * `network.type` enum and sdk-swift's `NetworkType`.
 */
@Serializable
internal enum class NetworkType {
    @SerialName("wifi")
    WIFI,

    @SerialName("cellular")
    CELLULAR,

    @SerialName("ethernet")
    ETHERNET,

    @SerialName("other")
    OTHER,

    ;

    companion object {
        /**
         * Parses the wire-format value; returns `null` for unknown / null
         * input. Mirrors Swift's `NetworkType(rawValue:)` shape so the
         * fallback decision lives at the call site (typically `?: OTHER`),
         * not buried in this helper.
         */
        fun fromString(value: String?): NetworkType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
