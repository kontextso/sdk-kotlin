package so.kontext.ads.internal.utils.consent

import android.content.Context
import androidx.preference.PreferenceManager
import so.kontext.ads.domain.Regulatory

internal data class TcfData(
    val gdpr: Int? = null,
    val gdprConsent: String? = null,
)

internal object TcfInfo {
    private const val TcStringKey = "IABTCF_TCString"
    private const val GdprAppliesKey = "IABTCF_gdprApplies"

    fun getTcfData(context: Context): TcfData {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val tcString = prefs.getString(TcStringKey, null)
                ?.takeIf { it.isNotEmpty() }

            val gdprAppliesRaw: Int? = if (prefs.contains(GdprAppliesKey)) {
                try {
                    prefs.getInt(GdprAppliesKey, 0)
                } catch (_: Throwable) {
                    prefs.getString(GdprAppliesKey, null)?.toIntOrNull()
                }
            } else {
                null
            }

            val gdprApplies = when (gdprAppliesRaw) {
                0, 1 -> gdprAppliesRaw
                else -> null
            }

            TcfData(gdpr = gdprApplies, gdprConsent = tcString)
        } catch (_: Throwable) {
            TcfData()
        }
    }
}

internal fun mergeRegulatoryWithTcf(regulatory: Regulatory?, tcfData: TcfData): Regulatory? {
    val merged = (regulatory ?: Regulatory()).copy(
        gdpr = tcfData.gdpr ?: regulatory?.gdpr,
        gdprConsent = tcfData.gdprConsent ?: regulatory?.gdprConsent,
    )

    return if (merged.isEmpty()) null else merged
}

private fun Regulatory.isEmpty(): Boolean {
    return gdpr == null &&
        gdprConsent == null &&
        coppa == null &&
        gpp == null &&
        gppSid.isNullOrEmpty() &&
        usPrivacy == null
}
