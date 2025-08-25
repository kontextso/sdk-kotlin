package so.kontext.ads.internal.data.mapper

import so.kontext.ads.domain.Regulatory
import so.kontext.ads.internal.data.dto.request.RegulatoryDto

internal fun Regulatory.toDto() = RegulatoryDto(
    gdpr = gdpr,
    gdprConsent = gdprConsent,
    coppa = coppa,
    gpp = gpp,
    gppSid = gppSid,
    usPrivacy = usPrivacy,
)
