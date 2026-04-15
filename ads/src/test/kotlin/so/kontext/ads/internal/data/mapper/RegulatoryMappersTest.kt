package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.Regulatory

class RegulatoryMappersTest {

    @Test
    fun `toDto copies every field verbatim`() {
        val dto = Regulatory(
            gdpr = 1,
            gdprConsent = "CONSENT-STRING",
            coppa = 0,
            gpp = "GPP-STRING",
            gppSid = listOf(2, 6),
            usPrivacy = "1YNN",
        ).toDto()

        assertEquals(1, dto.gdpr)
        assertEquals("CONSENT-STRING", dto.gdprConsent)
        assertEquals(0, dto.coppa)
        assertEquals("GPP-STRING", dto.gpp)
        assertEquals(listOf(2, 6), dto.gppSid)
        assertEquals("1YNN", dto.usPrivacy)
    }

    @Test
    fun `toDto preserves null optional fields`() {
        val dto = Regulatory().toDto()
        assertNull(dto.gdpr)
        assertNull(dto.gdprConsent)
        assertNull(dto.coppa)
        assertNull(dto.gpp)
        assertNull(dto.gppSid)
        assertNull(dto.usPrivacy)
    }
}
