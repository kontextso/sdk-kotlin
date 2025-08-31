package so.kontext.ads.domain

/**
 * Provides necessary information for the AdsProvider about the message's content.
 * Either use this protocol for your own message data class or use [AdsMessage]
 *
 * @param gdpr Flag indicating whether the request is subject to GDPR regulations. 0 = NO, 1 = YES,
 *            omission or null = Unknown.
 * @param gdprConsent When GDPR regulations are in effect this attribute contains the Transparency
 *          and Consent Framework's Consent String data structure. https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/TCFv2/IAB%20Tech%20Lab%20-%20Consent%20string%20and%20vendor%20list%20formats%20v2.md#about-the-transparency--consent-string-tc-string
 * @param coppa - Flag indicating if this request is subject to the [COPPA regulations](https://www.ftc.gov/legal-library/browse/rules/childrens-online-privacy-protection-rule-coppa)
 *          established by the USA FTC, where 0 = no, 1 = yes, omission/null indicates Unknown.
 *          `0` = NO, `1` = YES, omission or `null` = Unknown.
 * @param gpp Contains the Global Privacy Platform's consent string. See IAB-GPP spec for more details. https://github.com/InteractiveAdvertisingBureau/Global-Privacy-Platform
 * @param gppSid Comma-separated list of the section(s) of the GPP string which should be applied for this transaction.
 * @param usPrivacy Communicates signals regarding consumer privacy under US privacy regulation under CCPA and LSPA.
 *          https://github.com/InteractiveAdvertisingBureau/USPrivacy/blob/master/CCPA/US%20Privacy%20String.md
 */
public data class Regulatory(
    val gdpr: Int? = null,
    val gdprConsent: String? = null,
    val coppa: Int? = null,
    val gpp: String? = null,
    val gppSid: List<Int>? = null,
    val usPrivacy: String? = null,
)
