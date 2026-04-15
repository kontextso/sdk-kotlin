package so.kontext.ads.internal.utils.consent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.Regulatory

class TcfInfoMergeTest {

    @Test
    fun `returns null when both the regulatory and tcf data are empty`() {
        assertNull(mergeRegulatoryWithTcf(regulatory = null, tcfData = TcfData()))
    }

    @Test
    fun `returns null when an explicit empty Regulatory is merged with empty TcfData`() {
        assertNull(mergeRegulatoryWithTcf(regulatory = Regulatory(), tcfData = TcfData()))
    }

    @Test
    fun `TCF data overrides the regulatory gdpr + gdprConsent when both are present`() {
        val merged = mergeRegulatoryWithTcf(
            regulatory = Regulatory(gdpr = 0, gdprConsent = "OLD"),
            tcfData = TcfData(gdpr = 1, gdprConsent = "NEW"),
        )
        assertEquals(1, merged?.gdpr)
        assertEquals("NEW", merged?.gdprConsent)
    }

    @Test
    fun `TCF fallback keeps the regulatory value when TCF is null`() {
        val merged = mergeRegulatoryWithTcf(
            regulatory = Regulatory(gdpr = 1, gdprConsent = "KEPT"),
            tcfData = TcfData(),
        )
        assertEquals(1, merged?.gdpr)
        assertEquals("KEPT", merged?.gdprConsent)
    }

    @Test
    fun `TCF applies even when regulatory is null by synthesizing a Regulatory`() {
        val merged = mergeRegulatoryWithTcf(
            regulatory = null,
            tcfData = TcfData(gdpr = 1, gdprConsent = "CONSENT"),
        )
        assertEquals(1, merged?.gdpr)
        assertEquals("CONSENT", merged?.gdprConsent)
    }

    @Test
    fun `Non-gdpr fields from regulatory are preserved through the merge`() {
        val merged = mergeRegulatoryWithTcf(
            regulatory = Regulatory(
                coppa = 1,
                gpp = "GPP",
                gppSid = listOf(6),
                usPrivacy = "1YNN",
            ),
            tcfData = TcfData(gdpr = 1, gdprConsent = "CONSENT"),
        )
        assertEquals(1, merged?.coppa)
        assertEquals("GPP", merged?.gpp)
        assertEquals(listOf(6), merged?.gppSid)
        assertEquals("1YNN", merged?.usPrivacy)
    }

    @Test
    fun `empty Regulatory plus valid TCF yields non-null merged result`() {
        val merged = mergeRegulatoryWithTcf(
            regulatory = Regulatory(),
            tcfData = TcfData(gdpr = 0),
        )
        assertEquals(0, merged?.gdpr)
    }

    @Test
    fun `TcfData data-class equality`() {
        assertEquals(TcfData(gdpr = 1, gdprConsent = "x"), TcfData(gdpr = 1, gdprConsent = "x"))
    }
}
