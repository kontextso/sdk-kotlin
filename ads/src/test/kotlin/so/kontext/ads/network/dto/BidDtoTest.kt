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

    @Test
    fun `deserializes nested om creativeType`() {
        // v4 server wire shape: bids[i].om.creativeType. The OmDto block
        // is the production source of truth — `BidDto.creativeType` was
        // always null on real responses before this fix shipped, which is
        // how the OMID compliance bug ("never gets a real session") slipped
        // past the existing tests.
        val body = """
            {"bidId":"11111111-1111-1111-1111-111111111111",
             "code":"inlineAd",
             "om":{"creativeType":"video"}}
        """.trimIndent().replace("\n", "")
        val bid = json.decodeFromString<BidDto>(body)

        assertEquals(OmCreativeType.VIDEO, bid.om?.creativeType)
        // Top-level slot stays null when only the nested form is present —
        // the domain mapper prefers `om.creativeType` over the top-level
        // and the existence of the top-level slot is a forward-compat
        // landing pad only.
        assertNull(bid.creativeType)
    }

    @Test
    fun `nested om creativeType ignores unknown values like the top-level slot`() {
        // OmCreativeTypeSerializer is annotated coerceInputValues so a
        // future server-side enum value (e.g. "interactive") decodes to
        // null without throwing. Mirror the existing top-level test for
        // the nested location.
        val body = """
            {"bidId":"11111111-1111-1111-1111-111111111111",
             "code":"inlineAd",
             "om":{"creativeType":"interactive"}}
        """.trimIndent().replace("\n", "")
        val bid = json.decodeFromString<BidDto>(body)
        assertNull(bid.om?.creativeType)
    }

    @Test
    fun `deserializes both nested om and top-level creativeType`() {
        // Forward-compat: if the server starts sending top-level
        // creativeType again alongside the nested form (or stops sending
        // the nested form), both slots remain populated post-decode and
        // the mapper picks based on its preference rule.
        val body = """
            {"bidId":"11111111-1111-1111-1111-111111111111",
             "code":"inlineAd",
             "om":{"creativeType":"video"},
             "creativeType":"display"}
        """.trimIndent().replace("\n", "")
        val bid = json.decodeFromString<BidDto>(body)

        assertEquals(OmCreativeType.VIDEO, bid.om?.creativeType)
        assertEquals(OmCreativeType.DISPLAY, bid.creativeType)
    }
}
