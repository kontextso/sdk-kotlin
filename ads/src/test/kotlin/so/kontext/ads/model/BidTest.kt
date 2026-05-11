package so.kontext.ads.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import so.kontext.ads.network.dto.BidDto
import so.kontext.ads.network.dto.OmDto
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

    // toDomain om-fallback ----------------------------------------------------
    //
    // The v4 ad server sends `creativeType` on the nested `om` block, not on
    // the top-level bid. The domain mapper prefers `om.creativeType` over the
    // top-level slot so the SDK reads the wire shape the server actually
    // emits. Tests pin both fallback directions (nested → preferred, top-level
    // → used when nested missing) so a regression that swaps the precedence
    // is caught — that's how OMID was silently broken for an entire release
    // cycle on v4 before this fix.

    @Test
    fun `BidDto toDomain prefers nested om creativeType over top-level`() {
        val dto = BidDto(
            bidId = bidUuid,
            code = "inlineAd",
            om = OmDto(creativeType = OmCreativeType.VIDEO),
            creativeType = OmCreativeType.DISPLAY,
        )
        assertEquals(OmCreativeType.VIDEO, dto.toDomain().creativeType)
    }

    @Test
    fun `BidDto toDomain falls back to top-level creativeType when om is null`() {
        // Forward-compat: if a future server stops emitting the nested om
        // block, the top-level field is still picked up.
        val dto = BidDto(
            bidId = bidUuid,
            code = "inlineAd",
            om = null,
            creativeType = OmCreativeType.DISPLAY,
        )
        assertEquals(OmCreativeType.DISPLAY, dto.toDomain().creativeType)
    }

    @Test
    fun `BidDto toDomain uses nested om creativeType when top-level is null`() {
        // The production case as of v4: server sends only the nested form.
        // The existing `passes typed fields through` test only exercises
        // the top-level path — without this we'd ship the OMID bug again.
        val dto = BidDto(
            bidId = bidUuid,
            code = "inlineAd",
            om = OmDto(creativeType = OmCreativeType.VIDEO),
            creativeType = null,
        )
        assertEquals(OmCreativeType.VIDEO, dto.toDomain().creativeType)
    }

    @Test
    fun `BidDto toDomain returns null creativeType when both om and top-level are null`() {
        val dto = BidDto(bidId = bidUuid, code = "inlineAd", om = null, creativeType = null)
        assertNull(dto.toDomain().creativeType)
    }

    @Test
    fun `BidDto toDomain falls back to top-level when nested om creativeType is null`() {
        // Server sends the om block but with a null/unknown creativeType
        // inside — the mapper still falls back to the top-level slot
        // rather than emitting null.
        val dto = BidDto(
            bidId = bidUuid,
            code = "inlineAd",
            om = OmDto(creativeType = null),
            creativeType = OmCreativeType.DISPLAY,
        )
        assertEquals(OmCreativeType.DISPLAY, dto.toDomain().creativeType)
    }
}
