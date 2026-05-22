package so.kontext.ads.model

import so.kontext.ads.network.dto.RegulatoryDto

/**
 * Publisher-supplied regulatory state forwarded on every preload request.
 *
 * Field naming matches openRTB conventions used by the ad server, not
 * iOS-internal naming (`tcString` etc.). On Android, on-device TCF data
 * collected by `TCFDataProvider.collect(context)` is merged with this on the way
 * to the wire — TCF wins for `gdpr` / `gdprConsent`, publisher values are
 * used as-is for everything else.
 *
 * Wire encoding goes through [RegulatoryDto] via [toDto].
 *
 * Mirrors iOS `Regulatory` (`KontextSwiftSDK/Model/Regulatory.swift`).
 */
public data class Regulatory(
    /** GDPR applies flag: `1` = yes, `0` = no, `null` = unknown. */
    val gdpr: Int? = null,
    /** IAB TCF v2 consent string. */
    val gdprConsent: String? = null,
    /** COPPA flag: `1` = child-directed, `0` = not, `null` = unknown. */
    val coppa: Int? = null,
    /** IAB Global Privacy Platform string. */
    val gpp: String? = null,
    /** GPP section IDs that apply to this transaction. */
    val gppSid: List<Int>? = null,
    /** IAB US Privacy string (CCPA / LSPA). */
    val usPrivacy: String? = null,
)

internal fun Regulatory.toDto(): RegulatoryDto = RegulatoryDto(
    gdpr = gdpr,
    gdprConsent = gdprConsent,
    coppa = coppa,
    gpp = gpp,
    gppSid = gppSid,
    usPrivacy = usPrivacy,
)
