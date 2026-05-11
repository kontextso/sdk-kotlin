package so.kontext.ads.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import so.kontext.ads.network.dto.BidDto
import so.kontext.kit.omsdk.OmCreativeType
import java.util.UUID

class BidTest {

    private val bidUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

    // Mirrors Preload.kt's Json config (coerceInputValues for the
    // tolerant-decode behaviour on optional metadata fields).
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `creates with defaults`() {
        val bid = Bid(bidId = bidUuid, code = "inlineAd")

        assertEquals(bidUuid, bid.bidId)
        assertEquals("inlineAd", bid.code)
        assertNull(bid.revenue)
        assertEquals(ImpressionTrigger.IMMEDIATE, bid.impressionTrigger)
        assertNull(bid.creativeType)
    }

    @Test
    fun `creates with all fields`() {
        val bid = Bid(
            bidId = bidUuid,
            code = "sidebar",
            revenue = 1.5,
            impressionTrigger = ImpressionTrigger.COMPONENT,
            creativeType = OmCreativeType.DISPLAY,
        )

        assertEquals(1.5, bid.revenue)
        assertEquals(ImpressionTrigger.COMPONENT, bid.impressionTrigger)
        assertEquals(OmCreativeType.DISPLAY, bid.creativeType)
    }

    @Test
    fun `BidDto toDomain passes typed fields through`() {
        val dto = BidDto(
            bidId = bidUuid,
            code = "inlineAd",
            revenue = 2.5,
            impressionTrigger = ImpressionTrigger.IMMEDIATE,
            creativeType = OmCreativeType.DISPLAY,
        )

        val bid = dto.toDomain()

        assertEquals(bidUuid, bid.bidId)
        assertEquals("inlineAd", bid.code)
        assertEquals(2.5, bid.revenue)
        assertEquals(ImpressionTrigger.IMMEDIATE, bid.impressionTrigger)
        assertEquals(OmCreativeType.DISPLAY, bid.creativeType)
    }

    @Test
    fun `BidDto toDomain falls back to IMMEDIATE when impressionTrigger is null`() {
        // Server may omit the field; SDK should fall back to the safe default.
        val dto = BidDto(bidId = bidUuid, code = "inlineAd", impressionTrigger = null)
        assertEquals(ImpressionTrigger.IMMEDIATE, dto.toDomain().impressionTrigger)
    }

    // Wire-decode behaviour --------------------------------------------------

    @Test
    fun `BidDto decode strict on bidId — malformed UUID fails the decode`() {
        // Server contract is UUID. A non-UUID string is a server bug;
        // strict decode lets the surrounding response handler drop the
        // whole bid rather than silently producing one with garbage identity.
        assertThrows<IllegalArgumentException> {
            json.decodeFromString<BidDto>(
                """{"bidId":"not-a-uuid","code":"inlineAd"}""",
            )
        }
    }

    @Test
    fun `BidDto decode tolerant on impressionTrigger — unknown value coerces to null`() {
        // Mirrors sdk-swift's `try? c.decode(ImpressionTrigger.self, ...)`:
        // unknown values fall through to null so server-side additions
        // don't break old SDKs. toDomain then defaults the bid to IMMEDIATE.
        val dto = json.decodeFromString<BidDto>(
            """{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","impressionTrigger":"future-value"}""",
        )
        assertNull(dto.impressionTrigger)
        assertEquals(ImpressionTrigger.IMMEDIATE, dto.toDomain().impressionTrigger)
    }

    @Test
    fun `BidDto decode tolerant on creativeType — unknown value falls back to null`() {
        // Same tolerance as impressionTrigger. OmCreativeTypeSerializer's
        // fromString returns null for anything outside `display` / `video`.
        val uuid = """{"bidId":"11111111-1111-1111-1111-111111111111","code":"inlineAd","""
        assertEquals(
            OmCreativeType.VIDEO,
            json.decodeFromString<BidDto>("""$uuid"creativeType":"video"}""").creativeType,
        )
        assertNull(json.decodeFromString<BidDto>("""$uuid"creativeType":null}""").creativeType)
        assertNull(json.decodeFromString<BidDto>("""$uuid"creativeType":"native"}""").creativeType)
    }
}
