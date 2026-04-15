package so.kontext.ads.internal.data.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.kontext.ads.internal.data.dto.response.BidDto
import so.kontext.ads.internal.data.dto.response.OmInfoDto
import so.kontext.ads.internal.data.dto.response.PreloadResponse

class PreloadDtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---- PreloadResponse ----

    @Test
    fun `PreloadResponse decodes a minimal body`() {
        val raw = """{"sessionId": "s-1"}"""
        val parsed = json.decodeFromString<PreloadResponse>(raw)
        assertEquals("s-1", parsed.sessionId)
        assertNull(parsed.bids)
        assertNull(parsed.skip)
    }

    @Test
    fun `PreloadResponse decodes a full body`() {
        val raw = """
            {
              "sessionId": "s-1",
              "bids": [
                {"bidId": "b-1", "code": "inlineAd", "adDisplayPosition": "afterAssistantMessage"}
              ],
              "remoteLogLevel": "debug",
              "preloadTimeout": 5000,
              "errCode": null,
              "permanent": false,
              "skip": false,
              "skipCode": null
            }
        """.trimIndent()
        val parsed = json.decodeFromString<PreloadResponse>(raw)
        assertEquals("s-1", parsed.sessionId)
        assertEquals(1, parsed.bids?.size)
        assertEquals("debug", parsed.remoteLogLevel)
        assertEquals(5000, parsed.preloadTimeout)
        assertEquals(false, parsed.permanent)
        assertEquals(false, parsed.skip)
    }

    @Test
    fun `PreloadResponse tolerates unknown top-level keys`() {
        val raw = """{"sessionId": "s", "futureField": "ignored"}"""
        val parsed = json.decodeFromString<PreloadResponse>(raw)
        assertEquals("s", parsed.sessionId)
    }

    @Test
    fun `PreloadResponse empty JSON yields all-null fields`() {
        val parsed = json.decodeFromString<PreloadResponse>("{}")
        assertNull(parsed.sessionId)
        assertNull(parsed.bids)
        assertNull(parsed.skip)
    }

    // ---- BidDto ----

    @Test
    fun `BidDto defaults impressionTrigger to immediate when missing`() {
        val raw = """{"bidId": "b", "code": "c", "adDisplayPosition": "afterAssistantMessage"}"""
        val parsed = json.decodeFromString<BidDto>(raw)
        assertEquals("immediate", parsed.impressionTrigger)
        assertNull(parsed.om)
    }

    @Test
    fun `BidDto decodes om creativeType field`() {
        val raw = """
            {
              "bidId": "b", "code": "c", "adDisplayPosition": "afterAssistantMessage",
              "om": {"creativeType": "video"}
            }
        """.trimIndent()
        val parsed = json.decodeFromString<BidDto>(raw)
        assertEquals("video", parsed.om?.creativeType)
    }

    // ---- OmInfoDto ----

    @Test
    fun `OmInfoDto allows null creativeType`() {
        val parsed = json.decodeFromString<OmInfoDto>("""{"creativeType": null}""")
        assertNull(parsed.creativeType)
    }

    // ---- round-trip ----

    @Test
    fun `PreloadResponse round-trips through encode + decode`() {
        val original = PreloadResponse(
            sessionId = "s",
            bids = listOf(BidDto("b-1", "c", "afterUserMessage", "immediate", OmInfoDto("display"))),
            remoteLogLevel = "info",
            preloadTimeout = 1000,
            skip = true,
            skipCode = "no_fill",
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PreloadResponse>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `BidDto serialised JSON contains all field names`() {
        val bid = BidDto("b-1", "c", "afterAssistantMessage", "immediate", OmInfoDto("display"))
        val encoded = json.encodeToString(bid)
        assertTrue(encoded.contains("\"bidId\""))
        assertTrue(encoded.contains("\"code\""))
        assertTrue(encoded.contains("\"adDisplayPosition\""))
        assertTrue(encoded.contains("\"impressionTrigger\""))
        assertTrue(encoded.contains("\"om\""))
    }
}
