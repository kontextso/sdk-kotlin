package so.kontext.ads.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RegulatoryTest {

    @Test
    fun `creates with all null`() {
        val reg = Regulatory()

        assertNull(reg.gdpr)
        assertNull(reg.gdprConsent)
        assertNull(reg.coppa)
        assertNull(reg.gpp)
        assertNull(reg.gppSid)
        assertNull(reg.usPrivacy)
    }

    @Test
    fun `toDto preserves all fields verbatim`() {
        val regulatory = Regulatory(
            gdpr = 1,
            gdprConsent = "tc-string",
            coppa = 0,
            gpp = "gpp-string",
            gppSid = listOf(7, 8),
            usPrivacy = "1YNN",
        )

        val dto = regulatory.toDto()

        assertEquals(1, dto.gdpr)
        assertEquals("tc-string", dto.gdprConsent)
        assertEquals(0, dto.coppa)
        assertEquals("gpp-string", dto.gpp)
        assertEquals(listOf(7, 8), dto.gppSid)
        assertEquals("1YNN", dto.usPrivacy)
    }

    @Test
    fun `toDto leaves null fields as null`() {
        val dto = Regulatory(gdpr = 1).toDto()

        assertEquals(1, dto.gdpr)
        assertNull(dto.gdprConsent)
        assertNull(dto.coppa)
        assertNull(dto.gpp)
        assertNull(dto.gppSid)
        assertNull(dto.usPrivacy)
    }
}
