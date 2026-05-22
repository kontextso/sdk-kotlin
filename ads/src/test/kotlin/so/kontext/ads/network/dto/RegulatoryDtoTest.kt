package so.kontext.ads.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegulatoryDtoTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `serializes correctly`() {
        val reg = RegulatoryDto(gdpr = 1, gdprConsent = "consent-string", coppa = 0)
        val serialized = json.encodeToString(reg)
        assertTrue(serialized.contains("\"gdpr\":1"))
        assertTrue(serialized.contains("\"gdprConsent\":\"consent-string\""))
    }
}
