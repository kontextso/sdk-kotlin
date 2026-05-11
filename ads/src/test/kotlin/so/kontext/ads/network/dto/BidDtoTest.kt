package so.kontext.ads.network.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.kontext.ads.model.ImpressionTrigger
import so.kontext.kit.omsdk.OmCreativeType

class BidDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes with all fields`() {
        val body = """{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","revenue":1.5,"impressionTrigger":"component","creativeType":"display"}"""
        val bid = json.decodeFromString<BidDto>(body)

        assertEquals(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"), bid.bidId)
        assertEquals("inlineAd", bid.code)
        assertEquals(1.5, bid.revenue)
        assertEquals(ImpressionTrigger.COMPONENT, bid.impressionTrigger)
        assertEquals(OmCreativeType.DISPLAY, bid.creativeType)
    }

    @Test
    fun `deserializes with minimal fields`() {
        val body = """{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd"}"""
        val bid = json.decodeFromString<BidDto>(body)

        assertEquals(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"), bid.bidId)
        assertNull(bid.revenue)
        assertNull(bid.impressionTrigger)
    }
}
