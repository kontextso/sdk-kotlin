package so.kontext.ads.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Screen orientation. Mirrors the server's `screen.orientation` enum
 * and sdk-swift's `ScreenOrientation`.
 */
@Serializable
internal enum class ScreenOrientation {
    @SerialName("portrait")
    PORTRAIT,

    @SerialName("landscape")
    LANDSCAPE,

    ;

    companion object {
        /** Parses the wire-format value; unknown / null returns null. */
        fun fromString(value: String?): ScreenOrientation? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
