package so.kontext.ads.internal.data.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.kontext.ads.domain.AdDisplayPosition
import so.kontext.ads.domain.OmCreativeType
import so.kontext.ads.internal.data.dto.response.BidDto
import so.kontext.ads.internal.data.dto.response.OmInfoDto

class BidMapperTest {

    @Test
    fun `om field absent maps to null omCreativeType`() {
        val bid = bidDto(om = null).toDomain()
        assertNull(bid.omCreativeType)
    }

    @Test
    fun `om creativeType null maps to null omCreativeType`() {
        val bid = bidDto(om = OmInfoDto(creativeType = null)).toDomain()
        assertNull(bid.omCreativeType)
    }

    @Test
    fun `om creativeType display maps to DISPLAY`() {
        val bid = bidDto(om = OmInfoDto(creativeType = "display")).toDomain()
        assertEquals(OmCreativeType.DISPLAY, bid.omCreativeType)
    }

    @Test
    fun `om creativeType video maps to VIDEO`() {
        val bid = bidDto(om = OmInfoDto(creativeType = "video")).toDomain()
        assertEquals(OmCreativeType.VIDEO, bid.omCreativeType)
    }

    @Test
    fun `om creativeType unknown string maps to null`() {
        val bid = bidDto(om = OmInfoDto(creativeType = "audio")).toDomain()
        assertNull(bid.omCreativeType)
    }

    @Test
    fun `non-om fields are mapped correctly`() {
        val bid = bidDto(om = null).toDomain()
        assertEquals("bid-123", bid.bidId)
        assertEquals("inlineAd", bid.code)
        assertEquals(AdDisplayPosition.AfterAssistantMessage, bid.adDisplayPosition)
    }

    private fun bidDto(om: OmInfoDto?) = BidDto(
        bidId = "bid-123",
        code = "inlineAd",
        adDisplayPosition = "afterAssistantMessage",
        om = om,
    )
}
